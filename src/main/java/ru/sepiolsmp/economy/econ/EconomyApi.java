package ru.sepiolsmp.economy.econ;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.sepiolsmp.economy.util.Msg;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Единая точка входа в экономику: балансы, переводы, сдача товара в банк.
 * Другие наши плагины (чёрный рынок, контракты, финал) берут его через ServicesManager.
 */
public final class EconomyApi {

	public enum PayStatus { OK, DISABLED, TOO_SMALL, NOT_ENOUGH, COOLDOWN, SELF, OFFLINE, MAX_BALANCE, UNKNOWN }

	public enum SellStatus { OK, DISABLED, NOT_ACCEPTED, CONTRABAND, CUSTOM_ITEM, NOTHING, COOLDOWN }

	public record PayResult(PayStatus status, double amount, double fee, long waitSeconds) {}

	public record SellResult(SellStatus status, String id, int qty, double total, boolean capped) {}

	private final JavaPlugin plugin;
	private final Accounts accounts;
	private final Market market;
	private final Msg msg;

	private boolean payEnabled = true;
	private double feePercent = 5.0;
	private double minFee = 0.05;
	private double minAmount = 1.0;
	private boolean feeToTreasury = true;
	private long payCooldownMs = 3000L;
	private boolean requireOnline;

	private boolean bankEnabled = true;
	private long sellCooldownMs = 1000L;
	private boolean rejectCustom = true;
	private boolean rejectDamaged = true;

	private final Map<UUID, Long> payCooldown = new HashMap<>();
	private final Map<UUID, Long> sellCooldown = new HashMap<>();

	public EconomyApi(JavaPlugin plugin, Accounts accounts, Market market, Msg msg) {
		this.plugin = plugin;
		this.accounts = accounts;
		this.market = market;
		this.msg = msg;
	}

	public void readConfig(FileConfiguration cfg) {
		payEnabled = cfg.getBoolean("pay.enabled", true);
		feePercent = Math.max(0.0, cfg.getDouble("pay.fee-percent", 5.0));
		minFee = Math.max(0.0, cfg.getDouble("pay.min-fee", 0.05));
		minAmount = Math.max(0.01, cfg.getDouble("pay.min-amount", 1.0));
		feeToTreasury = !"burn".equalsIgnoreCase(cfg.getString("pay.fee-destination", "treasury"));
		payCooldownMs = Math.max(0L, cfg.getLong("pay.cooldown-seconds", 3L)) * 1000L;
		requireOnline = cfg.getBoolean("pay.require-online", false);

		bankEnabled = cfg.getBoolean("bank.enabled", true);
		sellCooldownMs = Math.max(0L, cfg.getLong("bank.sell-cooldown-seconds", 1L)) * 1000L;
		rejectCustom = cfg.getBoolean("bank.reject-custom-items", true);
		rejectDamaged = cfg.getBoolean("bank.reject-damaged-tools", true);
	}

	// ---------------------------------------------------------------- базовые операции

	public double balance(UUID id) {
		return accounts.balance(id);
	}

	public boolean has(UUID id, double amount) {
		return accounts.has(id, amount);
	}

	public boolean withdraw(UUID id, double amount, String reason) {
		return accounts.withdraw(id, amount, reason);
	}

	public double deposit(UUID id, double amount, String reason) {
		return accounts.deposit(id, amount, reason);
	}

	public String format(double amount) {
		return msg.money(amount);
	}

	public int decimals() {
		return msg.decimals();
	}

	public String currencySingular() {
		return msg.currencySingular();
	}

	public String currencyPlural() {
		return msg.currencyPlural();
	}

	public double fee(double amount) {
		if (feePercent <= 0.0 && minFee <= 0.0) {
			return 0.0;
		}
		double value = Math.max(minFee, amount * feePercent / 100.0);
		return Math.round(value * 100.0) / 100.0;
	}

	public double feePercent() {
		return feePercent;
	}

	public double minPayAmount() {
		return minAmount;
	}

