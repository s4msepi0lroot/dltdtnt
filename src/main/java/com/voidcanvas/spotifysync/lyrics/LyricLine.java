package com.voidcanvas.spotifysync.lyrics;

/** A single lyric line with an optional timestamp (ms from track start). */
public record LyricLine(long timeMs, String text) {

    public boolean timed() {
        return timeMs >= 0L;
    }
}
