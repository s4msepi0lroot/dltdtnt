package dev.riftspotify.client.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

final class LyricsClient {
    private final HttpClient http = HttpClient.newHttpClient();

    Lyrics fetch(SpotifyTrack track) {
        try {
            String url = "https://lrclib.net/api/get?track_name=" + enc(track.name()) + "&artist_name=" + enc(track.artist())
                    + "&album_name=" + enc(track.album()) + "&duration=" + Math.round(track.durationMs() / 1000d);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "RiftSpotify/1.0").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Lyrics.empty();
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String synced = json.has("syncedLyrics") && !json.get("syncedLyrics").isJsonNull() ? json.get("syncedLyrics").getAsString() : "";
            String plain = json.has("plainLyrics") && !json.get("plainLyrics").isJsonNull() ? json.get("plainLyrics").getAsString() : "";
            return new Lyrics(LrcLine.parse(synced), plain, "LRCLIB");
        } catch (Exception ignored) { return Lyrics.empty(); }
    }

    private static String enc(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
}
