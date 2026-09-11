package com.voidcanvas.spotifysync.client;

import com.voidcanvas.spotifysync.SpotifySync;
import com.voidcanvas.spotifysync.config.SyncConfig;
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
 */
public final class SpotifyManager {

    private static SpotifyManager instance;

    private final SpotifyAuth auth = new SpotifyAuth();
    private final SpotifyApi api = new SpotifyApi(auth);
    private final ExecutorService network;
    private final CoverArtCache covers;
    private final LyricsManager lyrics;

    private final AtomicReference<PlaybackState> state = new AtomicReference<>(PlaybackState.EMPTY);
    private final AtomicBoolean polling = new AtomicBoolean(false);
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

    public CoverArtCache covers() {
        return covers;
    }

    public LyricsManager lyrics() {
        return lyrics;
    }

    public PlaybackState state() {
        return state.get();
    }

    public boolean connected() {
        return auth.isAuthorized();
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
        if (!auth.isAuthorized() || !config.autoConnect) {
            return;
        }
        long now = System.nanoTime();
        long intervalNano = Math.max(700, config.pollIntervalMs) * 1_000_000L;
        if (now - lastPollNano < intervalNano) {
            return;
        }
        lastPollNano = now;
        poll();
    }

    /** Immediately schedules a playback poll. */
    public void poll() {
        if (!auth.isAuthorized()) {
            return;
        }
        if (!pollInFlight.compareAndSet(false, true)) {
            return;
        }
        network.execute(() -> {
            try {
                PlaybackState fetched = api.fetchPlayback();
                if (fetched == null) {
                    return;
                }
                if (System.nanoTime() < optimisticUntilNano && !fetched.hasTrack()) {
                    return;
                }
                state.set(fetched);
                lastSuccessNano = System.nanoTime();
                if (fetched.hasTrack()) {
                    if (fetched.coverUrl() != null) {
                        covers.request(fetched.coverUrl());
                    }
                    if (SyncConfig.get().lyricsEnabled) {
                        lyrics.onPlayback(fetched);
                    }
                } else {
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
        polling.set(false);
        auth.stopCallbackServer();
        network.shutdownNow();
    }

    // ----------------------------------------------------------------- controls

    public void togglePlayPause() {
        PlaybackState current = state.get();
        boolean wasPlaying = current.playing();
        applyOptimistic(current.withPlaying(!wasPlaying));
        network.execute(() -> {
            if (wasPlaying) {
                api.pause();
            } else {
                api.play();
            }
            sleep(350);
            poll();
        });
    }

    public void next() {
        network.execute(() -> {
            api.next();
            sleep(450);
            poll();
        });
    }

    public void previous() {
        network.execute(() -> {
            api.previous();
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
        applyOptimistic(current.withProgress(target));
        network.execute(() -> {
            api.seek(target);
            sleep(400);
            poll();
        });
    }

    public void seekRelative(long deltaMs) {
        PlaybackState current = state.get();
        if (!current.hasTrack()) {
            return;
        }
        long target = Math.max(0L, Math.min(current.durationMs(), current.interpolatedProgressMs() + deltaMs));
        applyOptimistic(current.withProgress(target));
        network.execute(() -> {
            api.seek(target);
            sleep(400);
            poll();
        });
    }

    public void setVolume(int percent) {
        PlaybackState current = state.get();
        applyOptimistic(current.withVolume(percent));
        network.execute(() -> {
            api.volume(percent);
            sleep(300);
            poll();
        });
    }

    public void toggleShuffle() {
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