	public boolean payEnabled() {
		return payEnabled;
	}

	public boolean bankEnabled() {
		return bankEnabled;
	}

	// ---------------------------------------------------------------- переводы

	public PayResult pay(UUID from, UUID to, double rawAmount) {
		if (!payEnabled) {
			return new PayResult(PayStatus.DISABLED, 0.0, 0.0, 0L);
		}
		if (from == null || to == null) {
			return new PayResult(PayStatus.UNKNOWN, 0.0, 0.0, 0L);
		}
		if (from.equals(to)) {
			return new PayResult(PayStatus.SELF, 0.0, 0.0, 0L);
		}
		if (!accounts.exists(to)) {
			return new PayResult(PayStatus.UNKNOWN, 0.0, 0.0, 0L);
		}
		if (requireOnline && Bukkit.getPlayer(to) == null) {
			return new PayResult(PayStatus.OFFLINE, 0.0, 0.0, 0L);
		}
		double amount = Math.round(rawAmount * 100.0) / 100.0;
		if (amount < minAmount) {
			return new PayResult(PayStatus.TOO_SMALL, amount, 0.0, 0L);
		}
		long now = System.currentTimeMillis();
		Long until = payCooldown.get(from);
		if (until != null && until > now) {
			return new PayResult(PayStatus.COOLDOWN, amount, 0.0, (until - now + 999L) / 1000L);
		}
		double commission = fee(amount);
		double need = amount + commission;
		if (!accounts.has(from, need)) {
			return new PayResult(PayStatus.NOT_ENOUGH, amount, commission, 0L);
		}
		if (accounts.balance(to) + amount > accounts.maxBalance()) {
			return new PayResult(PayStatus.MAX_BALANCE, amount, commission, 0L);
		}
		if (!accounts.withdraw(from, need, "pay")) {
			return new PayResult(PayStatus.NOT_ENOUGH, amount, commission, 0L);
		}
		accounts.deposit(to, amount, "pay");
		if (commission > 0.0) {
			if (feeToTreasury) {
				accounts.addTreasury(commission);
			} else {
				accounts.burn(commission);
			}
		}
		accounts.log("pay", from, to, amount, commission, null, 0, feeToTreasury ? "fee-treasury" : "fee-burn");
		if (payCooldownMs > 0L) {
			payCooldown.put(from, now + payCooldownMs);
		}
		return new PayResult(PayStatus.OK, amount, commission, 0L);
	}

	// ---------------------------------------------------------------- банк: сдача товара

	/** Сдать до limit штук предмета id из инвентаря. limit <= 0 — сдать всё. */
	public SellResult sell(Player player, String rawId, int limit) {
		if (!bankEnabled) {
			return new SellResult(SellStatus.DISABLED, rawId, 0, 0.0, false);
		}
		String id = Market.normalize(rawId);
		if (market.isContraband(id)) {
			return new SellResult(SellStatus.CONTRABAND, id, 0, 0.0, false);
		}
		if (!market.known(id)) {
			return new SellResult(SellStatus.NOT_ACCEPTED, id, 0, 0.0, false);
		}
		UUID uuid = player.getUniqueId();
		long now = System.currentTimeMillis();
		Long until = sellCooldown.get(uuid);
		if (until != null && until > now) {
			return new SellResult(SellStatus.COOLDOWN, id, 0, 0.0, false);
		}
		int available = countSellable(player, id);
		if (available <= 0) {
			int blocked = countMatching(player, id);
			return new SellResult(blocked > 0 ? SellStatus.CUSTOM_ITEM : SellStatus.NOTHING, id, 0, 0.0, false);
		}
		int qty = limit > 0 ? Math.min(limit, available) : available;
		Market.Quote quote = market.quote(id, qty);
		if (quote == null || quote.total <= 0.0) {
			return new SellResult(SellStatus.NOT_ACCEPTED, id, 0, 0.0, false);
		}
		int removed = removeItems(player, id, qty);
		if (removed <= 0) {
			return new SellResult(SellStatus.NOTHING, id, 0, 0.0, false);
		}
		Market.Quote finalQuote = removed == qty ? quote : market.quote(id, removed);
		double total = finalQuote == null ? 0.0 : finalQuote.total;
		double paid = accounts.deposit(uuid, total, "bank-sell");
		market.register(id, removed, paid);
		accounts.log("sell", null, uuid, paid, 0.0, id, removed, "bank");
		if (sellCooldownMs > 0L) {
			sellCooldown.put(uuid, now + sellCooldownMs);
		}
		boolean capped = finalQuote != null && finalQuote.capped;
		return new SellResult(SellStatus.OK, id, removed, paid, capped);
	}

