package dev.riftspotify.client.spotify;

import java.util.List;

public record Lyrics(List<LrcLine> lines, String plainText, String source) {
    public static Lyrics empty() { return new Lyrics(List.of(), "", "none"); }
    public boolean available() { return !lines.isEmpty() || (plainText != null && !plainText.isBlank()); }
}
