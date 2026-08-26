package ru.sepiolsmp.finale.phase;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.sepiolsmp.finale.SepiolFinale;
import ru.sepiolsmp.finale.util.Broadcast;
import ru.sepiolsmp.finale.util.CoreBridge;
import ru.sepiolsmp.finale.util.WorldSnapshot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Serdce finala: chasy, fazy i vse posledstviya.
 *
 * Tiker rabotaet 5 raz v sekundu (kazhdye 4 tika): budzhety razrusheniya
 * razmazyvayutsya po pyati porciyam, chtoby vmesto apokalipsisa ne poluchilsya friz.
 * Vse, chto plagin menyaet v mire, snachala sohranyaetsya v state.yml, chtoby
 * /finale abort vernul server v igrabelnoe sostoyanie odnoy komandoy.
 */
public final class FinaleEngine {

	private static final long PERIOD_TICKS = 4L;
	private static final int CALLS_PER_SECOND = 5;
	private static final int WEATHER_FOREVER_TICKS = 20 * 60 * 60 * 4;

	private final SepiolFinale plugin;
	private final Broadcast bc;
	private final Destroyer destroyer;
	private final Set<UUID> dead = new LinkedHashSet<>();
	private final Set<UUID> forced = new LinkedHashSet<>();

	private Phase phase = Phase.IDLE;
	private BukkitTask task;
	private long calls;
	private double timeScale = 1.0;
	private boolean dryRun;
	private boolean destroy = true;
	private boolean allowKill = true;
	private boolean allowShutdown = true;
	private int lastWaveSecond = -1;
	private List<Material> palette = List.of();
	private double carryCorrode;
	private double carryDissolve;
	private double carryTnt;
	private double carryBoom;
	private double carryLightning;

	public FinaleEngine(SepiolFinale plugin, Broadcast broadcast) {
		this.plugin = plugin;
		this.bc = broadcast;
		this.destroyer = new Destroyer(plugin.getLogger());
	}

	public Phase phase() {
		return phase;
	}

	public boolean dryRun() {
		return dryRun;
	}

	public boolean isDead(UUID id) {
		return dead.contains(id);
	}

	public int deadCount() {
		return dead.size();
	}

