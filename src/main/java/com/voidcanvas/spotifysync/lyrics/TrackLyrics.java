package com.voidcanvas.spotifysync.lyrics;

import java.util.List;

/**
 * Lyrics for one track.
 *
 * @param trackId  Spotify id the lyrics were fetched for
 * @param lines    ordered lyric lines
 * @param synced   whether the lines carry usable timestamps
 * @param source   provider label shown in the UI
 */
public record TrackLyrics(String trackId, List<String> rawLines, List<LyricLine> lines, boolean synced, String source) {

    public static final TrackLyrics NONE = new TrackLyrics(null, List.of(), List.of(), false, "");

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** Index of the line that should be highlighted at the given position. */
    public int activeIndex(long positionMs) {
        if (lines.isEmpty()) {
            return -1;
        }
        if (!synced) {
            return -1;
        }
        int result = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs() <= positionMs) {
                result = i;
            } else {
                break;
            }
        }
        return result;
    }

    /** Fraction (0..1) of the way through the active line. */
    public float lineProgress(int index, long positionMs, long trackDurationMs) {
        if (!synced || index < 0 || index >= lines.size()) {
            return 0f;
        }
        long start = lines.get(index).timeMs();
        long end = index + 1 < lines.size() ? lines.get(index + 1).timeMs() : trackDurationMs;
        if (end <= start) {
            return 0f;
        }
        float value = (float) (positionMs - start) / (float) (end - start);
        return Math.max(0f, Math.min(1f, value));
    }
}
