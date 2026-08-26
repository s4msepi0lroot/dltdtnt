package ru.sepiolsmp.core.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ru.sepiolsmp.core.SepiolCore;
import ru.sepiolsmp.core.data.SnapshotService.Online;
import ru.sepiolsmp.core.data.SnapshotService.Snapshot;

/** Builds the json the web page consumes. Runs on http threads, never touches Bukkit. */
public final class ProfileService {

	private record Cached(JsonObject json, long builtAt) {
	}

	private final SepiolCore plugin;
	private final SnapshotService snapshots;
	private final StatsReader stats;
	private final ModBridge mods;
	private final PlayerIndex index;

	private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();
	private volatile JsonArray leaderboard = new JsonArray();
	private volatile long leaderboardAt;

	public ProfileService(SepiolCore plugin, SnapshotService snapshots, StatsReader stats,
			ModBridge mods, PlayerIndex index) {
		this.plugin = plugin;
		this.snapshots = snapshots;
		this.stats = stats;
		this.mods = mods;
		this.index = index;
	}

	// ---------------------------------------------------------------- status

	public JsonObject status() {
		Snapshot snapshot = snapshots.snapshot();
		boolean coords = plugin.getConfig().getBoolean("web.expose-coordinates", false);

		JsonObject out = new JsonObject();
		JsonObject season = new JsonObject();
		season.addProperty("name", plugin.getConfig().getString("season.name", "Season II"));
		season.addProperty("start", plugin.getConfig().getString("season.start", ""));
		season.addProperty("end", plugin.getConfig().getString("season.end", ""));
		season.addProperty("border", plugin.getConfig().getInt("season.world-border", 12000));
		season.addProperty("day", seasonDay());
		out.add("season", season);

		out.addProperty("online", snapshot.online());
		out.addProperty("max", snapshot.max());
		out.addProperty("tps", round(snapshot.tps(), 2));
		out.addProperty("updated", snapshot.takenAt());
		out.addProperty("badhabits", mods.badHabitsPresent());
		out.addProperty("boozecraft", mods.boozePresent());

		JsonArray players = new JsonArray();
		for (Online player : snapshot.players()) {
			JsonObject entry = new JsonObject();
			entry.addProperty("name", player.name());
			entry.addProperty("uuid", player.id().toString());
			entry.addProperty("hours", round(player.playTicks() / 72000.0D, 1));
			entry.addProperty("world", player.world());
			if (coords) {
				entry.addProperty("x", player.x());
				entry.addProperty("y", player.y());
				entry.addProperty("z", player.z());
			}
			players.add(entry);
		}
		out.add("players", players);
		return out;
	}

	// ----------------------------------------------------------- leaderboard

	public JsonArray players() {
		long ttl = 60_000L;
		if (System.currentTimeMillis() - leaderboardAt < ttl && leaderboard.size() > 0) {
			return leaderboard;
		}
		int limit = Math.max(1, plugin.getConfig().getInt("leaderboards.size", 25));
		Snapshot snapshot = snapshots.snapshot();

		List<JsonObject> rows = new ArrayList<>();
		for (UUID id : index.known()) {
			StatsReader.Stats data = stats.read(id);
			Online online = snapshot.find(id);
			long ticks = online != null ? Math.max(online.playTicks(), data.playTicks()) : data.playTicks();
			if (ticks <= 0 && online == null) {
				continue;
			}
			JsonObject row = new JsonObject();
			row.addProperty("name", online != null ? online.name() : index.nameOf(id));
			row.addProperty("uuid", id.toString());
			row.addProperty("online", online != null);
			row.addProperty("hours", round(ticks / 72000.0D, 1));
			row.addProperty("deaths", online != null ? online.deaths() : data.deaths());
			row.addProperty("mobKills", online != null ? online.mobKills() : data.mobKills());
			row.addProperty("playerKills", online != null ? online.playerKills() : data.playerKills());
			rows.add(row);
		}
		rows.sort(Comparator.comparingDouble((JsonObject row) -> row.get("hours").getAsDouble()).reversed());

		JsonArray out = new JsonArray();
		for (int i = 0; i < Math.min(limit, rows.size()); i++) {
			out.add(rows.get(i));
		}
		leaderboard = out;
		leaderboardAt = System.currentTimeMillis();
		return out;
	}

	// --------------------------------------------------------------- profile

	public JsonObject profile(String query) {
		UUID id = resolve(query);
		if (id == null) {
			return null;
		}
		long ttl = Math.max(1, plugin.getConfig().getInt("web.cache-seconds", 15)) * 1000L;
		Cached cached = cache.get(id);
		if (cached != null && System.currentTimeMillis() - cached.builtAt() < ttl) {
			return cached.json();
		}
		JsonObject built = build(id);
		cache.put(id, new Cached(built, System.currentTimeMillis()));
		return built;
	}

	private UUID resolve(String query) {
		if (query == null || query.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(query);
		} catch (IllegalArgumentException ignored) {
			// fall through to name lookup
		}
		for (Online player : snapshots.snapshot().players()) {
			if (player.name().equalsIgnoreCase(query)) {
				return player.id();
			}
		}
		return index.byName(query);
	}

