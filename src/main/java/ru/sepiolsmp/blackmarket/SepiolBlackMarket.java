package ru.sepiolsmp.blackmarket;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.sepiolsmp.blackmarket.cmd.BlackMarketCommand;
import ru.sepiolsmp.blackmarket.econ.EconomyLink;
import ru.sepiolsmp.blackmarket.gui.ShopMenu;
import ru.sepiolsmp.blackmarket.market.Catalog;
import ru.sepiolsmp.blackmarket.market.Trader;
import ru.sepiolsmp.blackmarket.market.Wanted;
import ru.sepiolsmp.blackmarket.util.DealLog;
import ru.sepiolsmp.blackmarket.util.Msg;
import ru.sepiolsmp.blackmarket.web.WebExport;

/** Чёрный рынок sepiolSMP: барыга, контрабанда, розыск и облавы. */
public final class SepiolBlackMarket extends JavaPlugin {

	private Msg msg;
	private EconomyLink economy;
	private Catalog catalog;
	private Trader trader;
	private Wanted wanted;
	private DealLog deals;
	private ShopMenu menu;
	private WebExport web;
	private final List<BukkitTask> tasks = new ArrayList<>();

	@Override
	public void onEnable() {
		saveDefaultConfig();
		msg = new Msg(getConfig());
		economy = new EconomyLink(this);
		economy.setPreferred(getConfig().getString("economy.mode", "auto"));
		catalog = new Catalog(this, msg);
		trader = new Trader(this, msg, catalog);
		wanted = new Wanted(this, msg);
		deals = new DealLog(this);
		menu = new ShopMenu(this);
		web = new WebExport(this);

		applyConfig();
		catalog.loadState();
		trader.loadState();
		wanted.loadState();

		getServer().getPluginManager().registerEvents(menu, this);
		getServer().getPluginManager().registerEvents(new BmListener(this), this);

		BlackMarketCommand executor = new BlackMarketCommand(this);
		PluginCommand command = getCommand("blackmarket");
		if (command != null) {
			command.setExecutor(executor);
			command.setTabCompleter(executor);
		}

		startTasks();
		getLogger().info("Чёрный рынок открыт: лотов " + catalog.size() + ", экономика " + economy.modeName());
	}

	@Override
	public void onDisable() {
		stopTasks();
		if (catalog != null) {
			saveAll(true);
		}
		getLogger().info("Рынок закрыт");
	}

	// ------------------------------------------------------------------
	// доступ
	// ------------------------------------------------------------------

	public Msg messages() {
		return msg;
	}

	public EconomyLink economy() {
		return economy;
	}

	public Catalog catalog() {
		return catalog;
	}

	public Trader trader() {
		return trader;
	}

	public Wanted wanted() {
		return wanted;
	}

	public DealLog deals() {
		return deals;
	}

	public ShopMenu menu() {
		return menu;
	}

	public WebExport web() {
		return web;
	}

	// ------------------------------------------------------------------
	// конфиг и задачи
	// ------------------------------------------------------------------

	private void applyConfig() {
		catalog.loadConfig(getConfig());
		trader.loadConfig(getConfig());
		wanted.loadConfig(getConfig());
		deals.loadConfig(getConfig());
		menu.loadConfig(getConfig());
		web.loadConfig(getConfig());
	}

	public void reloadEverything() {
		reloadConfig();
		msg.reload(getConfig());
		economy.setPreferred(getConfig().getString("economy.mode", "auto"));
		applyConfig();
		stopTasks();
		startTasks();
		getLogger().info("Конфиг перечитан: лотов " + catalog.size());
	}

	public void saveAll(boolean force) {
		if (force || catalog.isDirty()) {
			catalog.saveState();
		}
		if (force || trader.isDirty()) {
			trader.saveState();
		}
		if (force || wanted.isDirty()) {
			wanted.saveState();
		}
	}

