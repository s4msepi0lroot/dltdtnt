package ru.sepiolsmp.blackmarket.util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.sepiolsmp.blackmarket.market.Lot;

/** Журнал сделок: одна строка JSON на событие, файл на дату. */
public final class DealLog {

	private static final Gson GSON = new Gson();

	private final JavaPlugin plugin;
	private boolean enabled = true;
	private String directory = "deals";

	public DealLog(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void loadConfig(FileConfiguration cfg) {
		enabled = cfg.getBoolean("storage.deals", true);
		directory = cfg.getString("storage.deals-dir", "deals");
	}

	public void buy(Player player, Lot lot, double price) {
		JsonObject row = base("buy");
		row.addProperty("player", player.getName());
		row.addProperty("uuid", player.getUniqueId().toString());
		row.addProperty("lot", lot.id);
		row.addProperty("item", lot.plainName());
		row.addProperty("qty", lot.qty);
		row.addProperty("price", round(price));
		row.addProperty("contraband", lot.contraband);
		write(row);
	}

	public void coords(Player player, double price, String coords) {
		JsonObject row = base("coords");
		row.addProperty("player", player.getName());
		row.addProperty("uuid", player.getUniqueId().toString());
		row.addProperty("price", round(price));
		row.addProperty("coords", coords);
		write(row);
	}

	public void check(String staff, String target, int items, boolean confiscated) {
		JsonObject row = base(confiscated ? "raid" : "scan");
		row.addProperty("staff", staff);
		row.addProperty("player", target);
		row.addProperty("items", items);
		write(row);
	}

	public void wanted(String target, int minutes, String reason) {
		JsonObject row = base("wanted");
		row.addProperty("player", target);
		row.addProperty("minutes", minutes);
		row.addProperty("reason", reason == null ? "" : reason);
		write(row);
	}

	private JsonObject base(String type) {
		JsonObject row = new JsonObject();
		row.addProperty("t", System.currentTimeMillis());
		row.addProperty("type", type);
		return row;
	}

	private static double round(double value) {
		return Math.round(value * 100.0D) / 100.0D;
	}

	private void write(JsonObject row) {
		if (!enabled) {
			return;
		}
		try {
			File folder = new File(plugin.getDataFolder(), directory);
			if (!folder.isDirectory() && !folder.mkdirs()) {
				return;
			}
			File file = new File(folder, LocalDate.now() + ".jsonl");
			Files.writeString(
					file.toPath(),
					GSON.toJson(row) + System.lineSeparator(),
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
		} catch (Exception error) {
			plugin.getLogger().warning("Не смог записать журнал сделок: " + error.getMessage());
		}
	}
}
