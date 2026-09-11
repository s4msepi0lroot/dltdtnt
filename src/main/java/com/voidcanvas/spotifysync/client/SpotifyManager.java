package com.voidcanvas.spotifysync.client;

import com.voidcanvas.spotifysync.SpotifySync;
import com.voidcanvas.spotifysync.config.PlaybackSource;
import com.voidcanvas.spotifysync.config.SyncConfig;
import com.voidcanvas.spotifysync.local.WindowsMediaSource;
import com.voidcanvas.spotifysync.lyrics.LyricsManager;
import com.voidcanvas.spotifysync.spotify.PlaybackState;
import com.voidcanvas.spotifysync.spotify.SpotifyApi;
import com.voidcanvas.spotifysync.spotify.SpotifyAuth;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Central client-side coordinator: owns the auth session, the polling loop,
 * the playback snapshot, the album art cache and the lyrics manager.
 *
 * <p>Two playback sources are supported. The Spotify Web API gives the richest
 * data but requires Premium on the developer account that owns the client id.
 * The Windows system media session needs no Premium at all and is used
 * automatically when no Web API session is available.</p>
 */
public final class SpotifyManager {

    private static SpotifyManager instance;

    private final SpotifyAuth auth = new SpotifyAuth();
    private final SpotifyApi api = new SpotifyApi(auth);
    private final WindowsMediaSource local = new WindowsMediaSource();
    private final ExecutorService network;
    private final CoverArtCache covers;
    private final LyricsManager lyrics;

    private final AtomicReference<PlaybackState> state = new AtomicReference<>(PlaybackState.EMPTY);
    private final AtomicBoolean pollInFlight = new AtomicBoolean(false);

    private volatile long lastPollNano;
    private volatile long lastSuccessNano;
    /** Local optimistic overrides so the UI reacts instantly to button presses. */
    private volatile long optimisticUntilNano;

