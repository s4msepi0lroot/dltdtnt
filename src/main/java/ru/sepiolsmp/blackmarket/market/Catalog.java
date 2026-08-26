package ru.sepiolsmp.blackmarket.market;

import java.io.File;
import java.io.FileWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.sepiolsmp.blackmarket.util.Msg;

/**
 * Ассортимент, цены по спросу, остатки завоза и распознавание контрабанды.
 * Цена растёт от покупок (противоположно банку из SepiolEconomy) и остывает со временем.
 */
public final class Catalog {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final JavaPlugin plugin;
	private final Msg msg;
	private final NamespacedKey markKey;
	private final File stateFile;

	private final Map<String, Lot> lots = new LinkedHashMap<>();
	private final List<String> masks = new ArrayList<>();
	private final Map<String, Double> demand = new ConcurrentHashMap<>();
	private final Map<String, Integer> sold = new ConcurrentHashMap<>();

	private double stepPercent = 6.0D;
	private double maxFactor = 4.0D;
	private double halfLifeHours = 24.0D;
	private double restockCooldown = 0.5D;
	private long lastDecay;
	private volatile boolean dirty;

	public Catalog(JavaPlugin plugin, Msg msg) {
		this.plugin = plugin;
		this.msg = msg;
		this.markKey = new NamespacedKey(plugin, "contraband");
		this.stateFile = new File(plugin.getDataFolder(), "catalog.json");
	}

	// ------------------------------------------------------------------
	// конфиг
	// ------------------------------------------------------------------

	public void loadConfig(FileConfiguration cfg) {
		stepPercent = cfg.getDouble("prices.step-percent", 6.0D);
		maxFactor = Math.max(1.0D, cfg.getDouble("prices.max-factor", 4.0D));
		halfLifeHours = Math.max(0.25D, cfg.getDouble("prices.half-life-hours", 24.0D));
		restockCooldown = Math.max(0.0D, Math.min(1.0D, cfg.getDouble("prices.restock-cooldown", 0.5D)));

		masks.clear();
		for (String mask : cfg.getStringList("contraband")) {
			if (mask != null && !mask.isBlank()) {
				masks.add(mask.trim().toLowerCase(Locale.ROOT));
			}
		}

		lots.clear();
		ConfigurationSection root = cfg.getConfigurationSection("lots");
		if (root == null) {
			plugin.getLogger().warning("В config.yml нет секции lots - торговать нечем");
			return;
		}
		for (String id : root.getKeys(false)) {
			ConfigurationSection node = root.getConfigurationSection(id);
			if (node == null) {
				continue;
			}
			double base = node.getDouble("base", -1.0D);
			if (base < 0.0D) {
				plugin.getLogger().warning("Лот " + id + " без цены, пропускаю");
				continue;
			}
			Lot lot = new Lot(
					id,
					node.getString("name", id),
					node.getString("category", ""),
					base,
					node.getDouble("step", -1.0D),
					node.getInt("qty", 1),
					node.getInt("stock", -1),
					node.getString("item"),
					node.getString("icon"),
					node.getBoolean("contraband", false),
					node.getString("effect", ""),
					node.getStringList("commands"),
					node.getStringList("lore"));
			if (!lot.hasItem() && !lot.hasCommands() && !lot.hasEffect()) {
				plugin.getLogger().warning("Лот " + id + " ничего не выдаёт, пропускаю");
				continue;
			}
			lots.put(id, lot);
		}
		plugin.getLogger().info("Ассортимент: " + lots.size() + " лотов, масок контрабанды: " + masks.size());
	}

	// ------------------------------------------------------------------
	// состояние
	// ------------------------------------------------------------------

	public void loadState() {
		demand.clear();
		sold.clear();
		lastDecay = System.currentTimeMillis();
		if (!stateFile.isFile()) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(stateFile.toPath(), StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			lastDecay = root.has("lastDecay") ? root.get("lastDecay").getAsLong() : System.currentTimeMillis();
			if (root.has("demand")) {
				JsonObject node = root.getAsJsonObject("demand");
				for (String key : node.keySet()) {
					demand.put(key, node.get(key).getAsDouble());
				}
			}
			if (root.has("sold")) {
				JsonObject node = root.getAsJsonObject("sold");
				for (String key : node.keySet()) {
					sold.put(key, node.get(key).getAsInt());
				}
			}
		} catch (Exception error) {
			plugin.getLogger().log(Level.WARNING, "Не смог прочитать catalog.json", error);
		}
	}

