package ru.sepiolsmp.blackmarket.market;

import java.io.File;
import java.io.FileWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.sepiolsmp.blackmarket.util.Msg;

/** Розыск: кто попался с контрабандой, на сколько и светится ли он. */
public final class Wanted {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static final class Entry {
		public final UUID id;
		public String name;
		public long until;
		public int level;

		public Entry(UUID id, String name, long until, int level) {
			this.id = id;
			this.name = name == null ? "?" : name;
			this.until = until;
			this.level = level;
		}
	}

	private final JavaPlugin plugin;
	private final Msg msg;
	private final File stateFile;
	private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

	private boolean enabled = true;
	private int minutes = 30;
	private int perItemMinutes = 2;
	private int maxMinutes = 180;
	private boolean glow = true;
	private boolean announce = true;
	private boolean staffOnly;
	private boolean keepOnDeath = true;
	private volatile boolean dirty;

	public Wanted(JavaPlugin plugin, Msg msg) {
		this.plugin = plugin;
		this.msg = msg;
		this.stateFile = new File(plugin.getDataFolder(), "wanted.json");
	}

	public void loadConfig(FileConfiguration cfg) {
		enabled = cfg.getBoolean("wanted.enabled", true);
		minutes = Math.max(1, cfg.getInt("wanted.minutes", 30));
		perItemMinutes = Math.max(0, cfg.getInt("wanted.per-item-minutes", 2));
		maxMinutes = Math.max(minutes, cfg.getInt("wanted.max-minutes", 180));
		glow = cfg.getBoolean("wanted.glow", true);
		announce = cfg.getBoolean("wanted.announce", true);
		staffOnly = cfg.getBoolean("wanted.staff-only-announce", false);
		keepOnDeath = cfg.getBoolean("wanted.keep-on-death", true);
	}

	public void loadState() {
		entries.clear();
		if (!stateFile.isFile()) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(stateFile.toPath(), StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (!root.has("wanted")) {
				return;
			}
			JsonObject node = root.getAsJsonObject("wanted");
			long now = System.currentTimeMillis();
			for (String key : node.keySet()) {
				try {
					UUID id = UUID.fromString(key);
					JsonObject row = node.getAsJsonObject(key);
					long until = row.has("until") ? row.get("until").getAsLong() : 0L;
					if (until <= now) {
						continue;
					}
					String name = row.has("name") ? row.get("name").getAsString() : "?";
					int level = row.has("level") ? row.get("level").getAsInt() : 1;
					entries.put(id, new Entry(id, name, until, level));
				} catch (Exception ignored) {
					// битая строка - пропускаем
				}
			}
		} catch (Exception error) {
			plugin.getLogger().log(Level.WARNING, "Не смог прочитать wanted.json", error);
		}
	}

