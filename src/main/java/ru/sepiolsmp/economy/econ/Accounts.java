package ru.sepiolsmp.economy.econ;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кошельки игроков, казна сезона и журнал транзакций.
 * Файлы: accounts.json + ledger/ГГГГ-ММ-ДД.jsonl (без внешних баз и драйверов).
 */
public final class Accounts {

	public static final class Acc {
		public String name = "";
		public double balance;
		public double earned;
		public double spent;
		public long created;
	}

	public static final class Entry {
		public final UUID id;
		public final String name;
		public final double balance;

		Entry(UUID id, String name, double balance) {
			this.id = id;
			this.name = name;
			this.balance = balance;
		}
	}

	private final JavaPlugin plugin;
	private final File file;
	private final File ledgerDir;
	private final boolean ledgerEnabled;
	private final double starting;
	private final double maxBalance;
	private final Gson gson = new GsonBuilder().create();

	private final Map<UUID, Acc> accounts = new ConcurrentHashMap<>();
	private final Map<String, UUID> names = new ConcurrentHashMap<>();

	private double treasury;
	private double burned;
	private volatile boolean dirty;

	public Accounts(JavaPlugin plugin, double starting, double maxBalance, boolean ledgerEnabled, String ledgerDir) {
		this.plugin = plugin;
		this.starting = Math.max(0.0, starting);
		this.maxBalance = maxBalance <= 0.0 ? Double.MAX_VALUE : maxBalance;
		this.ledgerEnabled = ledgerEnabled;
		this.file = new File(plugin.getDataFolder(), "accounts.json");
		this.ledgerDir = new File(plugin.getDataFolder(), ledgerDir == null || ledgerDir.isEmpty() ? "ledger" : ledgerDir);
	}

	// ---------------------------------------------------------------- чтение / запись

	public void load() {
		accounts.clear();
		names.clear();
		treasury = 0.0;
		burned = 0.0;
		if (!file.exists()) {
			return;
		}
		try {
			String rawJson = Files.readString(file.toPath(), StandardCharsets.UTF_8);
			JsonElement parsed = JsonParser.parseString(rawJson);
			if (parsed == null || !parsed.isJsonObject()) {
				return;
			}
			JsonObject root = parsed.getAsJsonObject();
			if (root.has("treasury")) {
				treasury = root.get("treasury").getAsDouble();
			}
			if (root.has("burned")) {
				burned = root.get("burned").getAsDouble();
			}
			JsonObject accs = root.getAsJsonObject("accounts");
			if (accs != null) {
				for (Map.Entry<String, JsonElement> e : accs.entrySet()) {
					UUID id;
					try {
						id = UUID.fromString(e.getKey());
					} catch (IllegalArgumentException ignored) {
						continue;
					}
					if (!e.getValue().isJsonObject()) {
						continue;
					}
					JsonObject o = e.getValue().getAsJsonObject();
					Acc acc = new Acc();
					acc.name = o.has("name") ? o.get("name").getAsString() : "";
					acc.balance = o.has("balance") ? o.get("balance").getAsDouble() : 0.0;
					acc.earned = o.has("earned") ? o.get("earned").getAsDouble() : 0.0;
					acc.spent = o.has("spent") ? o.get("spent").getAsDouble() : 0.0;
					acc.created = o.has("created") ? o.get("created").getAsLong() : System.currentTimeMillis();
					accounts.put(id, acc);
					if (!acc.name.isEmpty()) {
						names.put(acc.name.toLowerCase(Locale.ROOT), id);
					}
				}
			}
			plugin.getLogger().info("Загружено счетов: " + accounts.size());
		} catch (Exception ex) {
			plugin.getLogger().warning("Не удалось прочитать accounts.json: " + ex.getMessage());
		}
	}

	private String serialize() {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.addProperty("saved", System.currentTimeMillis());
		root.addProperty("treasury", round(treasury));
		root.addProperty("burned", round(burned));
		JsonObject accs = new JsonObject();
		for (Map.Entry<UUID, Acc> e : accounts.entrySet()) {
			Acc a = e.getValue();
			JsonObject o = new JsonObject();
			o.addProperty("name", a.name);
			o.addProperty("balance", round(a.balance));
			o.addProperty("earned", round(a.earned));
			o.addProperty("spent", round(a.spent));
			o.addProperty("created", a.created);
			accs.add(e.getKey().toString(), o);
		}
		root.add("accounts", accs);
		return gson.toJson(root);
	}

