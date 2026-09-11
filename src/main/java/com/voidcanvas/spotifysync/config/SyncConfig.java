package com.voidcanvas.spotifysync.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.voidcanvas.spotifysync.SpotifySync;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Human readable JSON configuration.
 *
 * <p>A hand rolled config (instead of the NeoForge config spec) is used on
 * purpose: every value is edited live from the in-game settings screen and
 * written back immediately, which is far smoother than round-tripping through
 * a TOML spec.</p>
 */
public final class SyncConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SyncConfig instance;

    // ---------------------------------------------------------------- account
    /**
     * Where playback data comes from. {@link PlaybackSource#AUTO} prefers the
     * Web API when a session is authorized and falls back to the local Windows
     * media session, which needs no Premium subscription.
     */
    public PlaybackSource playbackSource = PlaybackSource.AUTO;
    /** Substring matched against the media session app id (Windows source). */
    public String localSessionFilter = "spotify";
    /** Spotify application client id (PKCE flow, no secret required). */
    public String clientId = "";
    /** Loopback port used for the OAuth redirect. */
    public int callbackPort = 8910;
    /** Automatically start polling when the game client launches. */
    public boolean autoConnect = true;
    /** How often the Spotify API is polled, in milliseconds. */
    public int pollIntervalMs = 2000;

    // ------------------------------------------------------------- mini player
    /** Master switch for the mini player HUD. */
    public boolean hudEnabled = true;
    public HudAnchor hudAnchor = HudAnchor.TOP_CENTER;
    public int hudOffsetX = 0;
    public int hudOffsetY = 6;
    public float hudScale = 1.0f;
    public float hudOpacity = 1.0f;
    /** Draw the mini player above open screens (inventory, chat, ...). */
    public boolean showOnScreens = true;
    public boolean showCover = true;
    public boolean showProgressBar = true;
    public boolean showTimecode = true;
    /** Hide the widget completely while nothing is playing. */
    public boolean hideWhenIdle = false;
    /** Subtle animated film grain over the dark panels. */
    public boolean grain = true;
    /** Accent hairlines / progress fill colour (RGB). */
    public int accentColor = 0x0099FF;

    // ----------------------------------------------------------------- lyrics
    public LyricsMode lyricsMode = LyricsMode.HUD;
    public boolean lyricsEnabled = true;
    /** Vertical position of the 2D lyrics band, as a fraction of screen height. */
    public float lyricsHudY = 0.78f;
    public float lyricsScale = 1.0f;
    /** Number of context lines shown above/below the active line. */
    public int lyricsContextLines = 1;
    public boolean lyricsShowOnScreens = false;
    /** Manual offset (ms) applied to synced lyrics timing. */
    public int lyricsOffsetMs = 0;

    // -------------------------------------------------------------- 3D lyrics
    public float ringRadius = 3.4f;
    public float ringHeight = 2.3f;
    public float ringScale = 1.0f;
    /** How many lyric lines orbit the player at once. */
    public int ringLineCount = 5;
    public boolean ringSpin = true;
    public float ringSpinSpeed = 1.0f;
    /** Lines float up and down softly. */
    public boolean ringBob = true;
    /** Render through blocks. */
    public boolean ringSeeThrough = true;
    public float ringOpacity = 0.9f;

    // ------------------------------------------------------------------ helpers
    public static SyncConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("spotifysync.json");
    }

    private static SyncConfig load() {
        Path file = path();
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                SyncConfig loaded = GSON.fromJson(json, SyncConfig.class);
                if (loaded != null) {
                    loaded.sanitise();
                    return loaded;
                }
            } catch (Exception e) {
                SpotifySync.LOGGER.warn("[Spotify Sync] could not read config, using defaults", e);
            }
        }
        SyncConfig fresh = new SyncConfig();
        fresh.save();
        return fresh;
    }

    private void sanitise() {
        if (hudAnchor == null) {
            hudAnchor = HudAnchor.TOP_CENTER;
        }
        if (lyricsMode == null) {
            lyricsMode = LyricsMode.HUD;
        }
        if (clientId == null) {
            clientId = "";
        }
        if (playbackSource == null) {
            playbackSource = PlaybackSource.AUTO;
        }
        if (localSessionFilter == null) {
            localSessionFilter = "spotify";
        }
        callbackPort = clamp(callbackPort, 1024, 65535);
        pollIntervalMs = clamp(pollIntervalMs, 700, 15000);
        hudScale = clamp(hudScale, 0.5f, 2.5f);
        hudOpacity = clamp(hudOpacity, 0.15f, 1.0f);
        lyricsScale = clamp(lyricsScale, 0.5f, 2.5f);
        lyricsHudY = clamp(lyricsHudY, 0.05f, 0.95f);
        lyricsContextLines = clamp(lyricsContextLines, 0, 4);
        lyricsOffsetMs = clamp(lyricsOffsetMs, -5000, 5000);
        ringRadius = clamp(ringRadius, 1.5f, 12.0f);
        ringHeight = clamp(ringHeight, -1.0f, 6.0f);
        ringScale = clamp(ringScale, 0.3f, 3.0f);
        ringLineCount = clamp(ringLineCount, 1, 12);
        ringSpinSpeed = clamp(ringSpinSpeed, 0.0f, 4.0f);
        ringOpacity = clamp(ringOpacity, 0.1f, 1.0f);
    }

    public void save() {
        sanitise();
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SpotifySync.LOGGER.warn("[Spotify Sync] could not save config", e);
        }
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