    private SpotifyManager() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "SpotifySync network");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
        this.network = Executors.newFixedThreadPool(3, factory);
        this.covers = new CoverArtCache(auth.httpClient(), network);
        this.lyrics = new LyricsManager(auth.httpClient(), network);
    }

    public static SpotifyManager get() {
        if (instance == null) {
            instance = new SpotifyManager();
        }
        return instance;
    }

    // ------------------------------------------------------------------ access

    public SpotifyAuth auth() {
        return auth;
    }

    public WindowsMediaSource localSource() {
        return local;
    }

    public CoverArtCache covers() {
        return covers;
    }

    public LyricsManager lyrics() {
        return lyrics;
    }

    public PlaybackState state() {
        return state.get();
    }

    /** The source actually in use right now. */
    public PlaybackSource effectiveSource() {
        PlaybackSource configured = SyncConfig.get().playbackSource;
        if (configured == PlaybackSource.WEB_API) {
            return PlaybackSource.WEB_API;
        }
        if (configured == PlaybackSource.WINDOWS_LOCAL) {
            return PlaybackSource.WINDOWS_LOCAL;
        }
        // AUTO: the Web API wins when authorized, otherwise fall back locally.
        if (auth.isAuthorized()) {
            return PlaybackSource.WEB_API;
        }
        return WindowsMediaSource.supported() ? PlaybackSource.WINDOWS_LOCAL : PlaybackSource.WEB_API;
    }

    public boolean usingLocalSource() {
        return effectiveSource() == PlaybackSource.WINDOWS_LOCAL;
    }

    /** True when the active source can deliver playback data. */
    public boolean connected() {
        if (usingLocalSource()) {
            return WindowsMediaSource.supported();
        }
        return auth.isAuthorized();
    }

    /** Human readable status shown in the settings screen. */
    public String sourceStatus() {
        if (usingLocalSource()) {
            if (!WindowsMediaSource.supported()) {
                return "local source needs Windows";
            }
            if (!local.lastError().isEmpty()) {
                return local.lastError();
            }
            return local.available()
                    ? "reading the local Spotify client"
                    : "waiting for the Spotify desktop app";
        }
        return auth.isAuthorized() ? "web api session active" : "web api not connected";
    }

    /** Controls that only the Web API can perform. */
    public boolean supportsExtendedControls() {
        return !usingLocalSource();
    }

    public boolean stale() {
        return lastSuccessNano != 0L
                && (System.nanoTime() - lastSuccessNano) > 20_000_000_000L;
    }

    public boolean everSynced() {
        return lastSuccessNano != 0L;
    }

    // ------------------------------------------------------------------ polling

    /** Called every client tick. */
    public void tick() {
        SyncConfig config = SyncConfig.get();
        if (!config.autoConnect || !connected()) {
            return;
        }
        long now = System.nanoTime();
        // Spawning a PowerShell process is heavier than an HTTP call, so the
        // local source is polled a bit less aggressively.
        long floor = usingLocalSource() ? 1000L : 700L;
        long intervalNano = Math.max(floor, config.pollIntervalMs) * 1_000_000L;
        if (now - lastPollNano < intervalNano) {
            return;
        }
        lastPollNano = now;
        poll();
    }

    /** Immediately schedules a playback poll on the active source. */
    public void poll() {
        if (!connected()) {
            return;
        }
        if (!pollInFlight.compareAndSet(false, true)) {
            return;
        }
        boolean useLocal = usingLocalSource();
        network.execute(() -> {
            try {
                PlaybackState fetched = useLocal ? local.poll() : api.fetchPlayback();
                if (fetched == null) {
                    return;
                }
                if (System.nanoTime() < optimisticUntilNano && !fetched.hasTrack()) {
                    return;
                }
                state.set(fetched);
                lastSuccessNano = System.nanoTime();
                if (fetched.hasTrack() && fetched.coverUrl() != null) {
                    covers.request(fetched.coverUrl());
                }
                if (!fetched.hasTrack() || SyncConfig.get().lyricsEnabled) {
                    lyrics.onPlayback(fetched);
                }
            } catch (Exception e) {
                SpotifySync.LOGGER.debug("[Spotify Sync] poll failed", e);
            } finally {
                pollInFlight.set(false);
            }
        });
    }

    public void shutdown() {
        auth.stopCallbackServer();
        network.shutdownNow();
    }

    // ----------------------------------------------------------------- controls

    public void togglePlayPause() {
        PlaybackState current = state.get();
        boolean wasPlaying = current.playing();
        applyOptimistic(current.withPlaying(!wasPlaying));
        boolean useLocal = usingLocalSource();
        network.execute(() -> {
            if (useLocal) {
                local.togglePlayPause();
            } else if (wasPlaying) {
                api.pause();
            } else {
                api.play();
            }
            sleep(350);
            poll();
        });
    }

    public void next() {
        boolean useLocal = usingLocalSource();
        network.execute(() -> {
            if (useLocal) {
                local.next();
            } else {
                api.next();
            }
            sleep(450);
            poll();
        });
    }

    public void previous() {
        boolean useLocal = usingLocalSource();
        network.execute(() -> {
            if (useLocal) {
                local.previous();
            } else {
                api.previous();
            }
            sleep(450);
            poll();
        });
    }

    public void seekFraction(float fraction) {
        PlaybackState current = state.get();
        if (!current.hasTrack() || current.durationMs() <= 0L) {
            return;
        }
        long target = (long) (Math.max(0f, Math.min(1f, fraction)) * current.durationMs());
        seekAbsolute(target);
    }

    public void seekRelative(long deltaMs) {
        PlaybackState current = state.get();
        if (!current.hasTrack()) {
            return;
        }
        long target = Math.max(0L, Math.min(current.durationMs(), current.interpolatedProgressMs() + deltaMs));
        seekAbsolute(target);
    }

    private void seekAbsolute(long target) {
        applyOptimistic(state.get().withProgress(target));
        boolean useLocal = usingLocalSource();
        network.execute(() -> {
            if (useLocal) {
                local.seek(target);
            } else {
                api.seek(target);
            }
            sleep(400);
            poll();
        });
    }

    public void setVolume(int percent) {
        if (usingLocalSource()) {
            // The media session API exposes no volume channel.
            return;
        }
        PlaybackState current = state.get();
        applyOptimistic(current.withVolume(percent));
        network.execute(() -> {
            api.volume(percent);
            sleep(300);
            poll();
        });
    }

    public void toggleShuffle() {
        if (usingLocalSource()) {
            return;
        }
        PlaybackState current = state.get();
        boolean target = !current.shuffle();
        applyOptimistic(current.withShuffle(target));
        network.execute(() -> {
            api.shuffle(target);
            sleep(300);
            poll();
        });
    }

    public void cycleRepeat() {
        if (usingLocalSource()) {
            return;
        }
        PlaybackState current = state.get();
        String target = switch (current.repeatState()) {
            case "off" -> "context";
            case "context" -> "track";
            default -> "off";
        };
        applyOptimistic(current.withRepeat(target));
        network.execute(() -> {
            api.repeat(target);
            sleep(300);
            poll();
        });
    }

    public void beginLogin() {
        network.execute(() -> {
            auth.beginLogin();
            for (int i = 0; i < 120; i++) {
                sleep(1000);
                if (auth.status() == SpotifyAuth.Status.CONNECTED) {
                    poll();
                    return;
                }
            }
        });
    }

    public void logout() {
        auth.logout();
        state.set(PlaybackState.EMPTY);
        covers.clear();
        lyrics.clear();
        lastSuccessNano = 0L;
    }

    private void applyOptimistic(PlaybackState updated) {
        state.set(updated);
        optimisticUntilNano = System.nanoTime() + 1_200_000_000L;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
