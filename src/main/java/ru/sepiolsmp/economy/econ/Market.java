package ru.sepiolsmp.economy.econ;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Банк сезона: таблица цен, насыщение рынка и суточный лимит эмиссии.
 *
 * Курс одной позиции: factor = max(min, 1 / (1 + насыщение / квота)).
 * Насыщение растёт при сдаче и само спадает вдвое за полупериод (по умолчанию сутки).
 */
public final class Market {

	public static final class Price {
		public final String id;
		public final String name;
		public final double base;
		public final double quota;
		public final double minFactor;

		Price(String id, String name, double base, double quota, double minFactor) {
			this.id = id;
			this.name = name;
			this.base = base;
			this.quota = quota;
			this.minFactor = minFactor;
		}
	}

	public static final class Quote {
		public final String id;
		public final int qty;
		public final double total;
		public final double unit;
		public final double factor;
		public final boolean capped;

		Quote(String id, int qty, double total, double unit, double factor, boolean capped) {
			this.id = id;
			this.qty = qty;
			this.total = total;
			this.unit = unit;
			this.factor = factor;
			this.capped = capped;
		}
	}

	private final JavaPlugin plugin;
	private final File stateFile;
	private final Gson gson = new GsonBuilder().create();

	private final Map<String, Price> prices = new LinkedHashMap<>();
	private final Map<String, Double> saturation = new HashMap<>();
	private final List<String> contraband = new ArrayList<>();

	private double minFactor = 0.2;
	private double freshBonus = 1.1;
	private double halfLifeHours = 24.0;
	private double quotaMultiplier = 1.0;
	private double dailyCap = 5000.0;
	private double overCapFactor = 0.5;
	private int resetHour = 4;

	private double emittedToday;
	private String day = "";
	private long lastDecay = System.currentTimeMillis();
	private boolean dirty;

	public Market(JavaPlugin plugin) {
		this.plugin = plugin;
		this.stateFile = new File(plugin.getDataFolder(), "market.json");
	}

	// ---------------------------------------------------------------- конфиг

	public void loadConfig(FileConfiguration cfg) {
		prices.clear();
		contraband.clear();

		minFactor = clamp(cfg.getDouble("bank.price.min-factor", 0.2), 0.01, 1.0);
		freshBonus = clamp(cfg.getDouble("bank.price.fresh-bonus", 1.1), 1.0, 3.0);
		halfLifeHours = Math.max(0.25, cfg.getDouble("bank.price.saturation-half-life-hours", 24.0));
		quotaMultiplier = Math.max(0.05, cfg.getDouble("bank.price.quota-multiplier", 1.0));
		dailyCap = Math.max(0.0, cfg.getDouble("bank.emission.daily-cap", 5000.0));
		overCapFactor = clamp(cfg.getDouble("bank.emission.over-cap-factor", 0.5), 0.0, 1.0);
		resetHour = (int) clamp(cfg.getInt("bank.emission.reset-hour", 4), 0, 23);

		for (String pattern : cfg.getStringList("contraband")) {
			if (pattern != null && !pattern.isBlank()) {
				contraband.add(pattern.trim().toLowerCase(Locale.ROOT));
			}
		}

		ConfigurationSection section = cfg.getConfigurationSection("prices");
		if (section == null) {
			plugin.getLogger().warning("В конфиге нет секции prices — банк ничего не принимает.");
			return;
		}
		for (String key : section.getKeys(false)) {
			String id = normalize(key);
			Object value = section.get(key);
			double base;
			double quota = 256.0;
			double ownMin = minFactor;
			String name = prettify(id);
			if (value instanceof Number number) {
				base = number.doubleValue();
			} else {
				ConfigurationSection entry = section.getConfigurationSection(key);
				if (entry == null) {
					plugin.getLogger().warning("Позиция курса без цены: " + key);
					continue;
				}
				base = entry.getDouble("base", 0.0);
				quota = entry.getDouble("quota", 256.0);
				ownMin = clamp(entry.getDouble("min", minFactor), 0.01, 1.0);
				name = entry.getString("name", name);
			}
			if (base <= 0.0) {
				plugin.getLogger().warning("Позиция курса с нулевой ценой пропущена: " + key);
				continue;
			}
			prices.put(id, new Price(id, name, base, Math.max(1.0, quota), ownMin));
		}
		plugin.getLogger().info("Курс банка: " + prices.size() + " позиций, в стоп-листе: " + contraband.size());
	}

