package com.voidcanvas.spotifysync.client.render;

import com.voidcanvas.spotifysync.config.SyncConfig;
import com.voidcanvas.spotifysync.config.ThemeColors;

/**
 * "Obsidian &amp; Lime" design tokens.
 *
 * <p>Unlike the previous version these are methods, not constants: every colour
 * is read live from {@link ThemeColors} so the in-game theme editor can repaint
 * the whole interface while it is open. Values are returned as 0xAARRGGBB and
 * can be handed straight to {@code GuiGraphics}.</p>
 */
public final class UiTheme {

    private UiTheme() {
    }

    public static ThemeColors colors() {
        return SyncConfig.get().theme;
    }

    // ---------------------------------------------------------------- surfaces
    public static int background() {
        return opaque(colors().background);
    }

    /** The floating shell that holds the whole interface. */
    public static int shell() {
        return opaque(colors().shell);
    }

    public static int surface() {
        return opaque(colors().surface);
    }

    public static int surfaceAlt() {
        return opaque(colors().surfaceAlt);
    }

    /** Sunken track colour for sliders and progress bars. */
    public static int track() {
        return opaque(colors().surfaceAlt);
    }

    /** Translucent white overlay of a glass panel. */
    public static int glass(float alpha) {
        ThemeColors c = colors();
        return argb(c.glass, c.glassOpacity * alpha);
    }

    /** 1px ring around glass panels. */
    public static int ring(float alpha) {
        ThemeColors c = colors();
        return argb(c.border, c.borderOpacity * alpha);
    }

    /** Backdrop darkening that stands in for the CSS backdrop blur. */
    public static int scrim(float alpha) {
        return argb(colors().background, colors().blurOpacity * alpha);
    }

    // ------------------------------------------------------------------ accents
    public static int accent() {
        return opaque(colors().accent);
    }

    public static int accent(float alpha) {
        return argb(colors().accent, alpha);
    }

    public static int accentSecondary() {
        return opaque(colors().accentSecondary);
    }

    public static int accentSecondary(float alpha) {
        return argb(colors().accentSecondary, alpha);
    }

    public static int danger() {
        return opaque(colors().danger);
    }

    /** Readable text colour on top of a solid accent fill. */
    public static int onAccent() {
        return luminance(colors().accent) > 0.55f ? 0xFF000000 : 0xFFFFFFFF;
    }

    // -------------------------------------------------------------------- text
    public static int textPrimary() {
        return opaque(colors().textPrimary);
    }

    public static int textPrimary(float alpha) {
        return argb(colors().textPrimary, alpha);
    }

    public static int textSecondary() {
        return opaque(colors().textSecondary);
    }

    public static int textSecondary(float alpha) {
        return argb(colors().textSecondary, alpha);
    }

    public static int textMuted() {
        return opaque(colors().textMuted);
    }

    public static int textMuted(float alpha) {
        return argb(colors().textMuted, alpha);
    }

    // ----------------------------------------------------------------- geometry
    /** Card radius ("at least 2rem" in the spec, scaled to GUI pixels). */
    public static int radiusCard() {
        return Math.max(0, colors().cornerRadius);
    }

    /** Radius of small controls; pills are handled by the renderer. */
    public static int radiusControl() {
        return Math.max(2, colors().cornerRadius / 2);
    }

    public static boolean pills() {
        return colors().pillButtons;
    }

    /** Decorative grid cell size (60px in the spec, 20 GUI pixels in game). */
    public static final int GRID_CELL = 20;
    public static final int GAP = 10;
    public static final int BASE = 5;

    // ------------------------------------------------------------------ helpers
    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Builds 0xAARRGGBB from an RGB triplet and an absolute alpha (0..1). */
    public static int argb(int rgb, float alpha) {
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Scales the alpha channel of an existing 0xAARRGGBB colour. */
    public static int withAlpha(int argb, float alpha) {
        int base = (argb >>> 24) == 0 ? 255 : (argb >>> 24);
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * base);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int rgb(int argb) {
        return argb & 0xFFFFFF;
    }

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

    public static float luminance(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    /** Mixes an artwork colour towards the accent so it never clashes. */
    public static int tintFromCover(int coverRgb) {
        return lerpColor(opaque(coverRgb), accent(), 0.35f);
    }

    /** Pulse factor (0..1) used by status dots and the active lyric line. */
    public static float pulse(double periodSeconds) {
        double seconds = System.nanoTime() / 1_000_000_000.0;
        double phase = (seconds % periodSeconds) / periodSeconds;
        return (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2.0));
    }
}
