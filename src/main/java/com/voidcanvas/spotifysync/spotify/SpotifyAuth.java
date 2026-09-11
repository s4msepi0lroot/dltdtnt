package com.voidcanvas.spotifysync.spotify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.voidcanvas.spotifysync.SpotifySync;
import com.voidcanvas.spotifysync.config.SyncConfig;
import net.minecraft.Util;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Spotify authorisation using the Authorization Code + PKCE flow.
 *
 * <p>PKCE means no client secret has to be shipped or stored: the user creates
 * a free Spotify app, pastes the client id into the settings screen, and the
 * mod spins up a tiny loopback HTTP server to catch the redirect.</p>
 */
public final class SpotifyAuth {

    public enum Status {
        DISCONNECTED,
        WAITING_FOR_BROWSER,
        CONNECTED,
        ERROR
    }

    public static final String SCOPES = "user-read-playback-state user-modify-playback-state "
            + "user-read-currently-playing user-read-recently-played";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token";
    private static final String AUTHORIZE_ENDPOINT = "https://accounts.spotify.com/authorize";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private volatile String accessToken;
    private volatile String refreshToken;
    private volatile long expiresAtEpochMs;
    private volatile Status status = Status.DISCONNECTED;
    private volatile String message = "";

    private HttpServer callbackServer;
    private String pendingVerifier;
    private String pendingState;

    public SpotifyAuth() {
        readStore();
    }

    // ------------------------------------------------------------------ state

    public Status status() {
        return status;
    }

    public String message() {
        return message;
    }

    public boolean isAuthorized() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    public HttpClient httpClient() {
        return http;
    }

    /** Returns a usable access token, refreshing it when required. */
    public String accessTokenBlocking() {
        if (!isAuthorized()) {
            return null;
        }
        if (accessToken != null && System.currentTimeMillis() < expiresAtEpochMs - 30_000L) {
            return accessToken;
        }
        synchronized (this) {
            if (accessToken != null && System.currentTimeMillis() < expiresAtEpochMs - 30_000L) {
                return accessToken;
            }
            return refresh();
        }
    }

    public void logout() {
        accessToken = null;
        refreshToken = null;
        expiresAtEpochMs = 0L;
        status = Status.DISCONNECTED;
        message = "";
        stopCallbackServer();
        try {
            Files.deleteIfExists(storePath());
        } catch (IOException e) {
            SpotifySync.LOGGER.warn("[Spotify Sync] could not delete token store", e);
        }
    }

    // ------------------------------------------------------------- login flow