	private JsonObject build(UUID id) {
		StatsReader.Stats data = stats.read(id);
		Online online = snapshots.snapshot().find(id);

		JsonObject out = new JsonObject();
		out.addProperty("uuid", id.toString());
		out.addProperty("name", online != null ? online.name() : index.nameOf(id));
		out.addProperty("online", online != null);
		out.addProperty("lastSeen", lastSeen(id));
		if (online != null) {
			out.addProperty("world", online.world());
			out.addProperty("health", round(online.health(), 1));
			out.addProperty("xpLevel", online.xpLevel());
		}

		long ticks = online != null ? Math.max(online.playTicks(), data.playTicks()) : data.playTicks();
		JsonObject vanilla = new JsonObject();
		vanilla.addProperty("hours", round(ticks / 72000.0D, 1));
		vanilla.addProperty("deaths", online != null ? online.deaths() : data.deaths());
		vanilla.addProperty("mobKills", online != null ? online.mobKills() : data.mobKills());
		vanilla.addProperty("playerKills", online != null ? online.playerKills() : data.playerKills());
		vanilla.addProperty("blocksMined", data.blocksMined());
		vanilla.addProperty("kmWalked", round(data.walkedCm() / 100000.0D, 2));
		vanilla.addProperty("hoursSinceDeath", round(data.timeSinceDeath() / 72000.0D, 1));
		out.add("vanilla", vanilla);

		out.add("badhabits", badHabits(id));
		out.add("boozecraft", booze(id));
		return out;
	}

	/** BadHabits meters: two substances, each with addiction / dose / withdrawal stage. */
	private JsonObject badHabits(UUID id) {
		JsonObject source = mods.badHabitsOf(id);
		if (source == null) {
			return null;
		}
		double max = plugin.getConfig().getDouble("integration.badhabits.addiction-max", 100.0D);
		JsonObject out = new JsonObject();
		out.add("nicotine", meter(source, "nicotine", max));
		out.add("narcotic", meter(source, "narcotic", max));
		return out;
	}

	private static JsonObject meter(JsonObject source, String key, double max) {
		JsonObject meter = source.has(key) && source.get(key).isJsonObject()
				? source.getAsJsonObject(key)
				: new JsonObject();
		double addiction = number(meter, "addiction");
		JsonObject out = new JsonObject();
		out.addProperty("addiction", round(addiction, 1));
		out.addProperty("percent", round(max <= 0 ? 0 : Math.min(100.0D, addiction / max * 100.0D), 1));
		out.addProperty("dose", round(number(meter, "dose"), 1));
		out.addProperty("stage", (int) number(meter, "stage"));
		out.addProperty("withdrawalSeconds", (long) number(meter, "withdrawalSeconds"));
		return out;
	}

	/** BoozeCraft state: alcohol in the blood, addiction, caffeine, drink counters. */
	private JsonObject booze(UUID id) {
		JsonObject source = mods.boozeOf(id);
		if (source == null) {
			return null;
		}
		double cap = plugin.getConfig().getDouble("integration.boozecraft.alcohol-cap", 100.0D);
		double addictionMax = plugin.getConfig().getDouble("integration.boozecraft.addiction-max", 100.0D);
		double alcohol = number(source, "alcohol");
		double addiction = number(source, "addiction");

		JsonObject out = new JsonObject();
		out.addProperty("alcohol", round(alcohol, 1));
		out.addProperty("alcoholPercent", round(cap <= 0 ? 0 : Math.min(100.0D, alcohol / cap * 100.0D), 1));
		out.addProperty("addiction", round(addiction, 1));
		out.addProperty("addictionPercent",
				round(addictionMax <= 0 ? 0 : Math.min(100.0D, addiction / addictionMax * 100.0D), 1));
		out.addProperty("caffeine", round(number(source, "caffeine"), 1));
		out.addProperty("drinksTotal", (long) number(source, "drinksTotal"));
		out.addProperty("drinksAlcohol", (long) number(source, "drinksAlcohol"));
		out.addProperty("passOuts", (long) number(source, "passOuts"));
		out.addProperty("hangoverUntil", (long) number(source, "hangoverUntil"));
		return out;
	}

	// ----------------------------------------------------------------- utils

	private long lastSeen(UUID id) {
		Path file = stats.dir().resolve(id + ".json");
		try {
			return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : 0L;
		} catch (Exception e) {
			return 0L;
		}
	}

	private int seasonDay() {
		String start = plugin.getConfig().getString("season.start", "");
		if (start == null || start.isBlank()) {
			return 0;
		}
		try {
			long days = ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.now());
			return (int) Math.max(0, days) + 1;
		} catch (Exception e) {
			return 0;
		}
	}

	private static double number(JsonObject object, String key) {
		if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
			return 0.0D;
		}
		try {
			return object.get(key).getAsDouble();
		} catch (NumberFormatException e) {
			return 0.0D;
		}
	}

	private static double round(double value, int digits) {
		double factor = Math.pow(10, digits);
		return Math.round(value * factor) / factor;
	}
}
