package com.voidcanvas.spotifysync.config;

/**
 * Ready made colour schemes. The player can pick one and then tweak any single
 * token in the theme editor, which flips the scheme to {@link #CUSTOM}.
 */
public enum ThemePreset {

    /** The shipped default: obsidian surfaces, neon lime accent. */
    OBSIDIAN_LIME(0x000000, 0x0C0C0C, 0x121212, 0x1C1C1C, 0xCCFF00, 0x10B981,
            0xEBEBEB, 0x9A9A9A, 0x5A5A5A),
    /** The previous look: near-black with electric blue. */
    VOID_NOIR(0x000000, 0x0A0A0A, 0x111111, 0x1E1E1E, 0x0099FF, 0x00BB88,
            0xFFFFFF, 0x999999, 0x666666),
    /** Spotify-ish greens. */
    SPOTIFY_GREEN(0x000000, 0x0B0F0C, 0x121712, 0x1C231C, 0x1DB954, 0x8CE99A,
            0xF2F5F2, 0x9BA69B, 0x5C665C),
    /** Magenta / violet neon. */
    MAGENTA_PULSE(0x000000, 0x0D0A10, 0x141019, 0x1F1826, 0xFF2FD0, 0x8B5CF6,
            0xF5EBF8, 0xA694AD, 0x63566B),
    /** Warm amber terminal. */
    AMBER_TERMINAL(0x000000, 0x0E0A05, 0x16100A, 0x221A10, 0xFFB000, 0xFF7A29,
            0xF7EEDF, 0xA89578, 0x6B5E4A),
    /** Cold monochrome with an ice-blue hairline. */
    ICE_MONO(0x000000, 0x0B0C0D, 0x111315, 0x1B1E21, 0xCFE8FF, 0x7FB4D8,
            0xF0F3F5, 0x99A2A8, 0x5C6469),
    /** Hand edited scheme. */
    CUSTOM(0x000000, 0x0C0C0C, 0x121212, 0x1C1C1C, 0xCCFF00, 0x10B981,
            0xEBEBEB, 0x9A9A9A, 0x5A5A5A);

    private final int background;
    private final int shell;
    private final int surface;
    private final int surfaceAlt;
    private final int accent;
    private final int accentSecondary;
    private final int textPrimary;
    private final int textSecondary;
    private final int textMuted;

    ThemePreset(int background, int shell, int surface, int surfaceAlt, int accent, int accentSecondary,
                int textPrimary, int textSecondary, int textMuted) {
        this.background = background;
        this.shell = shell;
        this.surface = surface;
        this.surfaceAlt = surfaceAlt;
        this.accent = accent;
        this.accentSecondary = accentSecondary;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.textMuted = textMuted;
    }

    public int accent() {
        return accent;
    }

    public int surface() {
        return surface;
    }

    public String translationKey() {
        return "spotifysync.theme." + name().toLowerCase();
    }

    public ThemePreset next() {
        ThemePreset[] values = values();
        // CUSTOM is never reachable by cycling; it is set by editing a token.
        int index = (ordinal() + 1) % values.length;
        if (values[index] == CUSTOM) {
            index = 0;
        }
        return values[index];
    }

    /** Writes this preset's palette into the given scheme. */
    public void applyTo(ThemeColors colors) {
        colors.preset = name();
        colors.background = background;
        colors.shell = shell;
        colors.surface = surface;
        colors.surfaceAlt = surfaceAlt;
        colors.accent = accent;
        colors.accentSecondary = accentSecondary;
        colors.textPrimary = textPrimary;
        colors.textSecondary = textSecondary;
        colors.textMuted = textMuted;
        colors.border = 0xFFFFFF;
        colors.glass = 0xFFFFFF;
        colors.sanitise();
    }
}
