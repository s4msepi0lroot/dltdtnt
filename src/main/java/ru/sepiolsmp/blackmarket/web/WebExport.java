package ru.sepiolsmp.blackmarket.web;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.configuration.file.FileConfiguration;
import ru.sepiolsmp.blackmarket.SepiolBlackMarket;
import ru.sepiolsmp.blackmarket.market.Catalog;
import ru.sepiolsmp.blackmarket.market.Lot;
import ru.sepiolsmp.blackmarket.market.Trader;
import ru.sepiolsmp.blackmarket.market.Wanted;

/** Выгрузка состояния рынка в JSON для веб-профиля (его отдаёт SepiolCore). */
public final class WebExport {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final SepiolBlackMarket plugin;
	private boolean enabled = true;
	private String fileName = "web/blackmarket.json";
	private boolean exposeCoordinates;
	private boolean wantedList = true;

	public WebExport(SepiolBlackMarket plugin) {
		this.plugin = plugin;
	}

	public void loadConfig(FileConfiguration cfg) {
		enabled = cfg.getBoolean("web.export", true);
		fileName = cfg.getString("web.file", "web/blackmarket.json");
		exposeCoordinates = cfg.getBoolean("web.expose-coordinates", false);
		wantedList = cfg.getBoolean("web.wanted-list", true);
	}

	public boolean enabled() {
		return enabled;
	}

	public String fileName() {
		return fileName;
	}

	public File file() {
		return new File(plugin.getDataFolder(), fileName);
	}

	public void export() {
		if (!enabled) {
			return;
		}
		try {
			Catalog catalog = plugin.catalog();
			Trader trader = plugin.trader();
			Wanted wanted = plugin.wanted();

			JsonObject root = new JsonObject();
			root.addProperty("version", 1);
			root.addProperty("generated", System.currentTimeMillis());
			root.addProperty("economy", plugin.economy().modeName());

			JsonObject traderNode = new JsonObject();
			traderNode.addProperty("active", trader.active());
			traderNode.addProperty("revealed", trader.revealed());
			traderNode.addProperty("leavesInMs", trader.leftMillis());
			traderNode.addProperty("nextInMs", trader.untilArrival());
			traderNode.addProperty("buyers", trader.buyersCount());
			traderNode.addProperty("coordsPrice", round(trader.coordsPrice()));
			if (trader.active() && (exposeCoordinates || trader.revealed())) {
				traderNode.addProperty("coords", trader.coordsText());
			}
			root.add("trader", traderNode);

			JsonObject prices = new JsonObject();
			prices.addProperty("stepPercent", catalog.stepPercent());
			prices.addProperty("maxFactor", catalog.maxFactor());
			prices.addProperty("halfLifeHours", catalog.halfLifeHours());
			root.add("prices", prices);

			JsonArray lots = new JsonArray();
			for (Lot lot : catalog.lots()) {
				JsonObject row = new JsonObject();
				row.addProperty("id", lot.id);
				row.addProperty("name", lot.plainName());
				row.addProperty("category", lot.category);
				row.addProperty("contraband", lot.contraband);
				row.addProperty("qty", lot.qty);
				row.addProperty("base", round(lot.base));
				row.addProperty("price", round(catalog.price(lot.id)));
				row.addProperty("factor", round(catalog.factor(lot.id)));
				row.addProperty("left", catalog.left(lot.id));
				lots.add(row);
			}
			root.add("lots", lots);

			if (wantedList) {
				JsonArray array = new JsonArray();
				long now = System.currentTimeMillis();
				for (Wanted.Entry entry : wanted.list()) {
					JsonObject row = new JsonObject();
					row.addProperty("name", entry.name);
					row.addProperty("leftMs", Math.max(0L, entry.until - now));
					row.addProperty("level", entry.level);
					array.add(row);
				}
				root.add("wanted", array);
			}
			root.addProperty("wantedCount", wanted.count());

			File target = file();
			File parent = target.getParentFile();
			if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
				return;
			}
			File temp = new File(parent, target.getName() + ".tmp");
			try (FileWriter writer = new FileWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
			Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception error) {
			plugin.getLogger().log(Level.WARNING, "Не смог выгрузить " + fileName, error);
		}
	}

	private static double round(double value) {
		return Math.round(value * 100.0D) / 100.0D;
	}
}
