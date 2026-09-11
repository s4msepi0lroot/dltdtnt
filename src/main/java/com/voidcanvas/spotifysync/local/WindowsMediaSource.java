package com.voidcanvas.spotifysync.local;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.voidcanvas.spotifysync.SpotifySync;
import com.voidcanvas.spotifysync.config.SyncConfig;
import com.voidcanvas.spotifysync.spotify.PlaybackState;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Premium-free playback source for Windows.
 *
 * <p>Spotify blocks the Web API for developer accounts without Premium, so this
 * source reads the operating system's media session instead
 * ({@code Windows.Media.Control.GlobalSystemMediaTransportControls}). It gives
 * the track title, artist, album, play state, position, duration and the album
 * artwork of whatever the local Spotify desktop client is playing, and it can
 * also toggle play/pause, skip and seek.</p>
 *
 * <p>The WinRT bridge is a small PowerShell script shipped inside the jar and
 * extracted into the config folder on first use.</p>
 */
public final class WindowsMediaSource {

    /** Bump when the shipped script changes so it gets re-extracted. */
    private static final String SCRIPT_VERSION = "1";
    private static final String SCRIPT_RESOURCE = "/spotifysync-smtc.ps1";
    private static final long PROCESS_TIMEOUT_SECONDS = 10L;

    private volatile String lastError = "";
    private volatile boolean available;
    private volatile boolean scriptReady;

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public boolean available() {
        return available;
    }

    public String lastError() {
        return lastError == null ? "" : lastError;
    }

    // ------------------------------------------------------------------ paths

    private static Path scriptPath() {
        return FMLPaths.CONFIGDIR.get().resolve("spotifysync-smtc.ps1");
    }

    private static Path markerPath() {
        return FMLPaths.CONFIGDIR.get().resolve("spotifysync-smtc.version");
    }

    private static Path coverDir() {
        return FMLPaths.CONFIGDIR.get().resolve("spotifysync-covers");
    }

    /** Extracts (or refreshes) the bridge script next to the config file. */
    private synchronized boolean ensureScript() {
        if (scriptReady) {
            return true;
        }
        try {
            Path script = scriptPath();
            Path marker = markerPath();
            boolean upToDate = Files.isRegularFile(script)
                    && Files.isRegularFile(marker)
                    && SCRIPT_VERSION.equals(Files.readString(marker, StandardCharsets.UTF_8).trim());
            if (!upToDate) {
                Files.createDirectories(script.getParent());
                try (InputStream in = WindowsMediaSource.class.getResourceAsStream(SCRIPT_RESOURCE)) {
                    if (in == null) {
                        lastError = "bridge script missing from the jar";
                        return false;
                    }
                    Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.writeString(marker, SCRIPT_VERSION, StandardCharsets.UTF_8);
            }
            Files.createDirectories(coverDir());
            scriptReady = true;
            return true;
        } catch (Exception e) {
            lastError = "could not prepare bridge script";
            SpotifySync.LOGGER.warn("[Spotify Sync] could not extract SMTC bridge", e);
            return false;
        }
    }

    // ----------------------------------------------------------------- polling

    /**
     * Reads the current media session.
     *
     * @return a snapshot, or {@code null} when the session could not be read.
     */
    public PlaybackState poll() {
        JsonObject json = run("status", "");
        if (json == null) {
            available = false;
            return null;
        }
        if (!optBool(json, "ok")) {
            available = false;
            lastError = optString(json, "error", "windows media session unavailable");
            return null;
        }
        available = true;
        lastError = "";

        if (!optBool(json, "hasTrack")) {
            return PlaybackState.EMPTY;
        }

        String title = optString(json, "title", "");
        String artist = optString(json, "artist", "");
        String album = optString(json, "album", "");
        String cover = optString(json, "cover", "");
        String trackKey = optString(json, "trackKey", "");
        String app = optString(json, "app", "");
        long position = optLong(json, "positionMs");
        long duration = optLong(json, "durationMs");
        boolean playing = optBool(json, "playing");

        List<String> artists = artist.isEmpty()
                ? List.of()
                : List.of(artist.split("\\s*[;\u2022]\\s*|\\s*,\\s*"));

        return new PlaybackState(
                "local:" + (trackKey.isEmpty() ? title : trackKey),
                title,
                artists,
                album,
                duration,
                position,
                playing,
                cover.isEmpty() ? null : "file:" + cover,
                friendlyDeviceName(app),
                -1,
                false,
                "off",
                false,
                "",
                "",
                0,
                System.nanoTime());
    }

    private static String friendlyDeviceName(String appId) {
        if (appId == null || appId.isEmpty()) {
            return "Local session";
        }
        String lower = appId.toLowerCase();
        if (lower.contains("spotify")) {
            return "Spotify desktop";
        }
        int dot = appId.indexOf('.');
        return dot > 0 ? appId.substring(0, dot) : appId;
    }

    // ---------------------------------------------------------------- controls

    public void togglePlayPause() {
        run("playpause", "");
    }

    public void next() {
        run("next", "");
    }

    public void previous() {
        run("previous", "");
    }

    public void seek(long positionMs) {
        run("seek", String.valueOf(Math.max(0L, positionMs)));
    }

    // ----------------------------------------------------------------- process

    private JsonObject run(String command, String argument) {
        if (!supported()) {
            lastError = "local source only works on Windows";
            return null;
        }
        if (!ensureScript()) {
            return null;
        }
        SyncConfig config = SyncConfig.get();
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy", "Bypass",
                    "-File", scriptPath().toString(),
                    "-Command", command,
                    "-Arg", argument,
                    "-Filter", config.localSessionFilter,
                    "-OutDir", coverDir().toString());
            builder.redirectErrorStream(false);
            process = builder.start();
            byte[] out = process.getInputStream().readAllBytes();
            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                lastError = "windows bridge timed out";
                return null;
            }
            String text = new String(out, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                lastError = "windows bridge returned nothing";
                return null;
            }
            int brace = text.indexOf('{');
            if (brace > 0) {
                text = text.substring(brace);
            }
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            lastError = "windows bridge failed";
            SpotifySync.LOGGER.debug("[Spotify Sync] SMTC bridge call failed", e);
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    // -------------------------------------------------------------- json utils

    private static boolean optBool(JsonObject json, String key) {
        try {
            return json.has(key) && !json.get(key).isJsonNull() && json.get(key).getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private static long optLong(JsonObject json, String key) {
        try {
            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsLong() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String optString(JsonObject json, String key, String fallback) {
        try {
            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
