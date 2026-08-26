package ru.sepiolsmp.economy.econ;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Мост к Vault без единой строчки compile-time зависимости.
 *
 * Почему так: VaultAPI живёт на jitpack, его версии различаются (Vault / VaultUnlocked),
 * а собирать плагин без сети должно быть возможно. Здесь интерфейс Economy
 * берётся из чужого плагина в рантайме и реализуется динамическим прокси.
 * Если Vault не установлен — плагин просто работает автономно.
 */
public final class VaultBridge {

	private final JavaPlugin plugin;
	private final EconomyApi api;
	private Object proxy;

	public VaultBridge(JavaPlugin plugin, EconomyApi api) {
		this.plugin = plugin;
		this.api = api;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public boolean register(String priorityName) {
		boolean vaultPresent = Bukkit.getPluginManager().getPlugin("Vault") != null
				|| Bukkit.getPluginManager().getPlugin("VaultUnlocked") != null;
		if (!vaultPresent) {
			plugin.getLogger().info("Vault не найден — экономика работает автономно.");
			return false;
		}
		try {
			Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
			Class<?> responseClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
			Class<?> typeClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse$ResponseType");
			Constructor<?> responseCtor = responseClass.getConstructor(double.class, double.class, typeClass, String.class);

			Object typeSuccess = null;
			Object typeFailure = null;
			Object typeNotImplemented = null;
			Object[] constants = typeClass.getEnumConstants();
			if (constants != null) {
				for (Object constant : constants) {
					String name = ((Enum<?>) constant).name();
					if (name.equals("SUCCESS")) {
						typeSuccess = constant;
					} else if (name.equals("FAILURE")) {
						typeFailure = constant;
					} else if (name.equals("NOT_IMPLEMENTED")) {
						typeNotImplemented = constant;
					}
				}
			}
			final Object success = typeSuccess;
			final Object failure = typeFailure;
			final Object notImplemented = typeNotImplemented == null ? typeFailure : typeNotImplemented;

			InvocationHandler handler = (self, method, args) -> {
				String name = method.getName();
				switch (name) {
					case "isEnabled":
						return Boolean.TRUE;
					case "getName":
						return "SepiolEconomy";
					case "hasBankSupport":
						return Boolean.FALSE;
					case "fractionalDigits":
						return Integer.valueOf(api.decimals());
					case "format":
						return api.format(amountOf(args));
					case "currencyNamePlural":
						return api.currencyPlural();
					case "currencyNameSingular":
						return api.currencySingular();
					case "hasAccount":
					case "createPlayerAccount":
						return Boolean.TRUE;
					case "getBalance": {
						UUID id = resolve(args);
						return Double.valueOf(id == null ? 0.0 : api.balance(id));
					}
					case "has": {
						UUID id = resolve(args);
						return Boolean.valueOf(id != null && api.has(id, amountOf(args)));
					}
					case "withdrawPlayer": {
						UUID id = resolve(args);
						double amount = amountOf(args);
						if (id == null) {
							return responseCtor.newInstance(0.0, 0.0, failure, "Счёт не найден");
						}
						boolean ok = api.withdraw(id, amount, "vault");
						return responseCtor.newInstance(ok ? amount : 0.0, api.balance(id),
								ok ? success : failure, ok ? null : "Недостаточно средств");
					}
					case "depositPlayer": {
						UUID id = resolve(args);
						double amount = amountOf(args);
						if (id == null) {
							return responseCtor.newInstance(0.0, 0.0, failure, "Счёт не найден");
						}
						double added = api.deposit(id, amount, "vault");
						return responseCtor.newInstance(added, api.balance(id),
								added > 0.0 ? success : failure, added > 0.0 ? null : "Переполнение счёта");
					}
					case "getBanks":
						return new ArrayList<String>();
					case "createBank":
					case "deleteBank":
					case "bankBalance":
					case "bankHas":
					case "bankWithdraw":
					case "bankDeposit":
					case "isBankOwner":
					case "isBankMember":
						return responseCtor.newInstance(0.0, 0.0, notImplemented, "Банковские счета Vault не поддерживаются");
					case "toString":
						return "SepiolEconomy (Vault bridge)";
					case "hashCode":
						return Integer.valueOf(System.identityHashCode(self));
					case "equals":
						return Boolean.valueOf(args != null && args.length > 0 && self == args[0]);
					default:
						return defaultFor(method.getReturnType());
				}
			};

			proxy = Proxy.newProxyInstance(economyClass.getClassLoader(), new Class<?>[]{economyClass}, handler);
			ServicePriority priority;
			try {
				priority = ServicePriority.valueOf(priorityName == null ? "Highest" : priorityName);
			} catch (IllegalArgumentException ignored) {
				priority = ServicePriority.Highest;
			}
			Bukkit.getServicesManager().register((Class) economyClass, proxy, plugin, priority);
			plugin.getLogger().info("Экономика зарегистрирована в Vault (приоритет " + priority.name() + ").");
			return true;
		} catch (ClassNotFoundException ex) {
			plugin.getLogger().info("Классы Vault не найдены — работаем автономно.");
			return false;
		} catch (Throwable ex) {
			plugin.getLogger().warning("Не удалось подключиться к Vault: " + ex.getMessage());
			return false;
		}
	}

	public void unregister() {
		if (proxy == null) {
			return;
		}
		try {
			Bukkit.getServicesManager().unregister(proxy);
		} catch (Throwable ignored) {
			// сервер выключается — не важно
		}
		proxy = null;
	}

	private UUID resolve(Object[] args) {
		if (args == null) {
			return null;
		}
		for (Object arg : args) {
			if (arg instanceof OfflinePlayer offline) {
				return offline.getUniqueId();
			}
		}
		for (Object arg : args) {
			if (arg instanceof UUID uuid) {
				return uuid;
			}
		}
		for (Object arg : args) {
			if (arg instanceof String name) {
				UUID id = api.accounts().byName(name);
				if (id != null) {
					return id;
				}
			}
		}
		return null;
	}

	private static double amountOf(Object[] args) {
		double value = 0.0;
		if (args == null) {
			return value;
		}
		for (Object arg : args) {
			if (arg instanceof Double number) {
				value = number.doubleValue();
			}
		}
		return value;
	}

	private static Object defaultFor(Class<?> type) {
		if (type == boolean.class) {
			return Boolean.FALSE;
		}
		if (type == int.class) {
			return Integer.valueOf(0);
		}
		if (type == long.class) {
			return Long.valueOf(0L);
		}
		if (type == double.class) {
			return Double.valueOf(0.0);
		}
		if (type == float.class) {
			return Float.valueOf(0.0F);
		}
		if (type == List.class) {
			return new ArrayList<String>();
		}
		return null;
	}
}
