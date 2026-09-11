package com.voidcanvas.spotifysync.config;

/** How (and where) the lyrics of the current track should be rendered. */
public enum LyricsMode {
    OFF("off"),
    HUD("hud"),
    RING_3D("ring3d"),
    BOTH("both");

    private final String key;

    LyricsMode(String key) {
        this.key = key;
    }

    public String translationKey() {
        return "spotifysync.lyrics_mode." + key;
    }

    public boolean showsHud() {
        return this == HUD || this == BOTH;
    }

    public boolean shows3d() {
        return this == RING_3D || this == BOTH;
    }

    public LyricsMode next() {
        LyricsMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