	public List<Player> alivePlayers() {
		List<Player> out = new ArrayList<>();
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (dead.contains(player.getUniqueId()) || player.isDead()) {
				continue;
			}
			if (player.getGameMode() == GameMode.SPECTATOR) {
				continue;
			}
			out.add(player);
		}
		return out;
	}

	public boolean noRespawn() {
		return phase.terminal() && phaseConfig(Phase.ASH).getBoolean("no-respawn", true);
	}

	public String timeLeftText() {
		if (!phase.running()) {
			return "-";
		}
		int total = phaseLength(phaseConfig(phase));
		return format(Math.max(0, total - elapsedSeconds()));
	}

	public List<String> statusLines() {
		List<String> lines = new ArrayList<>();
		lines.add("&8--- &4ФИНАЛ СЕЗОНА &8---");
		lines.add("&7Фаза: &f" + phase.title() + (dryRun ? " &8(репетиция)" : ""));
		if (phase.running()) {
			lines.add("&7До следующей фазы: &f" + timeLeftText());
		}
		lines.add("&7Живых: &f" + alivePlayers().size() + " &8| &7Мёртвых: &f" + dead.size());
		World world = plugin.world();
		if (world != null) {
			lines.add("&7Мир: &f" + world.getName()
					+ " &8| &7граница: &f" + Math.round(world.getWorldBorder().getSize()));
		}
		lines.add("&7Разрушение: &f" + onOff(destroy)
				+ " &8| &7добивание: &f" + onOff(allowKill)
				+ " &8| &7выключение: &f" + onOff(allowShutdown));
		return lines;
	}

	public boolean start(boolean rehearsal) {
		if (phase.running()) {
			return false;
		}
		World world = plugin.world();
		if (world == null) {
			plugin.getLogger().severe("Мир не найден - финал не запускаю.");
			return false;
		}
		applyMode(rehearsal);
		dead.clear();
		forced.clear();
		WorldSnapshot.capture(world, plugin.state().section("snapshot"));
		plugin.state().set("dry-run", rehearsal);
		plugin.state().set("dead", new ArrayList<String>());
		plugin.state().save();
		enter(Phase.OMENS);
		startTicker();
		return true;
	}

	public boolean resume() {
		Phase saved = Phase.fromKey(plugin.state().getString("phase", "idle"));
		if (!saved.running()) {
			return false;
		}
		applyMode(plugin.state().getBoolean("dry-run", false));
		dead.clear();
		for (String raw : plugin.state().getStringList("dead")) {
			try {
				dead.add(UUID.fromString(raw));
			} catch (IllegalArgumentException ignored) {
				plugin.getLogger().warning("Плохой uuid в state.yml: " + raw);
			}
		}
		phase = saved;
		calls = 0;
		lastWaveSecond = -1;
		resetCarry();
		cachePalette();
		startTicker();
		bc.chat(plugin.msg("resumed").replace("%phase%", saved.title()));
		return true;
	}

	public void jumpTo(Phase target) {
		if (target == null || target == Phase.IDLE) {
			return;
		}
		if (target == Phase.DONE) {
			finish();
			return;
		}
		if (!phase.running()) {
			World world = plugin.world();
			if (world != null && !plugin.state().has("snapshot")) {
				WorldSnapshot.capture(world, plugin.state().section("snapshot"));
			}
			applyMode(dryRun);
			startTicker();
		}
		enter(target);
	}

	public void advance() {
		if (phase == Phase.SILENCE) {
			finish();
			return;
		}
		if (!phase.running()) {
			return;
		}
		enter(phase.next());
	}

	public boolean abort() {
		if (phase == Phase.IDLE) {
			return false;
		}
		stopTicker();
		WorldSnapshot.restore(plugin.world(), plugin.state().existingSection("snapshot"));
		for (UUID id : forced) {
			Player player = Bukkit.getPlayer(id);
			if (player != null && player.getGameMode() == GameMode.SPECTATOR) {
				player.setGameMode(GameMode.SURVIVAL);
			}
		}
		forced.clear();
		dead.clear();
		phase = Phase.IDLE;
		plugin.state().wipe();
		bc.chat(plugin.msg("aborted"));
		return true;
	}

	public boolean restoreWorld() {
		ConfigurationSection snapshot = plugin.state().existingSection("snapshot");
		return snapshot != null && WorldSnapshot.restore(plugin.world(), snapshot);
	}

	public void stopOnDisable() {
		stopTicker();
	}

	public void markDead(Player player) {
		if (player == null || !dead.add(player.getUniqueId())) {
			return;
		}
		List<String> ids = new ArrayList<>();
		for (UUID id : dead) {
			ids.add(id.toString());
		}
		plugin.state().set("dead", ids);
		plugin.state().save();
		int alive = alivePlayers().size();
		bc.chat(plugin.msg("death-broadcast")
				.replace("%player%", player.getName())
				.replace("%alive%", String.valueOf(alive)));
		if (phase == Phase.ASH && alive == 0) {
			Bukkit.getScheduler().runTask(plugin, this::advance);
		}
	}

	public void forceSpectator(Player player) {
		if (player == null) {
			return;
		}
		forced.add(player.getUniqueId());
		dead.add(player.getUniqueId());
		if (player.getGameMode() != GameMode.SPECTATOR) {
			player.setGameMode(GameMode.SPECTATOR);
		}
	}

	public void recount() {
		if (phase == Phase.ASH && alivePlayers().isEmpty()) {
			Bukkit.getScheduler().runTask(plugin, this::advance);
		}
	}

	public Location watchPoint() {
		World world = plugin.world();
		if (world == null) {
			return null;
		}
		Location center = plugin.center(world).clone();
		center.setY(Math.min(world.getMaxHeight() - 2, world.getHighestBlockYAt(center) + 40));
		return center;
	}

	private void tick() {
		if (!phase.running()) {
			stopTicker();
			return;
		}
		World world = plugin.world();
		if (world == null) {
			return;
		}
		calls++;
		ConfigurationSection cfg = phaseConfig(phase);
		List<Player> victims = alivePlayers();
		if (destroy) {
			subSecondEffects(world, cfg, victims);
		}
		if (calls % CALLS_PER_SECOND != 0) {
			return;
		}
		int seconds = elapsedSeconds();
		int total = phaseLength(cfg);
		Phase before = phase;
		perSecond(world, cfg, victims, seconds, total);
		if (phase == before && phase.running() && seconds >= total) {
			advance();
		}
	}

	private void subSecondEffects(World world, ConfigurationSection cfg, List<Player> victims) {
		if (victims.isEmpty()) {
			return;
		}
		switch (phase) {
			case OMENS -> lightning(world, victims, cfg);
			case RIFT -> {
				carryCorrode += cfg.getDouble("corrosion-per-second", 0.0) / CALLS_PER_SECOND;
				int budget = (int) carryCorrode;
				if (budget > 0) {
					carryCorrode -= budget;
					destroyer.corrode(world, victims, budget, cfg.getInt("corrosion-radius", 128), palette);
				}
				lightning(world, victims, cfg);
			}
			case CONVERGENCE -> {
				carryDissolve += cfg.getDouble("dissolve-per-second", 0.0) / CALLS_PER_SECOND;
				int dissolveBudget = (int) carryDissolve;
				if (dissolveBudget > 0) {
					carryDissolve -= dissolveBudget;
					destroyer.dissolve(world, victims, dissolveBudget,
							cfg.getInt("dissolve-radius", 96), cfg.getInt("dissolve-top-layers", 6));
				}
				carryTnt += cfg.getDouble("tnt-per-second", 0.0) / CALLS_PER_SECOND;
				int tntBudget = (int) carryTnt;
				if (tntBudget > 0) {
					carryTnt -= tntBudget;
					destroyer.rainTnt(world, victims, tntBudget);
				}
				carryBoom += cfg.getDouble("explosions-per-second", 0.0) / CALLS_PER_SECOND;
				int boomBudget = (int) carryBoom;
				if (boomBudget > 0) {
					carryBoom -= boomBudget;
					destroyer.explode(world, victims, boomBudget,
							(float) cfg.getDouble("explosion-power", 3.0), cfg.getBoolean("set-fire", true));
				}
				lightning(world, victims, cfg);
			}
			default -> {
				// в остальных фазах здесь делать нечего
			}
		}
	}

	private void lightning(World world, List<Player> victims, ConfigurationSection cfg) {
		carryLightning += cfg.getDouble("lightning-per-minute", 0.0) / 60.0 / CALLS_PER_SECOND;
		int strikes = (int) carryLightning;
		if (strikes <= 0) {
			return;
		}
		carryLightning -= strikes;
		destroyer.lightning(world, victims, strikes, 48, phase == Phase.CONVERGENCE);
	}

	private void perSecond(World world, ConfigurationSection cfg, List<Player> victims, int seconds, int total) {
		int left = Math.max(0, total - seconds);
		int announceEvery = scaled(cfg.getInt("announce-every", 60));
		if (announceEvery > 0 && seconds > 0 && seconds % announceEvery == 0 && left > 0) {
			bc.chat(plugin.msg("phase-progress")
					.replace("%phase%", phase.title())
					.replace("%time%", format(left)));
		}
		if (phase != Phase.SILENCE) {
			bc.actionBar("&4" + phase.title() + " &8| &f" + format(left));
		}
		switch (phase) {
			case RIFT -> mobWaves(world, cfg, victims, seconds);
			case CONVERGENCE -> {
				if (cfg.getBoolean("pull-players", true)) {
					pull(world);
				}
			}
			case ASH -> ashSecond(cfg, victims, seconds);
			case SILENCE -> silenceSecond(cfg, left);
			default -> {
				// в остальных фазах здесь делать нечего
			}
		}
	}

	private void mobWaves(World world, ConfigurationSection cfg, List<Player> victims, int seconds) {
		ConfigurationSection waves = cfg.getConfigurationSection("mob-waves");
		if (waves == null || !waves.getBoolean("enabled", true) || victims.isEmpty()) {
			return;
		}
		int every = Math.max(1, scaled(waves.getInt("every-seconds", 60)));
		if (seconds <= 0 || seconds % every != 0 || seconds == lastWaveSecond) {
			return;
		}
		lastWaveSecond = seconds;
		destroyer.spawnWave(world, victims,
				waves.getInt("per-player", 4), waves.getInt("radius", 24), waves.getStringList("types"));
	}

	private void pull(World world) {
		WorldBorder border = world.getWorldBorder();
		Location center = border.getCenter();
		double limit = border.getSize() / 2.0 + 32.0;
		for (Player player : alivePlayers()) {
			Location location = player.getLocation();
			if (Math.abs(location.getX() - center.getX()) < limit
					&& Math.abs(location.getZ() - center.getZ()) < limit) {
				continue;
			}
			Location target = center.clone();
			target.setY(Math.max(world.getMinHeight() + 2, world.getHighestBlockYAt(target) + 1));
			player.teleport(target);
			bc.to(player, plugin.msg("pulled"));
		}
	}

	private void ashSecond(ConfigurationSection cfg, List<Player> victims, int seconds) {
		if (allowKill) {
			double damage = cfg.getDouble("damage-per-second", 1.0);
			int killAfter = scaled(cfg.getInt("kill-remaining-after", 120));
			boolean finishThem = killAfter > 0 && seconds >= killAfter;
			for (Player player : victims) {
				if (finishThem) {
					kill(player);
				} else if (damage > 0) {
					player.damage(damage);
				}
			}
		}
		if (alivePlayers().isEmpty()) {
			advance();
		}
	}

	private void silenceSecond(ConfigurationSection cfg, int left) {
		if (!allowShutdown || !cfg.getBoolean("shutdown", true)) {
			return;
		}
		if (left > 0 && (left == 30 || left == 10 || left <= 5)) {
			bc.chat(plugin.msg("countdown").replace("%seconds%", String.valueOf(left)));
		}
	}

	private void enter(Phase target) {
		phase = target;
		calls = 0;
		lastWaveSecond = -1;
		resetCarry();
		plugin.state().set("phase", target.key());
		plugin.state().set("phase-started-at", System.currentTimeMillis());
		plugin.state().save();
		ConfigurationSection cfg = phaseConfig(target);
		bc.title(plugin.text(target.key() + "-title"), plugin.text(target.key() + "-subtitle"), 10, 70, 20);
		bc.chat(plugin.msg(target.key() + "-chat").replace("%season%", plugin.seasonName()));
		bc.sound(cfg.getString("sound", ""), 1.0f, 0.7f);
		World world = plugin.world();
		if (world != null) {
			switch (target) {
				case OMENS -> beginOmens(world, cfg);
				case RIFT -> beginRift(world, cfg);
				case CONVERGENCE -> beginConvergence(world, cfg);
				case ASH -> beginAsh(world);
				case SILENCE -> beginSilence(world, cfg);
				default -> {
					// IDLE/DONE сюда не попадают
				}
			}
		}
		runPhaseCommands(cfg);
		plugin.getLogger().info("Фаза " + target.title() + ": " + phaseLength(cfg) + " сек"
				+ (dryRun ? " (репетиция)" : ""));
	}

	private void beginOmens(World world, ConfigurationSection cfg) {
		world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
		world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
		long freeze = cfg.getLong("freeze-time", 18000L);
		if (freeze >= 0) {
			world.setTime(freeze);
		}
		if (cfg.getBoolean("storm", true)) {
			world.setStorm(true);
			world.setThundering(true);
			world.setWeatherDuration(WEATHER_FOREVER_TICKS);
			world.setThunderDuration(WEATHER_FOREVER_TICKS);
		}
	}

	private void beginRift(World world, ConfigurationSection cfg) {
		cachePalette();
		if (cfg.getBoolean("force-pvp", true)) {
			world.setPVP(true);
		}
		world.setDifficulty(Difficulty.HARD);
		shrinkBorder(world, cfg.getDouble("border-target", 2000.0), cfg.getInt("border-shrink-seconds", 540));
	}

	private void beginConvergence(World world, ConfigurationSection cfg) {
		shrinkBorder(world, cfg.getDouble("border-target", 96.0), cfg.getInt("border-shrink-seconds", 480));
		world.setGameRule(GameRule.NATURAL_REGENERATION, false);
		world.setGameRule(GameRule.DO_FIRE_TICK, true);
	}

	private void beginAsh(World world) {
		world.setGameRule(GameRule.KEEP_INVENTORY, false);
		world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, false);
		world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
		world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, true);
	}

	private void beginSilence(World world, ConfigurationSection cfg) {
		List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
		for (Player player : online) {
			forceSpectator(player);
		}
		if (cfg.getBoolean("snapshot-via-sepiolcore", true)) {
			CoreBridge.snapshot(plugin.getLogger());
		}
		if (cfg.getBoolean("save-world", true)) {
			Bukkit.savePlayers();
			world.save();
		}
	}

	private void finish() {
		ConfigurationSection cfg = phaseConfig(Phase.SILENCE);
		phase = Phase.DONE;
		stopTicker();
		plugin.state().set("phase", Phase.DONE.key());
		plugin.state().set("finished-at", System.currentTimeMillis());
		plugin.state().save();
		bc.chat(plugin.msg("finished").replace("%season%", plugin.seasonName()));
		writeMarker(cfg.getString("marker-file", "FINALE_DONE"));
		if (cfg.getBoolean("kick-players", true)) {
			String farewell = plugin.text("silence-chat").replace("%season%", plugin.seasonName());
			List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
			for (Player player : online) {
				player.kick(Broadcast.text(farewell));
			}
		}
		if (allowShutdown && cfg.getBoolean("shutdown", true)) {
			plugin.getLogger().warning("Финал завершён. Сервер выключается.");
			Bukkit.getScheduler().runTaskLater(plugin, Bukkit::shutdown, 40L);
		} else {
			plugin.getLogger().info("Финал завершён без выключения сервера.");
		}
	}

	private void writeMarker(String name) {
		if (name == null || name.isBlank()) {
			return;
		}
		try {
			File marker = new File(Bukkit.getWorldContainer(), name);
			String body = "sepiolSMP " + plugin.seasonName() + " finished at "
					+ LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + System.lineSeparator();
			Files.writeString(marker.toPath(), body, StandardCharsets.UTF_8);
			plugin.getLogger().info("Метка финала: " + marker.getAbsolutePath());
		} catch (IOException exception) {
			plugin.getLogger().warning("Не смог записать метку финала: " + exception.getMessage());
		}
	}

	private void shrinkBorder(World world, double target, int seconds) {
		WorldBorder border = world.getWorldBorder();
		Location center = plugin.center(world);
		border.setCenter(center.getX(), center.getZ());
		border.setWarningDistance(64);
		border.setWarningTime(20);
		double size = Math.max(8.0, target);
		if (border.getSize() > size) {
			border.setSize(size, Math.max(1L, scaled(seconds)));
		}
	}

	private void kill(Player player) {
		try {
			if (player.getGameMode() != GameMode.SURVIVAL) {
				player.setGameMode(GameMode.SURVIVAL);
			}
			player.setHealth(0.0);
		} catch (RuntimeException exception) {
			plugin.getLogger().warning("Не смог добить " + player.getName() + ": " + exception.getMessage());
		}
	}

	private void runPhaseCommands(ConfigurationSection cfg) {
		List<String> commands = cfg.getStringList("commands");
		if (commands.isEmpty()) {
			return;
		}
		for (String raw : commands) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			String command = raw.startsWith("/") ? raw.substring(1) : raw;
			if (command.contains("%player%")) {
				for (Player player : Bukkit.getOnlinePlayers()) {
					dispatch(command.replace("%player%", player.getName()));
				}
			} else {
				dispatch(command);
			}
		}
	}

	private void dispatch(String command) {
		try {
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
		} catch (RuntimeException exception) {
			plugin.getLogger().warning("Команда фазы упала: " + command + " (" + exception.getMessage() + ")");
		}
	}

	private void applyMode(boolean rehearsal) {
		ConfigurationSection dry = plugin.getConfig().getConfigurationSection("dry-run");
		this.dryRun = rehearsal;
		double scale = dry != null ? dry.getDouble("time-scale", 0.1) : 0.1;
		this.timeScale = rehearsal ? Math.max(0.01, scale) : 1.0;
		this.destroy = !rehearsal || (dry != null && dry.getBoolean("destroy", false));
		this.allowKill = !rehearsal || (dry != null && dry.getBoolean("kill", false));
		this.allowShutdown = !rehearsal || (dry != null && dry.getBoolean("shutdown", false));
	}

	private void cachePalette() {
		palette = Destroyer.palette(phaseConfig(Phase.RIFT).getStringList("corrosion-palette"), plugin.getLogger());
	}

	private ConfigurationSection phaseConfig(Phase target) {
		ConfigurationSection section = plugin.getConfig().getConfigurationSection("phases." + target.key());
		return section != null ? section : new MemoryConfiguration();
	}

	private int phaseLength(ConfigurationSection cfg) {
		return scaled(cfg.getInt("seconds", 300));
	}

	private int scaled(int seconds) {
		return seconds <= 0 ? seconds : (int) Math.max(1L, Math.round(seconds * timeScale));
	}

	private int elapsedSeconds() {
		return (int) (calls / CALLS_PER_SECOND);
	}

	private void resetCarry() {
		carryCorrode = 0.0;
		carryDissolve = 0.0;
		carryTnt = 0.0;
		carryBoom = 0.0;
		carryLightning = 0.0;
	}

	private void startTicker() {
		stopTicker();
		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
	}

	private void stopTicker() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	private static String onOff(boolean value) {
		return value ? "вкл" : "выкл";
	}

	private static String format(int seconds) {
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