    /** Opens the system browser and waits for the OAuth redirect. */
    public synchronized void beginLogin() {
        SyncConfig config = SyncConfig.get();
        String clientId = config.clientId == null ? "" : config.clientId.trim();
        if (clientId.isEmpty()) {
            status = Status.ERROR;
            message = "Client ID is empty";
            return;
        }

        stopCallbackServer();

        SecureRandom random = new SecureRandom();
        byte[] verifierBytes = new byte[64];
        random.nextBytes(verifierBytes);
        pendingVerifier = base64Url(verifierBytes);
        byte[] stateBytes = new byte[16];
        random.nextBytes(stateBytes);
        pendingState = base64Url(stateBytes);

        String challenge;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            challenge = base64Url(digest.digest(pendingVerifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            status = Status.ERROR;
            message = "SHA-256 unavailable: " + e.getMessage();
            return;
        }

        String redirectUri = "http://127.0.0.1:" + config.callbackPort + "/callback";

        try {
            callbackServer = HttpServer.create(new InetSocketAddress("127.0.0.1", config.callbackPort), 0);
            callbackServer.setExecutor(Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "SpotifySync OAuth callback");
                thread.setDaemon(true);
                return thread;
            }));
            callbackServer.createContext("/callback", this::handleCallback);
            callbackServer.start();
        } catch (IOException e) {
            status = Status.ERROR;
            message = "Port " + config.callbackPort + " busy: " + e.getMessage();
            return;
        }

        String url = AUTHORIZE_ENDPOINT
                + "?client_id=" + enc(clientId)
                + "&response_type=code"
                + "&redirect_uri=" + enc(redirectUri)
                + "&code_challenge_method=S256"
                + "&code_challenge=" + enc(challenge)
                + "&state=" + enc(pendingState)
                + "&scope=" + enc(SCOPES);

        status = Status.WAITING_FOR_BROWSER;
        message = "Waiting for the browser\u2026";
        try {
            Util.getPlatform().openUri(URI.create(url));
        } catch (Exception e) {
            message = "Open this URL manually: " + url;
            SpotifySync.LOGGER.info("[Spotify Sync] authorize URL: {}", url);
        }
    }

    private void handleCallback(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String code = query.get("code");
        String state = query.get("state");
        String error = query.get("error");

        String heading;
        String body;
        if (error != null) {
            status = Status.ERROR;
            message = "Spotify returned: " + error;
            heading = "Authorisation denied";
            body = error;
        } else if (code == null || state == null || pendingState == null || !pendingState.equals(state)) {
            status = Status.ERROR;
            message = "Invalid OAuth callback";
            heading = "Invalid callback";
            body = "State mismatch \u2014 please try again from Minecraft.";
        } else {
            boolean ok = exchangeCode(code);
            heading = ok ? "Connected" : "Token exchange failed";
            body = ok ? "You can go back to Minecraft now." : message;
        }

        byte[] page = renderHtml(heading, body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, page.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(page);
        }
        // The server is single-use.
        stopCallbackServerLater();
    }

    private boolean exchangeCode(String code) {
        SyncConfig config = SyncConfig.get();
        String redirectUri = "http://127.0.0.1:" + config.callbackPort + "/callback";
        Map<String, String> form = new HashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("client_id", config.clientId.trim());
        form.put("code_verifier", pendingVerifier == null ? "" : pendingVerifier);
        return postToken(form);
    }

    private String refresh() {
        SyncConfig config = SyncConfig.get();
        if (config.clientId == null || config.clientId.isBlank() || refreshToken == null) {
            return null;
        }
        Map<String, String> form = new HashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("client_id", config.clientId.trim());
        return postToken(form) ? accessToken : null;
    }

    private boolean postToken(Map<String, String> form) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_ENDPOINT))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                status = Status.ERROR;
                message = "Token request failed (" + response.statusCode() + ")";
                SpotifySync.LOGGER.warn("[Spotify Sync] token request failed: {} {}", response.statusCode(), response.body());
                return false;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (json.has("access_token")) {
                accessToken = json.get("access_token").getAsString();
            }
            if (json.has("refresh_token")) {
                refreshToken = json.get("refresh_token").getAsString();
            }
            long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600L;
            expiresAtEpochMs = System.currentTimeMillis() + expiresIn * 1000L;
            status = Status.CONNECTED;
            message = "Connected";
            writeStore();
            return true;
        } catch (Exception e) {
            status = Status.ERROR;
            message = "Token request error: " + e.getMessage();
            SpotifySync.LOGGER.warn("[Spotify Sync] token request error", e);
            return false;
        }
    }

    /** Called by the API client when Spotify rejects the token. */
    public void markUnauthorized(String reason) {
        accessToken = null;
        expiresAtEpochMs = 0L;
        if (!isAuthorized()) {
            status = Status.DISCONNECTED;
        } else {
            status = Status.ERROR;
        }
        message = reason;
    }

    public void markConnected() {
        status = Status.CONNECTED;
        message = "Connected";
    }

    // ----------------------------------------------------------------- server

    private void stopCallbackServerLater() {
        HttpServer server = callbackServer;
        callbackServer = null;
        if (server != null) {
            Thread stopper = new Thread(() -> server.stop(1), "SpotifySync OAuth shutdown");
            stopper.setDaemon(true);
            stopper.start();
        }
    }

    public void stopCallbackServer() {
        HttpServer server = callbackServer;
        callbackServer = null;
        if (server != null) {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------ token store

    private static Path storePath() {
        return FMLPaths.CONFIGDIR.get().resolve("spotifysync-auth.json");
    }

    private void readStore() {
        Path path = storePath();
        if (!Files.exists(path)) {
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.has("refresh_token")) {
                refreshToken = json.get("refresh_token").getAsString();
            }
            if (refreshToken != null && !refreshToken.isBlank()) {
                message = "Saved session found";
            }
        } catch (Exception e) {
            SpotifySync.LOGGER.warn("[Spotify Sync] could not read token store", e);
        }
    }

    private void writeStore() {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            JsonObject json = new JsonObject();
            json.addProperty("refresh_token", refreshToken);
            Path path = storePath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SpotifySync.LOGGER.warn("[Spotify Sync] could not write token store", e);
        }
    }

    // ----------------------------------------------------------------- helpers

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String formEncode(Map<String, String> form) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(enc(entry.getKey())).append('=').append(enc(entry.getValue()));
        }
        return builder.toString();
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    /** Void-canvas styled confirmation page shown in the browser. */
    private static String renderHtml(String heading, String body) {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<title>Spotify Sync</title><style>"
                + "*{box-sizing:border-box}"
                + "body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;"
                + "background:#000;color:#fff;font-family:Inter,system-ui,-apple-system,sans-serif}"
                + ".card{width:min(520px,90vw);background:#111;border:1px solid rgba(0,153,255,.45);"
                + "border-radius:15px;padding:40px}"
                + ".rule{height:1px;background:#0099ff;opacity:.7;margin:0 0 24px 0;width:56px}"
                + "h1{font-size:34px;line-height:1.05;letter-spacing:-1.4px;font-weight:500;margin:0 0 12px}"
                + "p{font-size:14px;line-height:1.5;color:#999;margin:0}"
                + "code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;color:#00bb88;font-size:13px}"
                + ".foot{margin-top:28px;font-size:12px;color:#666;font-family:ui-monospace,monospace}"
                + "</style></head><body><div class=\"card\"><div class=\"rule\"></div>"
                + "<h1>" + escape(heading) + "</h1><p>" + escape(body) + "</p>"
                + "<div class=\"foot\">spotify sync \u2014 <code>minecraft 1.21.1 / neoforge</code></div>"
                + "</div></body></html>";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
