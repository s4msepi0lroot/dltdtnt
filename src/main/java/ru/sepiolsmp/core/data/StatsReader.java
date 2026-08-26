package ru.sepiolsmp.core.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reads vanilla statistics straight from world/stats/&lt;uuid&gt;.json.
 *
 * Bukkit only exposes statistics for online players, and the season profile has
 * to work for everyone, so the files are the source of truth here.
 */
public final class StatsReader {

	public record Stats(long playTicks, long deaths, long mobKills, long playerKills,
			long blocksMined, long blocksPlaced, long walkedCm, long timeSinceDeath) {

		public static Stats empty() {
			return new Stats(0, 0, 0, 0, 0, 0, 0, 0);
		}
	}

	private final Path statsDir;

	public StatsReader(Path statsDir) {
		this.statsDir = statsDir;
	}

	public boolean available() {
		return Files.isDirectory(statsDir);
	}

	public Path dir() {
		return statsDir;
	}

	public Stats read(UUID id) {
		Path file = statsDir.resolve(id.toString() + ".json");
		if (!Files.isRegularFile(file)) {
			return Stats.empty();
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!parsed.isJsonObject()) {
				return Stats.empty();
			}
			JsonObject stats = optObject(parsed.getAsJsonObject(), "stats");
			if (stats == null) {
				return Stats.empty();
			}
			JsonObject custom = optObject(stats, "minecraft:custom");
			long play = longOf(custom, "minecraft:play_time", longOf(custom, "minecraft:play_one_minute", 0));
			return new Stats(
					play,
					longOf(custom, "minecraft:deaths", 0),
					longOf(custom, "minecraft:mob_kills", 0),
					longOf(custom, "minecraft:player_kills", 0),
					sum(optObject(stats, "minecraft:mined")),
					sum(optObject(stats, "minecraft:used")),
					longOf(custom, "minecraft:walk_one_cm", 0),
					longOf(custom, "minecraft:time_since_death", 0));
		} catch (Exception e) {
			return Stats.empty();
		}
	}

	private static JsonObject optObject(JsonObject parent, String key) {
		if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) {
			return null;
		}
		return parent.getAsJsonObject(key);
	}

	private static long longOf(JsonObject object, String key, long fallback) {
		if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
			return fallback;
		}
		try {
			return object.get(key).getAsLong();
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static long sum(JsonObject object) {
		if (object == null) {
			return 0L;
		}
		long total = 0L;
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (entry.getValue().isJsonPrimitive()) {
				try {
					total += entry.getValue().getAsLong();
				} catch (NumberFormatException ignored) {
					// not a counter, skip
				}
			}
		}
		return total;
	}
}
