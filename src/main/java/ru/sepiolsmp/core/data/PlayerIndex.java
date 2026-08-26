package ru.sepiolsmp.core.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ru.sepiolsmp.core.SepiolCore;

/**
 * Nickname to uuid index, built from usercache.json plus whatever uuids exist in
 * world/stats. Works for offline mode / AuthMe setups because it never asks
 * Mojang for anything.
 */
public final class PlayerIndex {

	private static final long TTL_MILLIS = 30_000L;

	private final SepiolCore plugin;
	private final Path levelRoot;

	private Map<UUID, String> names = Map.of();
	private Map<String, UUID> lookup = Map.of();
	private long loadedAt;

	public PlayerIndex(SepiolCore plugin, Path levelRoot) {
		this.plugin = plugin;
		this.levelRoot = levelRoot;
	}

	public synchronized UUID byName(String name) {
		refresh();
		return lookup.get(name.toLowerCase(Locale.ROOT));
	}

	public synchronized String nameOf(UUID id) {
		refresh();
		return names.getOrDefault(id, id.toString().substring(0, 8));
	}

	public synchronized List<UUID> known() {
		refresh();
		return new ArrayList<>(names.keySet());
	}

	private void refresh() {
		long now = System.currentTimeMillis();
		if (now - loadedAt <= TTL_MILLIS && !names.isEmpty()) {
			return;
		}
		loadedAt = now;

		Map<UUID, String> collected = new HashMap<>();
		readUserCache(collected);
		readStatsDir(collected);

		Map<String, UUID> reverse = new HashMap<>();
		collected.forEach((id, name) -> reverse.put(name.toLowerCase(Locale.ROOT), id));

		names = collected;
		lookup = reverse;
	}

	private void readUserCache(Map<UUID, String> out) {
		Path cache = plugin.serverRoot().resolve("usercache.json");
		if (!Files.isRegularFile(cache)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(cache, StandardCharsets.UTF_8));
			if (!parsed.isJsonArray()) {
				return;
			}
			JsonArray array = parsed.getAsJsonArray();
			for (JsonElement element : array) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject entry = element.getAsJsonObject();
				if (!entry.has("uuid") || !entry.has("name")) {
					continue;
				}
				try {
					out.put(UUID.fromString(entry.get("uuid").getAsString()), entry.get("name").getAsString());
				} catch (IllegalArgumentException ignored) {
					// broken entry
				}
			}
		} catch (Exception e) {
			plugin.getLogger().warning("Could not read usercache.json: " + e);
		}
	}

	private void readStatsDir(Map<UUID, String> out) {
		Path stats = levelRoot.resolve("stats");
		if (!Files.isDirectory(stats)) {
			return;
		}
		try (Stream<Path> files = Files.list(stats)) {
			files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
				String raw = path.getFileName().toString();
				try {
					UUID id = UUID.fromString(raw.substring(0, raw.length() - 5));
					out.putIfAbsent(id, id.toString().substring(0, 8));
				} catch (IllegalArgumentException ignored) {
					// not a uuid file
				}
			});
		} catch (Exception e) {
			plugin.getLogger().warning("Could not list " + stats + ": " + e);
		}
	}
}
