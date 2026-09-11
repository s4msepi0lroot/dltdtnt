package com.voidcanvas.spotifysync.config;

/**
 * Where playback information comes from.
 *
 * <p>Since February 2026 Spotify requires the developer account that owns the
 * application to have Premium, otherwise the Web API is blocked entirely. The
 * local source reads the Windows system media session instead, which works with
 * a free account as long as the Spotify desktop client is running.</p>
 */
public enum PlaybackSource {
    /** Local media session first, Web API when a session is authorized. */
    AUTO("auto"),
    /** Spotify Web API (requires Premium on the app owner account). */
    WEB_API("web_api"),
    /** Windows System Media Transport Controls (no Premium needed). */
    WINDOWS_LOCAL("windows_local");

    private final String key;

    PlaybackSource(String key) {
        this.key = key;
    }

    public String translationKey() {
        return "spotifysync.source." + key;
    }

    public PlaybackSource next() {
        PlaybackSource[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
