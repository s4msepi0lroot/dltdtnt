package ru.sepiolsmp.blackmarket.econ;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Мост к деньгам без единой строчки компиляционной зависимости.
 * Приоритет: SepiolEconomy (сервис или плагин) -> любая Vault-экономика.
 * Всё через рефлексию и подбор параметров по типам, чтобы мелкие правки API не ломали рынок.
 */
public final class EconomyLink {

	public enum Mode {
		NONE,
		SEPIOL,
		VAULT
	}

	private static final String API_CLASS = "ru.sepiolsmp.economy.econ.EconomyApi";
	private static final String VAULT_CLASS = "net.milkbowl.vault.economy.Economy";

	private final JavaPlugin plugin;

	private String preferred = "auto";
	private Mode mode = Mode.NONE;
	private Object provider;
	private Method mBalance;
	private Method mWithdraw;
	private Method mDeposit;
	private Method mFormat;
	private long lastAttempt;

	public EconomyLink(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void setPreferred(String value) {
		this.preferred = value == null ? "auto" : value.trim().toLowerCase(java.util.Locale.ROOT);
	}

	public Mode mode() {
		return mode;
	}

	public String modeName() {
		return switch (mode) {
			case SEPIOL -> "SepiolEconomy";
			case VAULT -> "Vault";
			default -> "нет";
		};
	}

	public boolean available() {
		if (provider != null && mBalance != null) {
			return true;
		}
		long now = System.currentTimeMillis();
		if (now - lastAttempt < 10_000L) {
			return false;
		}
		return resolve();
	}

	/** Попытка привязаться к экономике. Безопасно вызывать повторно. */
	public boolean resolve() {
		lastAttempt = System.currentTimeMillis();
		provider = null;
		mode = Mode.NONE;
		mBalance = null;
		mWithdraw = null;
		mDeposit = null;
		mFormat = null;

		if ("none".equals(preferred)) {
			return false;
		}
		if (!"vault".equals(preferred) && bindSepiol()) {
			return true;
		}
		if (!"sepiol".equals(preferred) && bindVault()) {
			return true;
		}
		return false;
	}

	private boolean bindSepiol() {
		Object api = null;
		try {
			for (Class<?> service : Bukkit.getServicesManager().getKnownServices()) {
				if (API_CLASS.equals(service.getName())) {
					api = Bukkit.getServicesManager().load(service);
					break;
				}
			}
		} catch (Throwable ignored) {
			api = null;
		}
		if (api == null) {
			try {
				Plugin economy = Bukkit.getPluginManager().getPlugin("SepiolEconomy");
				if (economy != null && economy.isEnabled()) {
					Method accessor = find(economy.getClass(), "api", 0);
					if (accessor != null) {
						api = accessor.invoke(economy);
					}
				}
			} catch (Throwable ignored) {
				api = null;
			}
		}
		if (api == null) {
			return false;
		}
		Class<?> type = api.getClass();
		Method balance = find(type, "balance", 1);
		Method withdraw = find(type, "withdraw", 3);
		if (withdraw == null) {
			withdraw = find(type, "withdraw", 2);
		}
		Method deposit = find(type, "deposit", 3);
		if (deposit == null) {
			deposit = find(type, "deposit", 2);
		}
		if (balance == null || withdraw == null || deposit == null) {
			plugin.getLogger().warning("SepiolEconomy найдена, но её API не совпадает с ожиданиями рынка");
			return false;
		}
		this.provider = api;
		this.mBalance = balance;
		this.mWithdraw = withdraw;
		this.mDeposit = deposit;
		this.mFormat = find(type, "format", 1);
		this.mode = Mode.SEPIOL;
		plugin.getLogger().info("Деньги: SepiolEconomy");
		return true;
	}

	private boolean bindVault() {
		try {
			Class<?> vault = null;
			for (Class<?> service : Bukkit.getServicesManager().getKnownServices()) {
				if (VAULT_CLASS.equals(service.getName())) {
					vault = service;
					break;
				}
			}
			if (vault == null) {
				return false;
			}
			Object economy = Bukkit.getServicesManager().load(vault);
			if (economy == null) {
				return false;
			}
			Class<?> type = economy.getClass();
			Method balance = find(type, "getBalance", 1);
			Method withdraw = find(type, "withdrawPlayer", 2);
			Method deposit = find(type, "depositPlayer", 2);
			if (balance == null || withdraw == null || deposit == null) {
				return false;
			}
			this.provider = economy;
			this.mBalance = balance;
			this.mWithdraw = withdraw;
			this.mDeposit = deposit;
			this.mFormat = find(type, "format", 1);
			this.mode = Mode.VAULT;
			plugin.getLogger().info("Деньги: Vault (" + type.getName() + ")");
			return true;
		} catch (Throwable error) {
			return false;
		}
	}

	public double balance(UUID id) {
		if (!available()) {
			return 0.0D;
		}
		Object result = call(mBalance, id, 0.0D, null);
		return result instanceof Number number ? number.doubleValue() : 0.0D;
	}

	public boolean has(UUID id, double amount) {
		return balance(id) + 1.0E-6D >= amount;
	}

	public boolean withdraw(UUID id, double amount, String reason) {
		if (!available() || amount <= 0.0D) {
			return false;
		}
		Object result = call(mWithdraw, id, amount, reason);
		return success(result);
	}

	public boolean deposit(UUID id, double amount, String reason) {
		if (!available() || amount <= 0.0D) {
			return false;
		}
		Object result = call(mDeposit, id, amount, reason);
		return success(result);
	}

	public String format(double amount) {
		if (mFormat != null && provider != null) {
			try {
				Object result = mFormat.invoke(provider, amount);
				if (result instanceof String text && !text.isEmpty()) {
					return text;
				}
			} catch (Throwable ignored) {
				// падаём на своё форматирование
			}
		}
		return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
	}

	/** Подбирает аргументы по типам метода, а не по жёсткой сигнатуре. */
	private Object call(Method method, UUID id, double amount, String reason) {
		if (method == null || provider == null) {
			return null;
		}
		Class<?>[] types = method.getParameterTypes();
		Object[] args = new Object[types.length];
		for (int i = 0; i < types.length; i++) {
			Class<?> type = types[i];
			if (UUID.class.isAssignableFrom(type)) {
				args[i] = id;
			} else if (type == double.class || type == Double.class) {
				args[i] = amount;
			} else if (type == String.class) {
				args[i] = reason == null ? "blackmarket" : reason;
			} else if (OfflinePlayer.class.isAssignableFrom(type)) {
				args[i] = Bukkit.getOfflinePlayer(id);
			} else if (type == boolean.class || type == Boolean.class) {
				args[i] = Boolean.TRUE;
			} else if (type == int.class || type == Integer.class) {
				args[i] = 0;
			} else {
				args[i] = null;
			}
		}
		try {
			return method.invoke(provider, args);
		} catch (Throwable error) {
			plugin.getLogger().log(Level.WARNING, "Ошибка вызова экономики: " + method.getName(), error);
			return null;
		}
	}

	/** true/false, число > 0, Vault-ответ с transactionSuccess() или void. */
	private boolean success(Object result) {
		if (result == null) {
			return true;
		}
		if (result instanceof Boolean flag) {
			return flag;
		}
		if (result instanceof Number number) {
			return number.doubleValue() > 0.0D;
		}
		try {
			Method check = find(result.getClass(), "transactionSuccess", 0);
			if (check != null) {
				Object value = check.invoke(result);
				return value instanceof Boolean flag && flag;
			}
		} catch (Throwable ignored) {
			return false;
		}
		return true;
	}

	private static Method find(Class<?> type, String name, int argc) {
		for (Method method : type.getMethods()) {
			if (!method.getName().equals(name)) {
				continue;
			}
			if (method.getParameterCount() != argc) {
				continue;
			}
			method.setAccessible(true);
			return method;
		}
		return null;
	}
}
