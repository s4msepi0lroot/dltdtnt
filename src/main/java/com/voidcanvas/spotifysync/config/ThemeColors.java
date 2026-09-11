package com.voidcanvas.spotifysync.config;

/**
 * User editable colour scheme ("Obsidian &amp; Lime" by default).
 *
 * <p>Every colour is stored as a plain 0xRRGGBB integer so it survives the JSON
 * round trip and can be edited by hand in {@code config/spotifysync.json} as
 * well as from the in-game theme editor. Alpha is never stored here: the
 * renderer decides how translucent each layer is, driven by the opacity
 * factors below.</p>
 */
public final class ThemeColors {

    /** Name of the preset this scheme was built from ({@code custom} when edited). */
    public String preset = ThemePreset.OBSIDIAN_LIME.name();

    // ---------------------------------------------------------------- surfaces
    /** The viewport behind everything (deep black). */
    public int background = 0x000000;
    /** The floating shell the whole UI lives in (obsidian). */
    public int shell = 0x0C0C0C;
    /** Raised cards inside the shell. */
    public int surface = 0x121212;
    /** Inputs, tracks and other sunken elements. */
    public int surfaceAlt = 0x1C1C1C;

    // ------------------------------------------------------------------ accents
    /** Primary accent, neon lime by default. */
    public int accent = 0xCCFF00;
    /** Secondary accent, emerald glow by default. */
    public int accentSecondary = 0x10B981;
    /** Destructive actions. */
    public int danger = 0xFF4D5E;

    // -------------------------------------------------------------------- text
    public int textPrimary = 0xEBEBEB;
    public int textSecondary = 0x9A9A9A;
    public int textMuted = 0x5A5A5A;

    /** Tint of hairline rings and glass borders (used at low alpha). */
    public int border = 0xFFFFFF;
    /** Tint of the glass overlay (used at {@link #glassOpacity}). */
    public int glass = 0xFFFFFF;

    // ---------------------------------------------------------------- intensity
    /** White overlay strength of glass panels (spec: 0.03). */
    public float glassOpacity = 0.05f;
    /** Strength of the 1px ring around glass panels (spec: 0.10). */
    public float borderOpacity = 0.14f;
    /** Strength of the decorative 60px grid pattern. */
    public float gridOpacity = 0.10f;
    /** Strength of the grainy noise overlay (spec: 0.15). */
    public float noiseOpacity = 0.13f;
    /** Strength of the large radial "glow sphere" behind panels. */
    public float glowOpacity = 0.40f;
    /** Backdrop darkening that stands in for the 16px backdrop blur. */
    public float blurOpacity = 0.55f;

    // ----------------------------------------------------------------- geometry
    /** Corner radius of cards, in GUI pixels (high radius = hardware feel). */
    public int cornerRadius = 10;
    /** Fully rounded (pill) buttons and tabs. */
    public boolean pillButtons = true;
    /** Draw the decorative grid inside panels. */
    public boolean gridPattern = true;
    /** Draw the noise overlay. */
    public boolean noiseOverlay = true;
    /** Draw glow spheres. */
    public boolean glowSpheres = true;

    public ThemePreset presetOrCustom() {
        try {
            return ThemePreset.valueOf(preset);
        } catch (Exception e) {
            return ThemePreset.CUSTOM;
        }
    }

    /** Marks the scheme as hand-edited so presets stop claiming ownership. */
    public void markCustom() {
        preset = ThemePreset.CUSTOM.name();
    }

    public void sanitise() {
        if (preset == null || preset.isBlank()) {
            preset = ThemePreset.OBSIDIAN_LIME.name();
        }
        background = rgb(background);
        shell = rgb(shell);
        surface = rgb(surface);
        surfaceAlt = rgb(surfaceAlt);
        accent = rgb(accent);
        accentSecondary = rgb(accentSecondary);
        danger = rgb(danger);
        textPrimary = rgb(textPrimary);
        textSecondary = rgb(textSecondary);
        textMuted = rgb(textMuted);
        border = rgb(border);
        glass = rgb(glass);
        glassOpacity = clamp(glassOpacity, 0f, 0.35f);
        borderOpacity = clamp(borderOpacity, 0f, 1f);
        gridOpacity = clamp(gridOpacity, 0f, 0.6f);
        noiseOpacity = clamp(noiseOpacity, 0f, 0.6f);
        glowOpacity = clamp(glowOpacity, 0f, 1f);
        blurOpacity = clamp(blurOpacity, 0f, 1f);
        cornerRadius = Math.max(0, Math.min(16, cornerRadius));
    }

    private static int rgb(int value) {
        return value & 0xFFFFFF;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Copies every value of {@code other} into this instance. */
    public void copyFrom(ThemeColors other) {
        preset = other.preset;
        background = other.background;
        shell = other.shell;
        surface = other.surface;
        surfaceAlt = other.surfaceAlt;
        accent = other.accent;
        accentSecondary = other.accentSecondary;
        danger = other.danger;
        textPrimary = other.textPrimary;
        textSecondary = other.textSecondary;
        textMuted = other.textMuted;
        border = other.border;
        glass = other.glass;
        glassOpacity = other.glassOpacity;
        borderOpacity = other.borderOpacity;
        gridOpacity = other.gridOpacity;
        noiseOpacity = other.noiseOpacity;
        glowOpacity = other.glowOpacity;
        blurOpacity = other.blurOpacity;
        cornerRadius = other.cornerRadius;
        pillButtons = other.pillButtons;
        gridPattern = other.gridPattern;
        noiseOverlay = other.noiseOverlay;
        glowSpheres = other.glowSpheres;
    }
}
