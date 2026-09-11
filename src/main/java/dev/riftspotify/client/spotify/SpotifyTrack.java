package dev.riftspotify.client.spotify;

public record SpotifyTrack(
        String id,
        String name,
        String artist,
        String album,
        String coverUrl,
        long progressMs,
        long durationMs,
        boolean playing
) {
    public String progressLabel() { return format(progressMs); }
    public String durationLabel() { return format(durationMs); }
    public static String format(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
