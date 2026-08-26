package ru.sepiolsmp.core.web;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ru.sepiolsmp.core.SepiolCore;
import ru.sepiolsmp.core.data.ProfileService;
import ru.sepiolsmp.core.data.SnapshotService;

/**
 * Tiny embedded HTTP server, the same trick Dynmap uses: the game server itself
 * listens on a dedicated port, so no external hosting is needed. Put nginx in
 * front of it if you want the domain and https.
 */
public final class WebServer {

	private static final Gson GSON = new Gson();
	private static final String AVATAR_PREFIX = "/avatar/";

	private final SepiolCore plugin;
	private final ProfileService profiles;
	private final SnapshotService snapshots;

	private HttpServer server;
	private ExecutorService pool;

	public WebServer(SepiolCore plugin, ProfileService profiles, SnapshotService snapshots) {
		this.plugin = plugin;
		this.profiles = profiles;
		this.snapshots = snapshots;
	}

	public void start() throws IOException {
		String bind = plugin.getConfig().getString("web.bind", "0.0.0.0");
		int port = plugin.getConfig().getInt("web.port", 8300);
		int threads = Math.max(1, plugin.getConfig().getInt("web.threads", 2));

		server = HttpServer.create(new InetSocketAddress(bind, port), 0);
		pool = Executors.newFixedThreadPool(threads, runnable -> {
			Thread thread = new Thread(runnable, "SepiolCore-web");
			thread.setDaemon(true);
			return thread;
		});
		server.setExecutor(pool);

		server.createContext("/api/status", exchange -> json(exchange, 200, profiles.status()));
		server.createContext("/api/players", exchange -> {
			JsonArray players = profiles.players();
			JsonObject wrapper = new JsonObject();
			wrapper.add("players", players);
			json(exchange, 200, wrapper);
		});
		server.createContext("/api/profile", exchange -> {
			String query = param(exchange.getRequestURI(), "player");
			JsonObject profile = profiles.profile(query);
			if (profile == null) {
				JsonObject error = new JsonObject();
				error.addProperty("error", "unknown_player");
				error.addProperty("query", query == null ? "" : query);
				json(exchange, 404, error);
				return;
			}
			json(exchange, 200, profile);
		});
		// Heads rendered by SepiolSkins (brick 2). No external avatar service needed.
		server.createContext("/avatar", this::serveAvatar);
		server.createContext("/", this::serveStatic);
		server.start();
	}

	public void stop() {
		if (server != null) {
			server.stop(0);
			server = null;
		}
		if (pool != null) {
			pool.shutdownNow();
			pool = null;
		}
	}

	// ------------------------------------------------------------- responses

	private void serveAvatar(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		String raw = path.length() > AVATAR_PREFIX.length() ? path.substring(AVATAR_PREFIX.length()) : "";
		raw = URLDecoder.decode(raw, StandardCharsets.UTF_8);
		if (raw.toLowerCase(Locale.ROOT).endsWith(".png")) {
			raw = raw.substring(0, raw.length() - 4);
		}
		String id = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.\\-]", "_");
		if (id.isBlank() || id.contains("..")) {
			send(exchange, 400, "text/plain; charset=utf-8", "bad request".getBytes(StandardCharsets.UTF_8));
			return;
		}
		Path dir = avatarDir();
		Path file = dir == null ? null : dir.resolve(id + ".png");
		if (file == null || !Files.isRegularFile(file)) {
			// The page falls back to a letter avatar on 404, so this is not an error.
			send(exchange, 404, "text/plain; charset=utf-8", "no avatar".getBytes(StandardCharsets.UTF_8));
			return;
		}
		send(exchange, 200, "image/png", Files.readAllBytes(file), 600);
	}

	/** Where SepiolSkins keeps rendered heads. "auto" = plugins/SepiolSkins/web/heads. */
	private Path avatarDir() {
		String configured = plugin.getConfig().getString("web.avatar-dir", "auto");
		if (configured != null && !configured.isBlank() && !configured.equalsIgnoreCase("auto")) {
			return Paths.get(configured);
		}
		File plugins = plugin.getDataFolder().getParentFile();
		if (plugins == null) {
			return null;
		}
		return plugins.toPath().resolve("SepiolSkins").resolve("web").resolve("heads");
	}

	private void serveStatic(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		if (path == null || path.equals("/") || path.isBlank()) {
			path = "/index.html";
		}
		if (path.contains("..")) {
			send(exchange, 400, "text/plain; charset=utf-8", "bad request".getBytes(StandardCharsets.UTF_8));
			return;
		}
		byte[] body = resource("web" + path);
		if (body == null) {
			send(exchange, 404, "text/plain; charset=utf-8", "404".getBytes(StandardCharsets.UTF_8));
			return;
		}
		send(exchange, 200, mime(path), body);
	}

	private byte[] resource(String name) {
		try (InputStream stream = plugin.getResource(name)) {
			return stream == null ? null : stream.readAllBytes();
		} catch (IOException e) {
			return null;
		}
	}

	private void json(HttpExchange exchange, int code, JsonElement body) throws IOException {
		send(exchange, code, "application/json; charset=utf-8",
				GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
	}

	private void send(HttpExchange exchange, int code, String contentType, byte[] body) throws IOException {
		send(exchange, code, contentType, body, 0);
	}

	private void send(HttpExchange exchange, int code, String contentType, byte[] body, int cacheSeconds)
			throws IOException {
		String cors = plugin.getConfig().getString("web.cors-allow-origin", "*");
		exchange.getResponseHeaders().add("Content-Type", contentType);
		exchange.getResponseHeaders().add("Cache-Control",
				cacheSeconds > 0 ? "public, max-age=" + cacheSeconds : "no-store");
		if (cors != null && !cors.isBlank()) {
			exchange.getResponseHeaders().add("Access-Control-Allow-Origin", cors);
		}
		exchange.sendResponseHeaders(code, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	private static String mime(String path) {
		if (path.endsWith(".html")) {
			return "text/html; charset=utf-8";
		}
		if (path.endsWith(".css")) {
			return "text/css; charset=utf-8";
		}
		if (path.endsWith(".js")) {
			return "application/javascript; charset=utf-8";
		}
		if (path.endsWith(".svg")) {
			return "image/svg+xml";
		}
		if (path.endsWith(".png")) {
			return "image/png";
		}
		return "application/octet-stream";
	}

	private static String param(URI uri, String key) {
		String raw = uri.getRawQuery();
		if (raw == null || raw.isBlank()) {
			return null;
		}
		Map<String, String> values = new HashMap<>();
		for (String pair : raw.split("&")) {
			int eq = pair.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			values.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
					URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
		}
		return values.get(key);
	}
}