	private void startTasks() {
		// основной тик: барыга, розыск, остывание цен
		tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> {
			trader.tick();
			wanted.tick();
			catalog.tick();
		}, 100L, 100L));

		// автоматические досмотры в зонах
		if (getConfig().getBoolean("scan.enabled", true)) {
			long period = Math.max(5L, getConfig().getLong("scan.interval-seconds", 20L)) * 20L;
			tasks.add(Bukkit.getScheduler().runTaskTimer(this, this::runZoneScan, period, period));
		}

		// сохранение
		long saveTicks = Math.max(30L, getConfig().getLong("storage.save-interval-seconds", 120L)) * 20L;
		tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> saveAll(false), saveTicks, saveTicks));

		// выгрузка для веб-профиля
		if (web.enabled()) {
			long webTicks = Math.max(15L, getConfig().getLong("web.interval-seconds", 60L)) * 20L;
			tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> web.export(), webTicks, webTicks));
		}
	}

	private void stopTasks() {
		for (BukkitTask task : tasks) {
			try {
				task.cancel();
			} catch (Throwable ignored) {
				// задача уже снята
			}
		}
		tasks.clear();
	}

	// ------------------------------------------------------------------
	// автодосмотр
	// ------------------------------------------------------------------

	/** В охраняемых зонах контрабанда в инвентаре сама ставит тебя в розыск. */
	private void runZoneScan() {
		String bypass = getConfig().getString("scan.bypass-permission", "sepiolblackmarket.staff");
		boolean autoWanted = getConfig().getBoolean("scan.auto-wanted", true);
		boolean confiscate = getConfig().getBoolean("scan.confiscate", false);
		boolean notifyStaff = getConfig().getBoolean("scan.notify-staff", true);

		for (Player player : Bukkit.getOnlinePlayers()) {
			if (bypass != null && !bypass.isBlank() && player.hasPermission(bypass)) {
				continue;
			}
			if (wanted.isWanted(player.getUniqueId())) {
				continue;
			}
			if (!inZone(player.getLocation())) {
				continue;
			}
			int items = catalog.countContraband(player);
			if (items <= 0) {
				continue;
			}
			msg.send(player, "scan-zone-warning", "%count%", items);
			int taken = confiscate ? catalog.confiscate(player) : 0;
			if (autoWanted) {
				int minutes = wanted.mark(player, items);
				wanted.announceCatch(player.getName(), items);
				deals.wanted(player.getName(), minutes, "автодосмотр");
			}
			if (notifyStaff) {
				msg.broadcastPerm("sepiolblackmarket.staff", "scan-dirty",
						"%player%", player.getName(), "%count%", items);
			}
			deals.check("auto", player.getName(), confiscate ? taken : items, confiscate);
		}
	}

	private boolean inZone(Location where) {
		World world = where.getWorld();
		if (world == null) {
			return false;
		}
		if (getConfig().getBoolean("scan.use-world-spawn", true)) {
			double radius = getConfig().getDouble("scan.radius", 120.0D);
			Location spawn = world.getSpawnLocation();
			if (flatDistance(where, spawn) <= radius) {
				return true;
			}
		}
		for (String raw : getConfig().getStringList("scan.zones")) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			String[] parts = raw.split(",");
			if (parts.length < 4) {
				continue;
			}
			try {
				String worldName = parts[0].trim();
				if (!worldName.equalsIgnoreCase(world.getName())
						&& !"*".equals(worldName)
						&& !"auto".equalsIgnoreCase(worldName)) {
					continue;
				}
				double zx = Double.parseDouble(parts[1].trim());
				double zz = Double.parseDouble(parts[2].trim());
				double radius = Double.parseDouble(parts[3].trim());
				double dx = where.getX() - zx;
				double dz = where.getZ() - zz;
				if (Math.sqrt(dx * dx + dz * dz) <= radius) {
					return true;
				}
			} catch (NumberFormatException ignored) {
				getLogger().warning("Непонятная зона в scan.zones: " + raw.toLowerCase(Locale.ROOT));
			}
		}
		return false;
	}

	private static double flatDistance(Location first, Location second) {
		double dx = first.getX() - second.getX();
		double dz = first.getZ() - second.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