	/** Сколько банк заплатит за всё, что есть в инвентаре (без сдачи). */
	public Market.Quote preview(Player player, String rawId) {
		String id = Market.normalize(rawId);
		int available = countSellable(player, id);
		if (available <= 0) {
			return null;
		}
		return market.quote(id, available);
	}

	// ---------------------------------------------------------------- предметы

	/** Намеспейс-ключ предмета: minecraft:diamond, badhabits:detox_tonic, boozecraft:yeast. */
	public static String idOf(ItemStack item) {
		if (item == null) {
			return "";
		}
		try {
			return item.getType().getKey().toString().toLowerCase(Locale.ROOT);
		} catch (Throwable ignored) {
			return "minecraft:" + item.getType().name().toLowerCase(Locale.ROOT);
		}
	}

	/** Обычный ли это стак: без имени, лора, зачарований, NBT и поломки. */
	public boolean sellable(ItemStack item) {
		if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
			return false;
		}
		if (!rejectCustom) {
			return true;
		}
		if (!item.hasItemMeta()) {
			return true;
		}
		ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return true;
		}
		if (meta.hasDisplayName() || meta.hasLore() || meta.hasEnchants()) {
			return false;
		}
		if (meta.hasCustomModelData()) {
			return false;
		}
		if (!meta.getPersistentDataContainer().getKeys().isEmpty()) {
			return false;
		}
		if (rejectDamaged && meta instanceof Damageable damageable && damageable.hasDamage()) {
			return false;
		}
		return true;
	}

	public int countSellable(Player player, String rawId) {
		String id = Market.normalize(rawId);
		int total = 0;
		for (ItemStack item : player.getInventory().getContents()) {
			if (item == null || !sellable(item)) {
				continue;
			}
			if (idOf(item).equals(id)) {
				total += item.getAmount();
			}
		}
		return total;
	}

	private int countMatching(Player player, String rawId) {
		String id = Market.normalize(rawId);
		int total = 0;
		for (ItemStack item : player.getInventory().getContents()) {
			if (item == null || item.getType().isAir()) {
				continue;
			}
			if (idOf(item).equals(id)) {
				total += item.getAmount();
			}
		}
		return total;
	}

	private int removeItems(Player player, String rawId, int qty) {
		String id = Market.normalize(rawId);
		ItemStack[] contents = player.getInventory().getContents();
		int left = qty;
		int removed = 0;
		for (int i = 0; i < contents.length && left > 0; i++) {
			ItemStack item = contents[i];
			if (item == null || !sellable(item) || !idOf(item).equals(id)) {
				continue;
			}
			int take = Math.min(left, item.getAmount());
			if (take >= item.getAmount()) {
				contents[i] = null;
			} else {
				item.setAmount(item.getAmount() - take);
				contents[i] = item;
			}
			left -= take;
			removed += take;
		}
		if (removed > 0) {
			player.getInventory().setContents(contents);
			player.updateInventory();
		}
		return removed;
	}

	// ---------------------------------------------------------------- доступ к внутренностям

	public Accounts accounts() {
		return accounts;
	}

	public Market market() {
		return market;
	}

	public Msg messages() {
		return msg;
	}

	public JavaPlugin plugin() {
		return plugin;
	}
}
