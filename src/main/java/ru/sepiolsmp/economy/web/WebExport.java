package ru.sepiolsmp.economy.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import ru.sepiolsmp.economy.SepiolEconomy;
import ru.sepiolsmp.economy.econ.Accounts;
import ru.sepiolsmp.economy.econ.Market;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Выгрузка экономики в JSON для веб-профиля (его раздаёт SepiolCore).
 * Сбор данных — в главном потоке, запись файла — асинхронно.
 */
public final class WebExport {

	private final SepiolEconomy plugin;
	private final Gson gson = new GsonBuilder().create();

	public WebExport(SepiolEconomy plugin) {
		this.plugin = plugin;
	}

	public void export() {
		if (!plugin.getConfig().getBoolean("web.export", true)) {
			return;
		}
		Market market = plugin.market();
		Accounts accounts = plugin.accounts();

		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.addProperty("generated", System.currentTimeMillis());

		JsonObject currency = new JsonObject();
		currency.addProperty("singular", plugin.getConfig().getString("economy.currency.singular", "сепиол"));
		currency.addProperty("few", plugin.getConfig().getString("economy.currency.few", "сепиола"));
		currency.addProperty("many", plugin.getConfig().getString("economy.currency.many", "сепиолов"));
		root.add("currency", currency);

		JsonObject stats = new JsonObject();
		stats.addProperty("accounts", accounts.count());
		stats.addProperty("supply", accounts.supply());
		stats.addProperty("treasury", accounts.treasury());
		stats.addProperty("burned", accounts.burned());
		stats.addProperty("startingBalance", accounts.startingBalance());
		stats.addProperty("feePercent", plugin.api().feePercent());
		stats.addProperty("emittedToday", market.emittedToday());
		stats.addProperty("dailyCap", market.dailyCap());
		stats.addProperty("positions", market.size());
		root.add("stats", stats);

		int topSize = Math.max(1, Math.min(50, plugin.getConfig().getInt("web.top-size", 25)));
		JsonArray top = new JsonArray();
		List<Accounts.Entry> entries = accounts.top(topSize);
		int pos = 1;
		for (Accounts.Entry entry : entries) {
			JsonObject row = new JsonObject();
			row.addProperty("pos", pos++);
			row.addProperty("name", entry.name);
			row.addProperty("balance", entry.balance);
			top.add(row);
		}
		root.add("top", top);

		JsonArray prices = new JsonArray();
		for (Market.Price price : market.sortedByPrice()) {
			JsonObject row = new JsonObject();
			row.addProperty("id", price.id);
			row.addProperty("name", price.name);
			row.addProperty("base", price.base);
			row.addProperty("price", market.unitPrice(price.id));
			row.addProperty("factor", Math.round(market.factor(price.id) * 1000.0) / 1000.0);
			row.addProperty("saturation", Math.round(market.saturationOf(price.id) * 10.0) / 10.0);
			prices.add(row);
		}
		root.add("prices", prices);

		final String json = gson.toJson(root);
		final File target = new File(plugin.getDataFolder(),
				plugin.getConfig().getString("web.file", "web/economy.json"));
		if (plugin.isEnabled()) {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(target, json));
		} else {
			write(target, json);
		}
	}

	private void write(File target, String json) {
		try {
			File dir = target.getParentFile();
			if (dir != null && !dir.exists()) {
				dir.mkdirs();
			}
			Path tmp = new File(target.getParentFile(), target.getName() + ".tmp").toPath();
			Files.writeString(tmp, json, StandardCharsets.UTF_8);
			Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) {
			plugin.getLogger().warning("Не удалось выгрузить economy.json: " + ex.getMessage());
		}
	}
}
