package dev.riftspotify.client.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.riftspotify.client.config.RiftConfig;

import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SpotifyClient {
    public static final String REDIRECT_URI = "http://127.0.0.1:8765/callback";
    public static final String SCOPES = "user-read-currently-playing user-read-playback-state user-modify-playback-state";
    private static final SpotifySession SESSION = new SpotifySession();
    private static final SpotifyApi API = new SpotifyApi();
    private static final LyricsClient LYRICS = new LyricsClient();
    private static final ExecutorService IO = Executors.newVirtualThreadPerTaskExecutor();
    private static SpotifyTrack track;
    private static Lyrics lyrics = Lyrics.empty();
    private static long lastPoll;
    private static String lastLyricsTrackId = "";
    private static CompletableFuture<String> loginFuture;

    private SpotifyClient() {}
    public static void initialize() { /* Lazy state is intentional: no network before the user connects. */ }
    public static boolean connected() { return SESSION.connected(); }
    public static SpotifyTrack track() { return track; }
    public static Lyrics lyrics() { return lyrics; }
    public static long position() {
        if (track == null) return 0;
        long elapsed = track.playing() ? System.currentTimeMillis() - lastPoll : 0;
        return Math.min(track.durationMs(), track.progressMs() + elapsed);
    }

    public static void tick() {
        if (!connected() || System.currentTimeMillis() - lastPoll < RiftConfig.get().refreshSeconds * 1000L) return;
        lastPoll = System.currentTimeMillis();
        IO.submit(() -> {
            if (SESSION.expiresSoon() && !refresh()) return;
            Optional<SpotifyTrack> now = API.current(SESSION.accessToken());
            track = now.orElse(null);
            if (track != null) CoverArtCache.request(track);
            if (track != null && RiftConfig.get().autoFetchLyrics && !track.id().equals(lastLyricsTrackId)) {
                lastLyricsTrackId = track.id();
                IO.submit(() -> lyrics = LYRICS.fetch(track));
            }
        });
    }

    public static void playPause() { if (track != null) API.control(SESSION.accessToken(), track.playing() ? "/me/player/pause" : "/me/player/play", "PUT"); }
    public static void next() { API.control(SESSION.accessToken(), "/me/player/next", "POST"); }
    public static void previous() { API.control(SESSION.accessToken(), "/me/player/previous", "POST"); }
    public static void disconnect() { SESSION.clear(); track = null; lyrics = Lyrics.empty(); RiftConfig.get().save(); }

    public static void login(String clientId) {
        if (clientId == null || clientId.isBlank()) return;
        RiftConfig.get().clientId = clientId.trim(); RiftConfig.get().save();
        if (loginFuture != null && !loginFuture.isDone()) return;
        loginFuture = new CompletableFuture<>();
        IO.submit(() -> {
            try {
                String verifier = randomString(64);
                String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8)));
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8765), 0);
                server.createContext("/callback", exchange -> {
                    String query = exchange.getRequestURI().getRawQuery();
                    String code = query == null ? "" : parameter(query, "code");
                    String body = "<html><body style='background:#000;color:#fff;font:16px sans-serif;padding:40px'>Rift Spotify connected. You can return to Minecraft.</body></html>";
                    exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
                    try (OutputStream output = exchange.getResponseBody()) { output.write(body.getBytes(StandardCharsets.UTF_8)); }
                    loginFuture.complete(code);
                });
                server.start();
                String auth = "https://accounts.spotify.com/authorize?" + SpotifyApi.form("client_id", clientId, "response_type", "code", "redirect_uri", REDIRECT_URI, "scope", SCOPES, "code_challenge_method", "S256", "code_challenge", challenge);
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(auth));
                String code = loginFuture.get();
                server.stop(0);
                exchangeCode(clientId, code, verifier);
            } catch (Exception ignored) { if (loginFuture != null) loginFuture.completeExceptionally(ignored); }
        });
    }

    private static void exchangeCode(String clientId, String code, String verifier) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(SpotifyApi.form("client_id", clientId, "grant_type", "authorization_code", "code", code, "redirect_uri", REDIRECT_URI, "code_verifier", verifier))).build();
        JsonObject json = JsonParser.parseString(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
        if (json.has("access_token")) SESSION.set(json.get("access_token").getAsString(), json.has("refresh_token") ? json.get("refresh_token").getAsString() : null, json.get("expires_in").getAsLong());
    }

    private static boolean refresh() {
        try {
            if (SESSION.refreshToken() == null) return false;
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(SpotifyApi.form("client_id", RiftConfig.get().clientId, "grant_type", "refresh_token", "refresh_token", SESSION.refreshToken()))).build();
            JsonObject json = JsonParser.parseString(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
            if (!json.has("access_token")) return false;
            SESSION.set(json.get("access_token").getAsString(), null, json.get("expires_in").getAsLong()); return true;
        } catch (Exception ignored) { return false; }
    }

    private static String parameter(String query, String key) {
        for (String part : query.split("&")) if (part.startsWith(key + "=")) return java.net.URLDecoder.decode(part.substring(key.length() + 1), StandardCharsets.UTF_8);
        return "";
    }
    private static String randomString(int size) { byte[] bytes = new byte[size]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