	// ---------------------------------------------------------------- состояние

	public void loadState() {
		saturation.clear();
		emittedToday = 0.0;
		day = dayKey();
		lastDecay = System.currentTimeMillis();
		if (!stateFile.exists()) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(stateFile.toPath(), StandardCharsets.UTF_8));
			if (parsed == null || !parsed.isJsonObject()) {
				return;
			}
			JsonObject root = parsed.getAsJsonObject();
			if (root.has("day")) {
				day = root.get("day").getAsString();
			}
			if (root.has("emittedToday")) {
				emittedToday = root.get("emittedToday").getAsDouble();
			}
			if (root.has("lastDecay")) {
				lastDecay = root.get("lastDecay").getAsLong();
			}
			JsonObject sat = root.getAsJsonObject("saturation");
			if (sat != null) {
				for (Map.Entry<String, JsonElement> e : sat.entrySet()) {
					saturation.put(normalize(e.getKey()), e.getValue().getAsDouble());
				}
			}
			if (!day.equals(dayKey())) {
				emittedToday = 0.0;
				day = dayKey();
			}
		} catch (Exception ex) {
			plugin.getLogger().warning("Не удалось прочитать market.json: " + ex.getMessage());
		}
	}

	public void saveState() {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.addProperty("day", day);
		root.addProperty("emittedToday", round(emittedToday));
		root.addProperty("lastDecay", lastDecay);
		JsonObject sat = new JsonObject();
		for (Map.Entry<String, Double> e : saturation.entrySet()) {
			sat.addProperty(e.getKey(), round(e.getValue()));
		}
		root.add("saturation", sat);
		try {
			File dir = stateFile.getParentFile();
			if (dir != null && !dir.exists()) {
				dir.mkdirs();
			}
			Path tmp = new File(stateFile.getParentFile(), stateFile.getName() + ".tmp").toPath();
			Files.writeString(tmp, gson.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			dirty = false;
		} catch (IOException ex) {
			plugin.getLogger().warning("Не удалось сохранить market.json: " + ex.getMessage());
		}
	}

	/** Спад насыщения и сброс суточного лимита. Вызывается каждую минуту. */
	public void tick() {
		String today = dayKey();
		if (!today.equals(day)) {
			day = today;
			emittedToday = 0.0;
			dirty = true;
		}
		long now = System.currentTimeMillis();
		double hours = (now - lastDecay) / 3600000.0;
		if (hours <= 0.0) {
			lastDecay = now;
			return;
		}
		double keep = Math.pow(0.5, hours / halfLifeHours);
		lastDecay = now;
		if (saturation.isEmpty()) {
			return;
		}
		List<String> gone = new ArrayList<>();
		for (Map.Entry<String, Double> e : saturation.entrySet()) {
			double value = e.getValue() * keep;
			if (value < 0.01) {
				gone.add(e.getKey());
			} else {
				e.setValue(value);
			}
		}
		for (String id : gone) {
			saturation.remove(id);
		}
		dirty = true;
	}

	public boolean isDirty() {
		return dirty;
	}

	// ---------------------------------------------------------------- цены

	public Price price(String id) {
		return id == null ? null : prices.get(normalize(id));
	}

	public boolean known(String id) {
		return price(id) != null;
	}

	public boolean isContraband(String id) {
		if (id == null) {
			return false;
		}
		String key = normalize(id);
		for (String pattern : contraband) {
			if (pattern.endsWith("*")) {
				if (key.startsWith(pattern.substring(0, pattern.length() - 1))) {
					return true;
				}
			} else if (key.equals(pattern)) {
				return true;
			}
		}
		return false;
	}

	public double saturationOf(String id) {
		return saturation.getOrDefault(normalize(id), 0.0);
	}

	/** Текущий курс позиции как доля от базовой цены. */
	public double factor(String id) {
		Price p = price(id);
		if (p == null) {
			return 0.0;
		}
		return factorAt(p, saturationOf(id), saturationOf(id) <= 1.0E-6);
	}

	public double unitPrice(String id) {
		Price p = price(id);
		return p == null ? 0.0 : p.base * factor(id);
	}

	private double factorAt(Price p, double sat, boolean fresh) {
		double quota = Math.max(1.0, p.quota * quotaMultiplier);
		double f = 1.0 / (1.0 + Math.max(0.0, sat) / quota);
		if (f < p.minFactor) {
			f = p.minFactor;
		}
		if (fresh) {
			f = f * freshBonus;
		}
		return f;
	}

	/**
	 * Сколько банк заплатит за qty штук именно сейчас.
	 * Цена считается поштучно: большая партия сама себе роняет курс.
	 */
	public Quote quote(String id, int qty) {
		Price p = price(id);
		if (p == null || qty <= 0) {
			return null;
		}
		int amount = Math.min(qty, 100000);
		double sat = saturationOf(id);
		boolean fresh = sat <= 1.0E-6;
		double quota = Math.max(1.0, p.quota * quotaMultiplier);
		double total = 0.0;
		boolean capped = false;
		for (int i = 0; i < amount; i++) {
			double f = 1.0 / (1.0 + (sat + i) / quota);
			if (f < p.minFactor) {
				f = p.minFactor;
			}
			if (fresh) {
				f = f * freshBonus;
			}
			double unit = p.base * f;
			if (dailyCap > 0.0 && emittedToday + total >= dailyCap) {
				unit = unit * overCapFactor;
				capped = true;
			}
			total += unit;
		}
		total = round(total);
		return new Quote(p.id, amount, total, amount == 0 ? 0.0 : round(total / amount), factorAt(p, sat, fresh), capped);
	}

	/** Фиксируем сдачу: рынок насытился, деньги напечатаны. */
	public void register(String id, int qty, double emitted) {
		if (qty <= 0) {
			return;
		}
		String key = normalize(id);
		saturation.merge(key, (double) qty, Double::sum);
		emittedToday = round(emittedToday + Math.max(0.0, emitted));
		dirty = true;
	}

	public void resetSaturation(String id) {
		if (id == null || id.isEmpty()) {
			saturation.clear();
		} else {
			saturation.remove(normalize(id));
		}
		dirty = true;
	}

	/** Все позиции, отсортированные по текущей цене за штуку. */
	public List<Price> sortedByPrice() {
		List<Price> list = new ArrayList<>(prices.values());
		list.sort(Comparator.comparingDouble((Price p) -> p.base * factor(p.id)).reversed());
		return list;
	}

	public List<String> ids() {
		return new ArrayList<>(prices.keySet());
	}

	public int size() {
		return prices.size();
	}

	public double emittedToday() {
		return emittedToday;
	}

	public double dailyCap() {
		return dailyCap;
	}

	public double dailyLeft() {
		return dailyCap <= 0.0 ? -1.0 : Math.max(0.0, round(dailyCap - emittedToday));
	}

	public double halfLifeHours() {
		return halfLifeHours;
	}

	public int resetHour() {
		return resetHour;
	}

	// ---------------------------------------------------------------- вспомогательное

	private String dayKey() {
		return LocalDateTime.now().minusHours(resetHour).toLocalDate().toString();
	}

	public static String normalize(String id) {
		if (id == null) {
			return "";
		}
		String out = id.trim().toLowerCase(Locale.ROOT);
		if (!out.contains(":")) {
			out = "minecraft:" + out;
		}
		return out;
	}

	private static String prettify(String id) {
		int idx = id.indexOf(':');
		String path = idx >= 0 ? id.substring(idx + 1) : id;
		String spaced = path.replace('_', ' ');
		if (spaced.isEmpty()) {
			return id;
		}
		return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