	public void saveState() {
		try {
			if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
				return;
			}
			JsonObject root = new JsonObject();
			root.addProperty("version", 1);
			root.addProperty("saved", System.currentTimeMillis());
			root.addProperty("lastDecay", lastDecay);
			JsonObject demandNode = new JsonObject();
			for (Map.Entry<String, Double> entry : demand.entrySet()) {
				demandNode.addProperty(entry.getKey(), entry.getValue());
			}
			root.add("demand", demandNode);
			JsonObject soldNode = new JsonObject();
			for (Map.Entry<String, Integer> entry : sold.entrySet()) {
				soldNode.addProperty(entry.getKey(), entry.getValue());
			}
			root.add("sold", soldNode);

			File temp = new File(stateFile.getParentFile(), stateFile.getName() + ".tmp");
			try (FileWriter writer = new FileWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
			Files.move(temp.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			dirty = false;
		} catch (Exception error) {
			plugin.getLogger().log(Level.WARNING, "Не смог сохранить catalog.json", error);
		}
	}

	public boolean isDirty() {
		return dirty;
	}

	// ------------------------------------------------------------------
	// цены и остатки
	// ------------------------------------------------------------------

	public Collection<Lot> lots() {
		return lots.values();
	}

	public List<String> ids() {
		return new ArrayList<>(lots.keySet());
	}

	public Lot lot(String id) {
		return id == null ? null : lots.get(id.toLowerCase(Locale.ROOT));
	}

	public int size() {
		return lots.size();
	}

	public double demandOf(String id) {
		Double value = demand.get(id);
		return value == null ? 0.0D : value;
	}

	/** Множитель цены: 1.0 = база, выше = разогретый спрос. */
	public double factor(String id) {
		Lot lot = lots.get(id);
		if (lot == null) {
			return 1.0D;
		}
		double step = lot.step >= 0.0D ? lot.step : stepPercent;
		double value = 1.0D + (step / 100.0D) * demandOf(id);
		return Math.min(maxFactor, Math.max(1.0D, value));
	}

	public double price(String id) {
		Lot lot = lots.get(id);
		if (lot == null) {
			return 0.0D;
		}
		return lot.base * factor(id);
	}

	/** Остаток завоза, -1 = без лимита. */
	public int left(String id) {
		Lot lot = lots.get(id);
		if (lot == null) {
			return 0;
		}
		if (lot.unlimited()) {
			return -1;
		}
		Integer taken = sold.get(id);
		return Math.max(0, lot.stock - (taken == null ? 0 : taken));
	}

	public boolean soldOut(String id) {
		return left(id) == 0;
	}

	public void registerPurchase(String id) {
		if (!lots.containsKey(id)) {
			return;
		}
		demand.merge(id, 1.0D, Double::sum);
		sold.merge(id, 1, Integer::sum);
		dirty = true;
	}

	/** Новый завоз: остатки полные, спрос частично остывает. */
	public void restock() {
		sold.clear();
		if (restockCooldown <= 0.0D) {
			demand.clear();
		} else {
			for (Map.Entry<String, Double> entry : demand.entrySet()) {
				entry.setValue(entry.getValue() * restockCooldown);
			}
			demand.entrySet().removeIf(entry -> entry.getValue() < 0.01D);
		}
		dirty = true;
	}

	/** Остывание спроса по полураспаду. */
	public void tick() {
		long now = System.currentTimeMillis();
		if (lastDecay <= 0L) {
			lastDecay = now;
			return;
		}
		double hours = (now - lastDecay) / 3_600_000.0D;
		if (hours <= 0.0D || demand.isEmpty()) {
			lastDecay = now;
			return;
		}
		double multiplier = Math.pow(0.5D, hours / halfLifeHours);
		lastDecay = now;
		if (multiplier >= 0.9999D) {
			return;
		}
		for (Map.Entry<String, Double> entry : demand.entrySet()) {
			entry.setValue(entry.getValue() * multiplier);
		}
		demand.entrySet().removeIf(entry -> entry.getValue() < 0.01D);
		dirty = true;
	}

	public double maxFactor() {
		return maxFactor;
	}

	public double stepPercent() {
		return stepPercent;
	}

	public double halfLifeHours() {
		return halfLifeHours;
	}

	// ------------------------------------------------------------------
	// предметы
	// ------------------------------------------------------------------

	public NamespacedKey markKey() {
		return markKey;
	}

	/** Ванильный товар с именем, лором и скрытой меткой контрабанды. */
	public ItemStack build(Lot lot) {
		Material material = material(lot.item, material(lot.icon, Material.PAPER));
		ItemStack stack = new ItemStack(material, Math.max(1, lot.qty));
		ItemMeta meta = stack.getItemMeta();
		if (meta != null) {
			meta.displayName(Msg.text(lot.name));
			List<Component> lore = new ArrayList<>();
			for (String line : lot.lore) {
				lore.add(Msg.text(line));
			}
			if (lot.contraband) {
				lore.add(Msg.text(msg.raw("gui-contraband")));
				meta.getPersistentDataContainer().set(markKey, PersistentDataType.STRING, lot.id);
			}
			if (!lore.isEmpty()) {
				meta.lore(lore);
			}
			stack.setItemMeta(meta);
		}
		return stack;
	}

	/** Иконка для витрины (без метки, одна штука). */
	public ItemStack icon(Lot lot) {
		Material material = material(lot.icon, material(lot.item, Material.PAPER));
		return new ItemStack(material, 1);
	}

	public static Material material(String id, Material fallback) {
		if (id == null || id.isBlank()) {
			return fallback;
		}
		Material direct = Material.matchMaterial(id);
		if (direct != null) {
			return direct;
		}
		int colon = id.indexOf(':');
		if (colon > 0 && colon + 1 < id.length()) {
			Material short1 = Material.matchMaterial(id.substring(colon + 1));
			if (short1 != null) {
				return short1;
			}
		}
		return fallback;
	}

	/** Идентификатор предмета вида namespace:key. */
	public static String idOf(ItemStack stack) {
		if (stack == null || stack.getType() == Material.AIR) {
			return "";
		}
		try {
			return stack.getType().getKey().toString().toLowerCase(Locale.ROOT);
		} catch (Throwable ignored) {
			return stack.getType().name().toLowerCase(Locale.ROOT);
		}
	}

	// ------------------------------------------------------------------
	// контрабанда
	// ------------------------------------------------------------------

	public List<String> masks() {
		return new ArrayList<>(masks);
	}

	/** По идентификатору: работает и для badhabits:cig_black, и для badhabits_cig_black. */
	public boolean isContrabandId(String id) {
		if (id == null || id.isEmpty()) {
			return false;
		}
		String value = id.toLowerCase(Locale.ROOT);
		String flat = value.replace(':', '_');
		for (String mask : masks) {
			if (matches(value, mask) || matches(flat, mask.replace(':', '_'))) {
				return true;
			}
		}
		return false;
	}

	private static boolean matches(String value, String mask) {
		if (mask.endsWith("*")) {
			return value.startsWith(mask.substring(0, mask.length() - 1));
		}
		return value.equals(mask);
	}

	/** Метка рынка или совпадение по идентификатору. */
	public boolean isContraband(ItemStack stack) {
		if (stack == null || stack.getType() == Material.AIR) {
			return false;
		}
		ItemMeta meta = stack.getItemMeta();
		if (meta != null && meta.getPersistentDataContainer().has(markKey, PersistentDataType.STRING)) {
			return true;
		}
		return isContrabandId(idOf(stack));
	}

	/** Сколько штук запрещённого при себе. */
	public int countContraband(Player player) {
		int total = 0;
		ItemStack[] contents = player.getInventory().getContents();
		for (ItemStack stack : contents) {
			if (isContraband(stack)) {
				total += stack.getAmount();
			}
		}
		return total;
	}

	/** Изъятие. Возвращает число изъятых штук. */
	public int confiscate(Player player) {
		int total = 0;
		ItemStack[] contents = player.getInventory().getContents();
		for (int slot = 0; slot < contents.length; slot++) {
			ItemStack stack = contents[slot];
			if (isContraband(stack)) {
				total += stack.getAmount();
				player.getInventory().setItem(slot, null);
			}
		}
		ItemStack cursor = player.getItemOnCursor();
		if (isContraband(cursor)) {
			total += cursor.getAmount();
			player.setItemOnCursor(null);
		}
		if (total > 0) {
			player.updateInventory();
		}
		return total;
	}
}