	public void saveState() {
		try {
			if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
				return;
			}
			JsonObject wanted = new JsonObject();
			for (Entry entry : entries.values()) {
				JsonObject row = new JsonObject();
				row.addProperty("name", entry.name);
				row.addProperty("until", entry.until);
				row.addProperty("level", entry.level);
				wanted.add(entry.id.toString(), row);
			}
			JsonObject root = new JsonObject();
			root.addProperty("version", 1);
			root.addProperty("saved", System.currentTimeMillis());
			root.add("wanted", wanted);

			File temp = new File(stateFile.getParentFile(), stateFile.getName() + ".tmp");
			try (FileWriter writer = new FileWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
			Files.move(temp.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			dirty = false;
		} catch (Exception error) {
			plugin.getLogger().log(Level.WARNING, "Не смог сохранить wanted.json", error);
		}
	}

	public boolean isDirty() {
		return dirty;
	}

	public boolean enabled() {
		return enabled;
	}

	public boolean keepOnDeath() {
		return keepOnDeath;
	}

	public boolean isWanted(UUID id) {
		Entry entry = id == null ? null : entries.get(id);
		return entry != null && entry.until > System.currentTimeMillis();
	}

	public long leftMillis(UUID id) {
		Entry entry = id == null ? null : entries.get(id);
		return entry == null ? 0L : Math.max(0L, entry.until - System.currentTimeMillis());
	}

	public int level(UUID id) {
		Entry entry = id == null ? null : entries.get(id);
		return entry == null ? 0 : entry.level;
	}

	public int count() {
		long now = System.currentTimeMillis();
		int total = 0;
		for (Entry entry : entries.values()) {
			if (entry.until > now) {
				total++;
			}
		}
		return total;
	}

	public List<Entry> list() {
		long now = System.currentTimeMillis();
		List<Entry> out = new ArrayList<>();
		for (Entry entry : entries.values()) {
			if (entry.until > now) {
				out.add(entry);
			}
		}
		out.sort(Comparator.comparingLong((Entry entry) -> entry.until).reversed());
		return out;
	}

	/** Поймали с количеством контрабанды: база + за каждый предмет. */
	public int mark(Player player, int items) {
		int total = minutes + perItemMinutes * Math.max(0, items);
		return markMinutes(player.getUniqueId(), player.getName(), Math.min(maxMinutes, total));
	}

	/** Ставит розыск на указанное время. Возвращает итоговые минуты. */
	public int markMinutes(UUID id, String name, int newMinutes) {
		if (!enabled || id == null) {
			return 0;
		}
		int capped = Math.max(1, Math.min(maxMinutes, newMinutes));
		long now = System.currentTimeMillis();
		Entry entry = entries.get(id);
		if (entry == null || entry.until <= now) {
			entry = new Entry(id, name, now + capped * 60_000L, 1);
			entries.put(id, entry);
		} else {
			long extended = entry.until + capped * 60_000L;
			long ceiling = now + maxMinutes * 60_000L;
			entry.until = Math.min(extended, ceiling);
			entry.level = entry.level + 1;
			entry.name = name == null ? entry.name : name;
		}
		dirty = true;
		int leftMinutes = (int) Math.max(1L, (entry.until - now) / 60_000L);
		Player online = Bukkit.getPlayer(id);
		if (online != null) {
			msg.send(online, "wanted-set", "%minutes%", leftMinutes);
			apply(online);
		}
		return leftMinutes;
	}

	/** Оповестить мир или только стафф о пойманном. */
	public void announceCatch(String playerName, int items) {
		if (!announce) {
			return;
		}
		if (staffOnly) {
			msg.broadcastPerm("sepiolblackmarket.staff", "wanted-broadcast", "%player%", playerName, "%count%", items);
		} else {
			msg.broadcast("wanted-broadcast", "%player%", playerName, "%count%", items);
		}
	}

	public void clear(UUID id) {
		if (id == null) {
			return;
		}
		if (entries.remove(id) != null) {
			dirty = true;
		}
		Player online = Bukkit.getPlayer(id);
		if (online != null) {
			apply(online);
		}
	}

	/** Светящийся силуэт для тех, кто в розыске. */
	public void apply(Player player) {
		if (player == null) {
			return;
		}
		if (!glow) {
			return;
		}
		boolean shouldGlow = isWanted(player.getUniqueId());
		if (player.isGlowing() != shouldGlow) {
			player.setGlowing(shouldGlow);
		}
	}

	/** Снятие истёкших и поддержка света. */
	public void tick() {
		long now = System.currentTimeMillis();
		for (Entry entry : new ArrayList<>(entries.values())) {
			if (entry.until > now) {
				continue;
			}
			entries.remove(entry.id);
			dirty = true;
			Player online = Bukkit.getPlayer(entry.id);
			if (online != null) {
				msg.send(online, "wanted-expired");
				apply(online);
			}
		}
		for (Player online : Bukkit.getOnlinePlayers()) {
			apply(online);
		}
	}
}
