package com.voidcanvas.spotifysync.lyrics;

import com.voidcanvas.spotifysync.spotify.PlaybackState;

import java.net.http.HttpClient;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the lyrics of the currently playing track in memory and fetches new
 * ones asynchronously whenever the track changes.
 */
public final class LyricsManager {

    private final LrcLibClient client;
    private final ExecutorService executor;

    private final AtomicReference<TrackLyrics> current = new AtomicReference<>(TrackLyrics.NONE);
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private volatile String requestedTrackId;
    private volatile String failedTrackId;

    public LyricsManager(HttpClient http, ExecutorService executor) {
        this.client = new LrcLibClient(http);
        this.executor = executor;
    }

    public TrackLyrics lyrics() {
        return current.get();
    }

    public boolean loading() {
        return loading.get();
    }

    public boolean failedForCurrentTrack(String trackId) {
        return trackId != null && trackId.equals(failedTrackId);
    }

    /** Called from the polling loop with the freshest playback snapshot. */
    public void onPlayback(PlaybackState state) {
        if (state == null || !state.hasTrack()) {
            requestedTrackId = null;
            current.set(TrackLyrics.NONE);
            return;
        }
        String trackId = state.trackId();
        if (Objects.equals(trackId, requestedTrackId)) {
            return;
        }
        requestedTrackId = trackId;
        current.set(TrackLyrics.NONE);
        if (Objects.equals(trackId, failedTrackId)) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        String title = state.title();
        String artist = state.artists().isEmpty() ? "" : state.artists().get(0);
        String album = state.album();
        long duration = state.durationMs();
        executor.execute(() -> {
            try {
                TrackLyrics fetched = client.fetch(trackId, title, artist, album, duration);
                if (Objects.equals(trackId, requestedTrackId)) {
                    current.set(fetched);
                    failedTrackId = fetched.isEmpty() ? trackId : null;
                }
            } finally {
                loading.set(false);
            }
        });
    }

    /** Forces a new lookup for the given track (used by the retry button). */
    public void retry(PlaybackState state) {
        failedTrackId = null;
        requestedTrackId = null;
        onPlayback(state);
    }

    public void clear() {
        requestedTrackId = null;
        failedTrackId = null;
        current.set(TrackLyrics.NONE);
    }
}