	/** Снимок делается в главном потоке, запись на диск — в асинхронном. */
	public void saveAsync() {
		if (!dirty) {
			return;
		}
		String json = serialize();
		dirty = false;
		if (plugin.isEnabled()) {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(json));
		} else {
			write(json);
		}
	}

	public void saveNow() {
		write(serialize());
		dirty = false;
	}

	private void write(String json) {
		try {
			File dir = file.getParentFile();
			if (dir != null && !dir.exists()) {
				dir.mkdirs();
			}
			Path tmp = new File(file.getParentFile(), file.getName() + ".tmp").toPath();
			Files.writeString(tmp, json, StandardCharsets.UTF_8);
			Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) {
			plugin.getLogger().warning("Не удалось сохранить accounts.json: " + ex.getMessage());
		}
	}

	// ---------------------------------------------------------------- счета

	/** Создаёт счёт со стартовым капиталом. true — если счёт только что открыт. */
	public boolean ensure(UUID id, String name) {
		Acc acc = accounts.get(id);
		if (acc == null) {
			acc = new Acc();
			acc.name = name == null ? "" : name;
			acc.balance = starting;
			acc.created = System.currentTimeMillis();
			accounts.put(id, acc);
			if (!acc.name.isEmpty()) {
				names.put(acc.name.toLowerCase(Locale.ROOT), id);
			}
			dirty = true;
			log("start", null, id, starting, 0.0, null, 0, "first-join");
			return true;
		}
		if (name != null && !name.isEmpty() && !name.equals(acc.name)) {
			if (!acc.name.isEmpty()) {
				names.remove(acc.name.toLowerCase(Locale.ROOT));
			}
			acc.name = name;
			names.put(name.toLowerCase(Locale.ROOT), id);
			dirty = true;
		}
		return false;
	}

	public boolean exists(UUID id) {
		return id != null && accounts.containsKey(id);
	}

	public double balance(UUID id) {
		Acc acc = id == null ? null : accounts.get(id);
		return acc == null ? 0.0 : acc.balance;
	}

	public boolean has(UUID id, double amount) {
		return balance(id) + 1.0E-6 >= amount;
	}

	public double maxBalance() {
		return maxBalance;
	}

	public double startingBalance() {
		return starting;
	}

	public boolean withdraw(UUID id, double amount, String reason) {
		if (id == null || amount <= 0.0) {
			return false;
		}
		Acc acc = accounts.get(id);
		if (acc == null || acc.balance + 1.0E-6 < amount) {
			return false;
		}
		acc.balance = round(acc.balance - amount);
		acc.spent = round(acc.spent + amount);
		dirty = true;
		log("withdraw", id, null, amount, 0.0, null, 0, reason);
		return true;
	}

	public double deposit(UUID id, double amount, String reason) {
		if (id == null || amount <= 0.0) {
			return 0.0;
		}
		Acc acc = accounts.get(id);
		if (acc == null) {
			ensure(id, "");
			acc = accounts.get(id);
		}
		double room = maxBalance - acc.balance;
		double added = Math.min(amount, Math.max(0.0, room));
		acc.balance = round(acc.balance + added);
		acc.earned = round(acc.earned + added);
		dirty = true;
		log("deposit", null, id, added, 0.0, null, 0, reason);
		return added;
	}

	public void setBalance(UUID id, double value, String reason) {
		if (id == null) {
			return;
		}
		ensure(id, "");
		Acc acc = accounts.get(id);
		acc.balance = round(Math.max(0.0, Math.min(maxBalance, value)));
		dirty = true;
		log("set", null, id, acc.balance, 0.0, null, 0, reason);
	}

	// ---------------------------------------------------------------- казна и стоки

	public double treasury() {
		return treasury;
	}

	public double burned() {
		return burned;
	}

	public void addTreasury(double amount) {
		treasury = round(Math.max(0.0, treasury + amount));
		dirty = true;
	}

	public void burn(double amount) {
		if (amount <= 0.0) {
			return;
		}
		burned = round(burned + amount);
		dirty = true;
	}

	// ---------------------------------------------------------------- выборки

	public UUID byName(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		return names.get(name.toLowerCase(Locale.ROOT));
	}

	public String nameOf(UUID id) {
		Acc acc = id == null ? null : accounts.get(id);
		if (acc == null || acc.name.isEmpty()) {
			return id == null ? "?" : id.toString().substring(0, 8);
		}
		return acc.name;
	}

	public List<String> knownNames() {
		List<String> out = new ArrayList<>();
		for (Acc acc : accounts.values()) {
			if (!acc.name.isEmpty()) {
				out.add(acc.name);
			}
		}
		return out;
	}

	public int count() {
		return accounts.size();
	}

	public double supply() {
		double sum = 0.0;
		for (Acc acc : accounts.values()) {
			sum += acc.balance;
		}
		return round(sum);
	}

	public List<Entry> top(int limit) {
		List<Entry> list = new ArrayList<>();
		for (Map.Entry<UUID, Acc> e : accounts.entrySet()) {
			list.add(new Entry(e.getKey(), nameOf(e.getKey()), e.getValue().balance));
		}
		list.sort(Comparator.comparingDouble((Entry x) -> x.balance).reversed());
		if (limit > 0 && list.size() > limit) {
			return new ArrayList<>(list.subList(0, limit));
		}
		return list;
	}

	// ---------------------------------------------------------------- журнал

	public void log(String type, UUID from, UUID to, double amount, double fee, String item, int qty, String reason) {
		if (!ledgerEnabled) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("t", System.currentTimeMillis());
		o.addProperty("type", type);
		o.addProperty("from", from == null ? "-" : from.toString());
		o.addProperty("to", to == null ? "-" : to.toString());
		o.addProperty("fromName", from == null ? "-" : nameOf(from));
		o.addProperty("toName", to == null ? "-" : nameOf(to));
		o.addProperty("amount", round(amount));
		o.addProperty("fee", round(fee));
		if (item != null) {
			o.addProperty("item", item);
			o.addProperty("qty", qty);
		}
		if (reason != null) {
			o.addProperty("reason", reason);
		}
		final String line = gson.toJson(o) + System.lineSeparator();
		final File target = new File(ledgerDir, LocalDate.now() + ".jsonl");
		if (plugin.isEnabled()) {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> appendLine(target, line));
		} else {
			appendLine(target, line);
		}
	}

	private void appendLine(File target, String line) {
		try {
			if (!ledgerDir.exists()) {
				ledgerDir.mkdirs();
			}
			Files.writeString(target.toPath(), line, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ex) {
			plugin.getLogger().warning("Не удалось писать в журнал: " + ex.getMessage());
		}
	}

	public void markDirty() {
		dirty = true;
	}

	private static double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
