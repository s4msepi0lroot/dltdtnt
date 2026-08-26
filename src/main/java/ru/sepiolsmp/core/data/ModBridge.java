package ru.sepiolsmp.core.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ru.sepiolsmp.core.SepiolCore;

/**
 * Bridge to the two hand written mods of the season.
 *
 * BadHabits stores its meters in &lt;level&gt;/badhabits/addiction.json and
 * BoozeCraft in &lt;level&gt;/boozecraft_players.json, both keyed by player uuid,
 * both plain Gson output. Reading the files keeps the plugin fully decoupled
 * from the NeoForge side, which is what makes this safe on a Youer hybrid: no
 * mod classes are touched, so the plugin also boots fine if a mod is removed.
 */
public final class ModBridge {

	private static final long TTL_MILLIS = 5_000L;

	private final SepiolCore plugin;
	private final Path levelRoot;

	private Map<UUID, JsonObject> badHabits = Map.of();
	private Map<UUID, JsonObject> booze = Map.of();
	private long badHabitsLoaded;
	private long boozeLoaded;

	public ModBridge(SepiolCore plugin, Path levelRoot) {
		this.plugin = plugin;
		this.levelRoot = levelRoot;
	}

	public synchronized JsonObject badHabitsOf(UUID id) {
		if (!plugin.getConfig().getBoolean("integration.badhabits.enabled", true)) {
			return null;
		}
		long now = System.currentTimeMillis();
		if (now - badHabitsLoaded > TTL_MILLIS) {
			badHabits = load(plugin.getConfig().getString("integration.badhabits.file", "badhabits/addiction.json"));
			badHabitsLoaded = now;
		}
		return badHabits.get(id);
	}

	public synchronized JsonObject boozeOf(UUID id) {
		if (!plugin.getConfig().getBoolean("integration.boozecraft.enabled", true)) {
			return null;
		}
		long now = System.currentTimeMillis();
		if (now - boozeLoaded > TTL_MILLIS) {
			booze = load(plugin.getConfig().getString("integration.boozecraft.file", "boozecraft_players.json"));
			boozeLoaded = now;
		}
		return booze.get(id);
	}

	public boolean badHabitsPresent() {
		return Files.isRegularFile(resolve(plugin.getConfig()
				.getString("integration.badhabits.file", "badhabits/addiction.json")));
	}

	public boolean boozePresent() {
		return Files.isRegularFile(resolve(plugin.getConfig()
				.getString("integration.boozecraft.file", "boozecraft_players.json")));
	}

	private Path resolve(String relative) {
		Path path = Path.of(relative);
		return path.isAbsolute() ? path : levelRoot.resolve(relative);
	}

	/** Reads a uuid keyed json map, tolerating one level of wrapping. */
	private Map<UUID, JsonObject> load(String relative) {
		Path file = resolve(relative);
		if (!Files.isRegularFile(file)) {
			return Map.of();
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!parsed.isJsonObject()) {
				return Map.of();
			}
			Map<UUID, JsonObject> direct = collect(parsed.getAsJsonObject());
			if (!direct.isEmpty()) {
				return direct;
			}
			for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
				if (entry.getValue().isJsonObject()) {
					Map<UUID, JsonObject> nested = collect(entry.getValue().getAsJsonObject());
					if (!nested.isEmpty()) {
						return nested;
					}
				}
			}
			return Map.of();
		} catch (Exception e) {
			plugin.getLogger().warning("Could not read " + file + ": " + e);
			return Map.of();
		}
	}

	private static Map<UUID, JsonObject> collect(JsonObject root) {
		Map<UUID, JsonObject> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			try {
				out.put(UUID.fromString(entry.getKey()), entry.getValue().getAsJsonObject());
			} catch (IllegalArgumentException ignored) {
				// key is not a uuid, so this object is a wrapper
			}
		}
		return out;
	}
}
