package com.voidcanvas.spotifysync.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.voidcanvas.spotifysync.SpotifySync;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lyrics provider backed by the free, keyless <a href="https://lrclib.net">LRCLIB</a>
 * database. Synced (LRC) lyrics are preferred; plain lyrics are used as a
 * fallback so that at least something can be displayed.
 */
public final class LrcLibClient {

    private static final String USER_AGENT = "SpotifySync-Minecraft/1.0.0 (https://github.com/your-name/SpotifySync)";

    private final HttpClient http;

    public LrcLibClient(HttpClient http) {
        this.http = http;
    }

    /** Blocking lookup. Never returns {@code null}. */
    public TrackLyrics fetch(String trackId, String title, String artist, String album, long durationMs) {
        if (title == null || title.isBlank()) {
            return TrackLyrics.NONE;
        }
        TrackLyrics exact = tryGet(trackId, title, artist, album, durationMs);
        if (!exact.isEmpty()) {
            return exact;
        }
        return trySearch(trackId, title, artist);
    }

    private TrackLyrics tryGet(String trackId, String title, String artist, String album, long durationMs) {
        StringBuilder url = new StringBuilder("https://lrclib.net/api/get?track_name=")
                .append(enc(cleanTitle(title)))
                .append("&artist_name=").append(enc(artist == null ? "" : artist));
        if (album != null && !album.isBlank()) {
            url.append("&album_name=").append(enc(album));
        }
        if (durationMs > 0L) {
            url.append("&duration=").append(Math.round(durationMs / 1000.0));
        }
        JsonElement element = get(url.toString());
        if (element == null || !element.isJsonObject()) {
            return TrackLyrics.NONE;
        }
        return parseRecord(trackId, element.getAsJsonObject());
    }

    private TrackLyrics trySearch(String trackId, String title, String artist) {
        String url = "https://lrclib.net/api/search?track_name=" + enc(cleanTitle(title))
                + "&artist_name=" + enc(artist == null ? "" : artist);
        JsonElement element = get(url);
        if (element == null || !element.isJsonArray()) {
            return TrackLyrics.NONE;
        }
        JsonArray array = element.getAsJsonArray();
        TrackLyrics plainFallback = TrackLyrics.NONE;
        for (JsonElement candidate : array) {
            if (!candidate.isJsonObject()) {
                continue;
            }
            TrackLyrics parsed = parseRecord(trackId, candidate.getAsJsonObject());
            if (parsed.synced()) {
                return parsed;
            }
            if (plainFallback.isEmpty() && !parsed.isEmpty()) {
                plainFallback = parsed;
            }
        }
        return plainFallback;
    }

    private static TrackLyrics parseRecord(String trackId, JsonObject record) {
        String synced = optString(record, "syncedLyrics");
        String plain = optString(record, "plainLyrics");
        if (synced != null && !synced.isBlank()) {
            List<LyricLine> lines = parseLrc(synced);
            if (!lines.isEmpty()) {
                List<String> raw = new ArrayList<>(lines.size());
                for (LyricLine line : lines) {
                    raw.add(line.text());
                }
                return new TrackLyrics(trackId, raw, lines, true, "LRCLIB");
            }
        }
        if (plain != null && !plain.isBlank()) {
            List<String> raw = new ArrayList<>();
            List<LyricLine> lines = new ArrayList<>();
            for (String rawLine : plain.split("\\r?\\n")) {
                String text = rawLine.trim();
                raw.add(text);
                lines.add(new LyricLine(-1L, text));
            }
            return new TrackLyrics(trackId, raw, lines, false, "LRCLIB");
        }
        return TrackLyrics.NONE;
    }

    /** Parses an LRC payload, supporting several timestamps per line. */
    public static List<LyricLine> parseLrc(String lrc) {
        List<LyricLine> lines = new ArrayList<>();
        for (String rawLine : lrc.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            List<Long> stamps = new ArrayList<>();
            int cursor = 0;
            while (cursor < line.length() && line.charAt(cursor) == '[') {
                int close = line.indexOf(']', cursor);
                if (close < 0) {
                    break;
                }
                String stamp = line.substring(cursor + 1, close);
                Long millis = parseStamp(stamp);
                if (millis == null) {
                    break;
                }
                stamps.add(millis);
                cursor = close + 1;
            }
            if (stamps.isEmpty()) {
                continue;
            }
            String text = line.substring(cursor).trim();
            for (Long stamp : stamps) {
                lines.add(new LyricLine(stamp, text));
            }
        }
        lines.sort((a, b) -> Long.compare(a.timeMs(), b.timeMs()));
        return lines;
    }

    private static Long parseStamp(String stamp) {
        int colon = stamp.indexOf(':');
        if (colon < 0) {
            return null;
        }
        try {
            int minutes = Integer.parseInt(stamp.substring(0, colon).trim());
            String rest = stamp.substring(colon + 1).replace(',', '.').trim();
            double seconds = Double.parseDouble(rest);
            return Math.round(minutes * 60_000L + seconds * 1000.0);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonElement get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2 || response.body() == null || response.body().isBlank()) {
                return null;
            }
            return JsonParser.parseString(response.body());
        } catch (Exception e) {
            SpotifySync.LOGGER.debug("[Spotify Sync] lyrics lookup failed", e);
            return null;
        }
    }

    /** Strips "- Remastered 2011", "(feat. X)" style noise that breaks matching. */
    public static String cleanTitle(String title) {
        String result = title;
        int dash = result.indexOf(" - ");
        if (dash > 0) {
            String tail = result.substring(dash + 3).toLowerCase(Locale.ROOT);
            if (tail.contains("remaster") || tail.contains("version") || tail.contains("edit")
                    || tail.contains("mix") || tail.contains("live")) {
                result = result.substring(0, dash);
            }
        }
        int paren = result.indexOf(" (feat");
        if (paren > 0) {
            result = result.substring(0, paren);
        }
        return result.trim();
    }

    private static String optString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
