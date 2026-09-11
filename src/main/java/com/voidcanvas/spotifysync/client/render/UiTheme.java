package com.voidcanvas.spotifysync.client.render;

import com.voidcanvas.spotifysync.config.SyncConfig;

/**
 * "Void-canvas agent noir" design tokens.
 *
 * <p>Near-black surfaces, a single rationed electric blue accent, hairline
 * borders and slight (8-10px equivalent) corner rounding. Colours are stored
 * as 0xAARRGGBB so they can be handed straight to {@code GuiGraphics}.</p>
 */
public final class UiTheme {

    private UiTheme() {
    }

    // ---------------------------------------------------------------- palette
    public static final int BACKGROUND = 0xFF000000;
    public static final int SURFACE = 0xFF111111;
    public static final int SURFACE_ALT = 0xFF1E1E1E;
    public static final int TEXT_PRIMARY = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFF999999;
    public static final int TEXT_MUTED = 0xFF666666;
    public static final int ACCENT = 0xFF0099FF;
    public static final int ACCENT_SECONDARY = 0xFF00BB88;
    public static final int BORDER = 0x66767676;
    public static final int BORDER_STRONG = 0xBB767676;
    public static final int GLASS = 0x380099FF;
    public static final int SCRIM = 0xD9000000;
    public static final int TRACK = 0xFF262626;

    // ---------------------------------------------------------------- geometry
    /** 5px base scale from the design spec, mapped 1:1 onto GUI pixels. */
    public static final int BASE = 5;
    public static final int GAP = 10;
    public static final int RADIUS_CONTROL = 3;
    public static final int RADIUS_CARD = 5;
    public static final int RADIUS_CARD_LG = 7;

    /** Accent colour from the config, as 0xAARRGGBB with full alpha. */
    public static int accent() {
        return 0xFF000000 | (SyncConfig.get().accentColor & 0xFFFFFF);
    }

    public static int accent(float alpha) {
        return withAlpha(accent(), alpha);
    }

    public static int withAlpha(int argb, float alpha) {
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * ((argb >>> 24) == 0 ? 255 : (argb >>> 24)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int rgb(int argb) {
        return argb & 0xFFFFFF;
    }

    /** Linear interpolation between two ARGB colours. */
    public static int lerpColor(int from, int to, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        int a = lerpChannel(from >>> 24, to >>> 24, clamped);
        int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, clamped);
        int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, clamped);
        int b = lerpChannel(from & 0xFF, to & 0xFF, clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, float t) {
        return (int) (from + (to - from) * t) & 0xFF;
    }

    /** Mixes an artwork colour towards the accent so it never gets garish. */
    public static int tintFromCover(int coverRgb) {
        return lerpColor(0xFF000000 | (coverRgb & 0xFFFFFF), accent(), 0.35f);
    }
}
