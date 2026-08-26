package ru.sepiolsmp.economy;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.sepiolsmp.economy.cmd.AdminCommand;
import ru.sepiolsmp.economy.cmd.BalanceCommand;
import ru.sepiolsmp.economy.cmd.BankCommand;
import ru.sepiolsmp.economy.cmd.PayCommand;
import ru.sepiolsmp.economy.econ.Accounts;
import ru.sepiolsmp.economy.econ.EconomyApi;
import ru.sepiolsmp.economy.econ.Market;
import ru.sepiolsmp.economy.econ.VaultBridge;
import ru.sepiolsmp.economy.gui.BankMenu;
import ru.sepiolsmp.economy.util.Msg;
import ru.sepiolsmp.economy.web.WebExport;

import java.util.ArrayList;
import java.util.List;

/**
 * SepiolEconomy — валюта сезона «sepiolSMP».
 *
 * Принципы:
 *  • единственный источник новых денег — банк сезона (скупка ресурсов);
 *  • банк ничего не продаёт — вещи добываются или покупаются у игроков;
 *  • каждая сдача роняет курс этого ресурса, курс сам восстанавливается со временем;
 *  • контрабанду банк не принимает — это работа чёрного рынка.
 */
public final class SepiolEconomy extends JavaPlugin {

	private Msg msg;
	private Accounts accounts;
	private Market market;
	private EconomyApi api;
	private VaultBridge vault;
	private BankMenu menu;
	private BankCommand bank;
	private WebExport web;

	private final List<BukkitTask> tasks = new ArrayList<>();

	@Override
	public void onEnable() {
		saveDefaultConfig();
		build();
		registerCommands();
		registerListeners();
		startTasks();

		// Vault может включиться позже нас — регистрируемся через тик.
		if (getConfig().getBoolean("integration.vault", true)) {
			Bukkit.getScheduler().runTaskLater(this, () -> {
				vault = new VaultBridge(this, api);
				vault.register(getConfig().getString("integration.vault-priority", "Highest"));
			}, 1L);
		}

		getLogger().info("Валюта сезона готова: " + market.size() + " позиций курса, "
				+ accounts.count() + " счетов, стартовый капитал " + msg.money(accounts.startingBalance()) + ".");
	}

	@Override
	public void onDisable() {
		cancelTasks();
		if (vault != null) {
			vault.unregister();
			vault = null;
		}
		if (api != null) {
			Bukkit.getServicesManager().unregister(api);
		}
		saveAll(true);
		getLogger().info("Экономика сохранена.");
	}

	// ---------------------------------------------------------------- сборка

	private void build() {
		msg = new Msg(getConfig());

		accounts = new Accounts(this,
				getConfig().getDouble("economy.starting-balance", 20.0),
				getConfig().getDouble("economy.max-balance", 10000000.0),
				getConfig().getBoolean("storage.ledger", true),
				getConfig().getString("storage.ledger-dir", "ledger"));
		accounts.load();

		market = new Market(this);
		market.loadConfig(getConfig());
		market.loadState();
		market.tick();

		api = new EconomyApi(this, accounts, market, msg);
		api.readConfig(getConfig());
		Bukkit.getServicesManager().register(EconomyApi.class, api, this, ServicePriority.Normal);

		if (web == null) {
			web = new WebExport(this);
		}

		// Счета тем, кто уже онлайн (после /reload или горячей установки).
		Bukkit.getOnlinePlayers().forEach(player -> accounts.ensure(player.getUniqueId(), player.getName()));
	}

	private void registerCommands() {
		setExecutor("balance", new BalanceCommand(this));
		setExecutor("pay", new PayCommand(this));
		bank = new BankCommand(this);
		PluginCommand bankCommand = getCommand("bank");
		if (bankCommand != null) {
			bankCommand.setExecutor(bank);
			bankCommand.setTabCompleter(bank);
		}
		AdminCommand admin = new AdminCommand(this);
		PluginCommand adminCommand = getCommand("seco");
		if (adminCommand != null) {
			adminCommand.setExecutor(admin);
			adminCommand.setTabCompleter(admin);
		}
	}

	private void setExecutor(String name, org.bukkit.command.CommandExecutor executor) {
		PluginCommand command = getCommand(name);
		if (command != null) {
			command.setExecutor(executor);
			if (executor instanceof org.bukkit.command.TabCompleter completer) {
				command.setTabCompleter(completer);
			}
		} else {
			getLogger().warning("Команда не объявлена в plugin.yml: " + name);
		}
	}

	private void registerListeners() {
		getServer().getPluginManager().registerEvents(new EconListener(this), this);
		menu = new BankMenu(this);
		getServer().getPluginManager().registerEvents(menu, this);
	}

	private void startTasks() {
		long saveTicks = Math.max(20L, getConfig().getLong("storage.save-interval-seconds", 120L) * 20L);
		tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> saveAll(false), saveTicks, saveTicks));

		tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> {
			market.tick();
			if (market.isDirty()) {
				market.saveState();
			}
		}, 1200L, 1200L));

		if (getConfig().getBoolean("web.export", true)) {
			long webTicks = Math.max(200L, getConfig().getLong("web.interval-seconds", 60L) * 20L);
			tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> web.export(), 100L, webTicks));
		}
	}

	private void cancelTasks() {
		for (BukkitTask task : tasks) {
			task.cancel();
		}
		tasks.clear();
	}

	public void saveAll(boolean force) {
		if (accounts != null) {
			if (force) {
				accounts.saveNow();
			} else {
				accounts.saveAsync();
			}
		}
		if (market != null && (force || market.isDirty())) {
			market.saveState();
		}
	}

	/** Полный перезапуск конфига: цены, сообщения, комиссии, лимиты. */
	public void reloadEverything() {
		saveAll(true);
		cancelTasks();
		if (vault != null) {
			vault.unregister();
			vault = null;
		}
		if (api != null) {
			Bukkit.getServicesManager().unregister(api);
		}
		reloadConfig();
		build();
		startTasks();
		if (getConfig().getBoolean("integration.vault", true)) {
			vault = new VaultBridge(this, api);
			vault.register(getConfig().getString("integration.vault-priority", "Highest"));
		}
	}

	// ---------------------------------------------------------------- доступ

	public Msg msg() {
		return msg;
	}

	public Accounts accounts() {
		return accounts;
	}

	public Market market() {
		return market;
	}

	public EconomyApi api() {
		return api;
	}

	public BankMenu menu() {
		return menu;
	}

	public BankCommand bank() {
		return bank;
	}

	public WebExport web() {
		return web;
	}
}
