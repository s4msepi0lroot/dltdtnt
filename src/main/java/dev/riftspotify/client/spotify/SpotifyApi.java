package dev.riftspotify.client.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

final class SpotifyApi {
    private static final String API = "https://api.spotify.com/v1";
    private final HttpClient http = HttpClient.newHttpClient();

    Optional<SpotifyTrack> current(String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API + "/me/player"))
                    .header("Authorization", "Bearer " + token).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204 || response.body().isBlank()) return Optional.empty();
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!root.has("item") || root.get("item").isJsonNull()) return Optional.empty();
            JsonObject item = root.getAsJsonObject("item");
            String artist = item.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
            String cover = item.getAsJsonObject("album").getAsJsonArray("images").isEmpty() ? "" :
                    item.getAsJsonObject("album").getAsJsonArray("images").get(0).getAsJsonObject().get("url").getAsString();
            return Optional.of(new SpotifyTrack(item.get("id").getAsString(), item.get("name").getAsString(), artist,
                    item.getAsJsonObject("album").get("name").getAsString(), cover,
                    root.has("progress_ms") ? root.get("progress_ms").getAsLong() : 0,
                    item.get("duration_ms").getAsLong(), root.has("is_playing") && root.get("is_playing").getAsBoolean()));
        } catch (Exception ignored) { return Optional.empty(); }
    }

    boolean control(String token, String endpoint, String method) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API + endpoint))
                    .header("Authorization", "Bearer " + token).method(method, HttpRequest.BodyPublishers.noBody()).build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() < 300;
        } catch (Exception ignored) { return false; }
    }

    static String form(String... pairs) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (out.length() > 0) out.append('&');
            out.append(URLEncoder.encode(pairs[i], StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
        }
        return out.toString();
    }
}
