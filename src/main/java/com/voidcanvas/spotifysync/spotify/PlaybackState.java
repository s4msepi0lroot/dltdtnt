package com.voidcanvas.spotifysync.spotify;

import java.util.List;

/**
 * Immutable snapshot of what Spotify is currently doing.
 *
 * <p>The snapshot is produced by the polling thread and consumed by the render
 * thread, therefore every field is final. Playback progress is interpolated
 * locally via {@link #interpolatedProgressMs()} so the UI stays smooth between
 * two API calls.</p>
 */
public final class PlaybackState {

    public static final PlaybackState EMPTY = new PlaybackState(
            null, "", List.of(), "", 0L, 0L, false, null, "", -1,
            false, "off", false, "", "", 0, System.nanoTime());

    private final String trackId;
    private final String title;
    private final List<String> artists;
    private final String album;
    private final long durationMs;
    private final long progressMs;
    private final boolean playing;
    private final String coverUrl;
    private final String deviceName;
    private final int volumePercent;
    private final boolean shuffle;
    private final String repeatState;
    private final boolean episode;
    private final String releaseDate;
    private final String trackUrl;
    private final int popularity;
    private final long sampledAtNano;

    public PlaybackState(String trackId,
                         String title,
                         List<String> artists,
                         String album,
                         long durationMs,
                         long progressMs,
                         boolean playing,
                         String coverUrl,
                         String deviceName,
                         int volumePercent,
                         boolean shuffle,
                         String repeatState,
                         boolean episode,
                         String releaseDate,
                         String trackUrl,
                         int popularity,
                         long sampledAtNano) {
        this.trackId = trackId;
        this.title = title == null ? "" : title;
        this.artists = artists == null ? List.of() : List.copyOf(artists);
        this.album = album == null ? "" : album;
        this.durationMs = Math.max(0L, durationMs);
        this.progressMs = Math.max(0L, progressMs);
        this.playing = playing;
        this.coverUrl = coverUrl;
        this.deviceName = deviceName == null ? "" : deviceName;
        this.volumePercent = volumePercent;
        this.shuffle = shuffle;
        this.repeatState = repeatState == null ? "off" : repeatState;
        this.episode = episode;
        this.releaseDate = releaseDate == null ? "" : releaseDate;
        this.trackUrl = trackUrl == null ? "" : trackUrl;
        this.popularity = popularity;
        this.sampledAtNano = sampledAtNano;
    }

    public boolean hasTrack() {
        return trackId != null && !title.isEmpty();
    }

    public String trackId() {
        return trackId;
    }

    public String title() {
        return title;
    }

    public List<String> artists() {
        return artists;
    }

    public String artistLine() {
        return String.join(", ", artists);
    }

    public String album() {
        return album;
    }

    public long durationMs() {
        return durationMs;
    }

    public long rawProgressMs() {
        return progressMs;
    }

    public boolean playing() {
        return playing;
    }

    public String coverUrl() {
        return coverUrl;
    }

    public String deviceName() {
        return deviceName;
    }

    public int volumePercent() {
        return volumePercent;
    }

    public boolean shuffle() {
        return shuffle;
    }

    public String repeatState() {
        return repeatState;
    }

    public boolean episode() {
        return episode;
    }

    public String releaseDate() {
        return releaseDate;
    }

    public String trackUrl() {
        return trackUrl;
    }

    public int popularity() {
        return popularity;
    }

    /** Progress extrapolated with the local clock, clamped to the duration. */
    public long interpolatedProgressMs() {
        if (!hasTrack()) {
            return 0L;
        }
        if (!playing) {
            return Math.min(progressMs, durationMs);
        }
        long elapsed = (System.nanoTime() - sampledAtNano) / 1_000_000L;
        return Math.max(0L, Math.min(progressMs + elapsed, durationMs));
    }

    public float progressFraction() {
        if (durationMs <= 0L) {
            return 0f;
        }
        return (float) interpolatedProgressMs() / (float) durationMs;
    }

    // ------------------------------------------------------- optimistic copies

    private PlaybackState copy(long newProgressMs,
                               boolean newPlaying,
                               int newVolume,
                               boolean newShuffle,
                               String newRepeat) {
        return new PlaybackState(trackId, title, artists, album, durationMs, newProgressMs, newPlaying,
                coverUrl, deviceName, newVolume, newShuffle, newRepeat, episode, releaseDate, trackUrl,
                popularity, System.nanoTime());
    }

    public PlaybackState withPlaying(boolean newPlaying) {
        return copy(interpolatedProgressMs(), newPlaying, volumePercent, shuffle, repeatState);
    }

    public PlaybackState withProgress(long newProgressMs) {
        return copy(newProgressMs, playing, volumePercent, shuffle, repeatState);
    }

    public PlaybackState withVolume(int newVolume) {
        return copy(interpolatedProgressMs(), playing, newVolume, shuffle, repeatState);
    }

    public PlaybackState withShuffle(boolean newShuffle) {
        return copy(interpolatedProgressMs(), playing, volumePercent, newShuffle, repeatState);
    }

    public PlaybackState withRepeat(String newRepeat) {
        return copy(interpolatedProgressMs(), playing, volumePercent, shuffle, newRepeat);
    }

    public static String formatTime(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }
}
