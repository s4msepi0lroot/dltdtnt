package com.voidcanvas.spotifysync.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.voidcanvas.spotifysync.SpotifySync;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper around the Spotify Web API endpoints the mod needs.
 *
 * <p>Every method blocks and must be called from the polling executor, never
 * from the render or client thread.</p>
 */
public final class SpotifyApi {

    private static final String BASE = "https://api.spotify.com/v1";

    private final SpotifyAuth auth;

    public SpotifyApi(SpotifyAuth auth) {
        this.auth = auth;
    }

    // --------------------------------------------------------------- playback

    /**
     * Fetches the current playback state.
     *
     * @return the state, {@link PlaybackState#EMPTY} when nothing is playing,
     *         or {@code null} when the request failed.
     */
    public PlaybackState fetchPlayback() {
        HttpResponse<String> response = request("GET", "/me/player?additional_types=track,episode", null);
        if (response == null) {
            return null;
        }
        if (response.statusCode() == 204 || response.body() == null || response.body().isBlank()) {
            return PlaybackState.EMPTY;
        }
        if (response.statusCode() / 100 != 2) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            return parsePlayback(root);
        } catch (Exception e) {
            SpotifySync.LOGGER.warn("[Spotify Sync] could not parse playback payload", e);
            return null;
        }
    }

    private static PlaybackState parsePlayback(JsonObject root) {
        JsonObject item = optObject(root, "item");
        if (item == null) {
            return PlaybackState.EMPTY;
        }

        boolean episode = "episode".equals(optString(item, "type", "track"));
        String id = optString(item, "id", null);
        String title = optString(item, "name", "");
        long duration = optLong(item, "duration_ms", 0L);
        int popularity = (int) optLong(item, "popularity", -1L);

        List<String> artists = new ArrayList<>();
        String album = "";
        String cover = null;
        String releaseDate = "";

        if (episode) {
            JsonObject show = optObject(item, "show");
            if (show != null) {
                artists.add(optString(show, "publisher", optString(show, "name", "")));
                album = optString(show, "name", "");
            }
            cover = biggestImage(optArray(item, "images"));
            releaseDate = optString(item, "release_date", "");
        } else {
            JsonArray artistArray = optArray(item, "artists");
            if (artistArray != null) {
                for (JsonElement element : artistArray) {
                    if (element.isJsonObject()) {
                        String name = optString(element.getAsJsonObject(), "name", "");
                        if (!name.isEmpty()) {
                            artists.add(name);
                        }
                    }
                }
            }
            JsonObject albumObject = optObject(item, "album");
            if (albumObject != null) {
                album = optString(albumObject, "name", "");
                cover = biggestImage(optArray(albumObject, "images"));
                releaseDate = optString(albumObject, "release_date", "");
            }
        }

        String trackUrl = "";
        JsonObject urls = optObject(item, "external_urls");
        if (urls != null) {
            trackUrl = optString(urls, "spotify", "");
        }

        JsonObject device = optObject(root, "device");
        String deviceName = device == null ? "" : optString(device, "name", "");
        int volume = device == null ? -1 : (int) optLong(device, "volume_percent", -1L);

        return new PlaybackState(
                id,
                title,
                artists,
                album,
                duration,
                optLong(root, "progress_ms", 0L),
                optBoolean(root, "is_playing", false),
                cover,
                deviceName,
                volume,
                optBoolean(root, "shuffle_state", false),
                optString(root, "repeat_state", "off"),
                episode,
                releaseDate,
                trackUrl,
                popularity,
                System.nanoTime());
    }

    // --------------------------------------------------------------- controls

    public boolean play() {
        return isOk(request("PUT", "/me/player/play", "{}"));
    }

    public boolean pause() {
        return isOk(request("PUT", "/me/player/pause", ""));
    }

    public boolean next() {
        return isOk(request("POST", "/me/player/next", ""));
    }

    public boolean previous() {
        return isOk(request("POST", "/me/player/previous", ""));
    }

    public boolean seek(long positionMs) {
        return isOk(request("PUT", "/me/player/seek?position_ms=" + Math.max(0L, positionMs), ""));
    }

    public boolean volume(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        return isOk(request("PUT", "/me/player/volume?volume_percent=" + clamped, ""));
    }

    public boolean shuffle(boolean state) {
        return isOk(request("PUT", "/me/player/shuffle?state=" + state, ""));
    }

    /** {@code off}, {@code context} or {@code track}. */
    public boolean repeat(String state) {
        return isOk(request("PUT", "/me/player/repeat?state=" + state, ""));
    }

    // ---------------------------------------------------------------- plumbing

    private static boolean isOk(HttpResponse<String> response) {
        return response != null && response.statusCode() / 100 == 2;
    }

    private HttpResponse<String> request(String method, String path, String body) {
        String token = auth.accessTokenBlocking();
        if (token == null) {
            return null;
        }
        try {
            HttpClient http = auth.httpClient();
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json");
            HttpRequest request = switch (method) {
                case "GET" -> builder.GET().build();
                case "POST" -> builder.POST(publisher).build();
                case "PUT" -> builder.PUT(publisher).build();
                default -> builder.method(method, publisher).build();
            };
            HttpResponse<String> response = auth.httpClient() == null
                    ? null
                    : http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response != null && response.statusCode() == 401) {
                auth.markUnauthorized("Session expired, reconnect");
            } else if (response != null && response.statusCode() == 403) {
                auth.markUnauthorized("Spotify Premium required for remote control");
            } else if (response != null && response.statusCode() / 100 == 2) {
                auth.markConnected();
            }
            return response;
        } catch (Exception e) {
            SpotifySync.LOGGER.debug("[Spotify Sync] request failed: {} {}", method, path, e);
            return null;
        }
    }

    private static String biggestImage(JsonArray images) {
        if (images == null) {
            return null;
        }
        String best = null;
        long bestWidth = -1L;
        for (JsonElement element : images) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject image = element.getAsJsonObject();
            long width = optLong(image, "width", 0L);
            String url = optString(image, "url", null);
            if (url != null && width > bestWidth) {
                bestWidth = width;
                best = url;
            }
        }
        return best;
    }

    private static JsonObject optObject(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray optArray(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String optString(JsonObject parent, String key, String fallback) {
        JsonElement element = parent.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        return element.getAsString();
    }

    private static long optLong(JsonObject parent, String key, long fallback) {
        JsonElement element = parent.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean optBoolean(JsonObject parent, String key, boolean fallback) {
        JsonElement element = parent.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }
}
