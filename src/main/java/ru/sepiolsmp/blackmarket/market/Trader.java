package ru.sepiolsmp.blackmarket.market;

import java.io.File;
import java.io.FileWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.sepiolsmp.blackmarket.util.Msg;

/**
 * Скрытый торговец: раз в сутки встаёт в случайной точке мира.
 * Координаты - товар: их покупают за сепиолы или ждут, когда адрес сльют всем.
 */
public final class Trader {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final JavaPlugin plugin;
	private final Msg msg;
	private final Catalog catalog;
	private final NamespacedKey traderKey;
	private final File stateFile;

	// конфиг
	private boolean enabled = true;
	private String worldName = "auto";
	private int radius = 5200;
	private int minDistance = 900;
	private double intervalHours = 24.0D;
	private double stayHours = 20.0D;
	private int firstDelayMinutes = 15;
	private double revealAfterHours = 6.0D;
	private double coordsPrice = 150.0D;
	private double interactionRadius = 6.0D;
	private boolean remoteShop;
	private boolean announceArrival = true;
	private boolean announceDeparture = true;
	private boolean announceReveal = true;
	private String traderName = "&5Барыга";
	private String entityName = "WANDERING_TRADER";
	private boolean restockOnArrival = true;
	private boolean safeBlocksOnly = true;
	private boolean effects = true;
	private String particleName = "WITCH";
	private String soundOpen = "entity.wandering_trader.yes";
	private String soundBuy = "entity.experience_orb.pickup";
	private String soundDeny = "entity.villager.no";

	// состояние
	private boolean active;
	private UUID entityId;
	private String activeWorld = "";
	private int x;
	private int y;
	private int z;
	private long arrivedAt;
	private long leavesAt;
	private long nextArrival;
	private boolean revealed;
	private final Set<UUID> buyers = new HashSet<>();
	private volatile boolean dirty;

	public Trader(JavaPlugin plugin, Msg msg, Catalog catalog) {
		this.plugin = plugin;
		this.msg = msg;
		this.catalog = catalog;
		this.traderKey = new NamespacedKey(plugin, "trader");
		this.stateFile = new File(plugin.getDataFolder(), "trader.json");
	}

	// ------------------------------------------------------------------
	// конфиг и состояние
	// ------------------------------------------------------------------

	public void loadConfig(FileConfiguration cfg) {
		enabled = cfg.getBoolean("trader.enabled", true);
		worldName = cfg.getString("trader.world", "auto");
		radius = Math.max(64, cfg.getInt("trader.radius", 5200));
		minDistance = Math.max(0, cfg.getInt("trader.min-distance", 900));
		intervalHours = Math.max(0.05D, cfg.getDouble("trader.interval-hours", 24.0D));
		stayHours = Math.max(0.05D, cfg.getDouble("trader.stay-hours", 20.0D));
		firstDelayMinutes = Math.max(0, cfg.getInt("trader.first-delay-minutes", 15));
		revealAfterHours = Math.max(0.0D, cfg.getDouble("trader.reveal-after-hours", 6.0D));
		coordsPrice = Math.max(0.0D, cfg.getDouble("trader.coords-price", 150.0D));
		interactionRadius = Math.max(1.0D, cfg.getDouble("trader.interaction-radius", 6.0D));
		remoteShop = cfg.getBoolean("trader.remote-shop", false);
		announceArrival = cfg.getBoolean("trader.announce-arrival", true);
		announceDeparture = cfg.getBoolean("trader.announce-departure", true);
		announceReveal = cfg.getBoolean("trader.announce-reveal", true);
		traderName = cfg.getString("trader.name", "&5Барыга");
		entityName = cfg.getString("trader.entity", "WANDERING_TRADER");
		restockOnArrival = cfg.getBoolean("trader.restock-on-arrival", true);
		safeBlocksOnly = cfg.getBoolean("trader.safe-blocks-only", true);
		effects = cfg.getBoolean("trader.effects", true);
		particleName = cfg.getString("trader.particle", "WITCH");
		soundOpen = cfg.getString("trader.sound-open", "entity.wandering_trader.yes");
		soundBuy = cfg.getString("trader.sound-buy", "entity.experience_orb.pickup");
		soundDeny = cfg.getString("trader.sound-deny", "entity.villager.no");
	}

	public void loadState() {
		buyers.clear();
		active = false;
		entityId = null;
		nextArrival = System.currentTimeMillis() + firstDelayMinutes * 60_000L;
		if (!stateFile.isFile()) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(stateFile.toPath(), StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			active = root.has("active") && root.get("active").getAsBoolean();
			activeWorld = root.has("world") ? root.get("world").getAsString() : "";
			x = root.has("x") ? root.get("x").getAsInt() : 0;
			y = root.has("y") ? root.get("y").getAsInt() : 0;
			z = root.has("z") ? root.get("z").getAsInt() : 0;
			arrivedAt = root.has("arrivedAt") ? root.get("arrivedAt").getAsLong() : 0L;
			leavesAt = root.has("leavesAt") ? root.get("leavesAt").getAsLong() : 0L;
			nextArrival = root.has("nextArrival") ? root.get("nextArrival").getAsLong() : nextArrival;
			revealed = root.has("revealed") && root.get("revealed").getAsBoolean();
			if (root.has("entity") && !root.get("entity").isJsonNull()) {
				try {
					entityId = UUID.fromString(root.get("entity").getAsString());
				} catch (IllegalArgumentException ignored) {
					entityId = null;
				}
			}
			if (root.has("buyers")) {
				JsonArray array = root.getAsJsonArray("buyers");
				for (JsonElement element : array) {
					try {
						buyers.add(UUID.fromString(element.getAsString()));
					} catch (IllegalArgumentException ignored) {
						// мусор в файле просто пропускаем
					}
				}
			}
		} catch (Exception error) {
			plugin.getLogger().log(Level.WARNING, "Не смог прочитать trader.json", error);
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
			root.addProperty("active", active);
			root.addProperty("world", activeWorld);
			root.addProperty("x", x);
			root.addProperty("y", y);
			root.addProperty("z", z);
			root.addProperty("arrivedAt", arrivedAt);
			root.addProperty("leavesAt", leavesAt);
			root.addProperty("nextArrival", nextArrival);
			root.addProperty("revealed", revealed);
			root.addProperty("entity", entityId == null ? null : entityId.toString());
			JsonArray array = new JsonArray();
			for (UUID id : buyers) {
				array.add(id.toString());
			}
			root.add("buyers", array);

			File temp = new File(stateFile.getParentFile(), stateFile.getName() + ".tmp");
			try (FileWriter writer = new FileWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
			Files.move(temp.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			dirty = false;
		} catch (Exception error) {
			plugin.getLogger().log(Level.WARNING, "Не смог сохранить trader.json", error);
		}
	}

	public boolean isDirty() {
		return dirty;
	}

	// ------------------------------------------------------------------
	// геттеры
	// ------------------------------------------------------------------

	public boolean enabled() {
		return enabled;
	}

	public boolean active() {
		return active;
	}

	public boolean revealed() {
		return revealed;
	}

	public double coordsPrice() {
		return coordsPrice;
	}

	public double interactionRadius() {
		return interactionRadius;
	}

	public boolean remoteShop() {
		return remoteShop;
	}

	public long leavesAt() {
		return leavesAt;
	}

	public long nextArrival() {
		return nextArrival;
	}

	public long leftMillis() {
		return Math.max(0L, leavesAt - System.currentTimeMillis());
	}

	public long untilArrival() {
		return Math.max(0L, nextArrival - System.currentTimeMillis());
	}

	public String soundOpen() {
		return soundOpen;
	}

	public String soundBuy() {
		return soundBuy;
	}

	public String soundDeny() {
		return soundDeny;
	}

	public Location location() {
		if (!active) {
			return null;
		}
		World world = Bukkit.getWorld(activeWorld);
		if (world == null) {
			return null;
		}
		return new Location(world, x + 0.5D, y, z + 0.5D);
	}

	public String coordsText() {
		if (!active) {
			return "-";
		}
		return x + ", " + y + ", " + z;
	}

	public boolean knows(UUID player) {
		return revealed || (player != null && buyers.contains(player));
	}

	public void grantCoords(UUID player) {
		if (player != null && buyers.add(player)) {
			dirty = true;
		}
	}

	public int buyersCount() {
		return buyers.size();
	}

	public boolean nearby(Player player) {
		if (!active || player == null) {
			return false;
		}
		if (remoteShop) {
			return true;
		}
		Location spot = location();
		if (spot == null || player.getWorld() != spot.getWorld()) {
			return false;
		}
		return player.getLocation().distance(spot) <= interactionRadius;
	}

	public boolean isTraderEntity(Entity entity) {
		if (entity == null) {
			return false;
		}
		if (entityId != null && entityId.equals(entity.getUniqueId())) {
			return true;
		}
		return entity.getPersistentDataContainer().has(traderKey, PersistentDataType.BYTE);
	}

	// ------------------------------------------------------------------
	// жизненный цикл
	// ------------------------------------------------------------------

	/** Вызывается задачей плагина раз в несколько секунд. */
	public void tick() {
		if (!enabled) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!active) {
			if (now >= nextArrival) {
				if (!spawn(null)) {
					// место не нашлось - повторим через пару минут
					nextArrival = now + 120_000L;
					dirty = true;
				}
			}
			return;
		}
		if (now >= leavesAt) {
			despawn(true);
			return;
		}
		if (!revealed && revealAfterHours > 0.0D && now >= arrivedAt + (long) (revealAfterHours * 3_600_000.0D)) {
			revealed = true;
			dirty = true;
			if (announceReveal) {
				msg.broadcast("trader-revealed", "%coords%", coordsText());
			}
		}
		keepAlive();
		showEffects();
	}

	/** Если сущность пропала в загруженном чанке - ставим заново на том же месте. */
	private void keepAlive() {
		Location spot = location();
		if (spot == null) {
			return;
		}
		World world = spot.getWorld();
		if (world == null || !world.isChunkLoaded(spot.getBlockX() >> 4, spot.getBlockZ() >> 4)) {
			return;
		}
		Entity entity = entityId == null ? null : Bukkit.getEntity(entityId);
		if (entity != null && entity.isValid()) {
			return;
		}
		spawnEntity(spot);
	}

	private void showEffects() {
		if (!effects) {
			return;
		}
		Location spot = location();
		if (spot == null) {
			return;
		}
		World world = spot.getWorld();
		if (world == null || !world.isChunkLoaded(spot.getBlockX() >> 4, spot.getBlockZ() >> 4)) {
			return;
		}
		Particle particle = particle();
		if (particle == null) {
			return;
		}
		try {
			world.spawnParticle(particle, spot.clone().add(0.0D, 1.2D, 0.0D), 12, 0.5D, 0.6D, 0.5D, 0.01D);
		} catch (Throwable ignored) {
			effects = false;
		}
	}

	private Particle particle() {
		try {
			return Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
		} catch (Throwable ignored) {
			return null;
		}
	}

	/** Поставить торговца. forced = админская точка или null для случайной. */
	public boolean spawn(Location forced) {
		World world = forced != null ? forced.getWorld() : resolveWorld();
		if (world == null) {
			return false;
		}
		Location spot = forced != null ? forced : findSpot(world);
		if (spot == null) {
			return false;
		}
		removeEntity();
		long now = System.currentTimeMillis();
		activeWorld = world.getName();
		x = spot.getBlockX();
		y = spot.getBlockY();
		z = spot.getBlockZ();
		active = true;
		revealed = false;
		buyers.clear();
		arrivedAt = now;
		leavesAt = now + (long) (stayHours * 3_600_000.0D);
		nextArrival = now + (long) (intervalHours * 3_600_000.0D);
		dirty = true;
		spawnEntity(location());
		if (restockOnArrival) {
			catalog.restock();
		}
		if (announceArrival) {
			msg.broadcast("trader-arrived", "%price%", msg.money(coordsPrice));
		}
		plugin.getLogger().info("Торговец приехал: " + activeWorld + " " + coordsText());
		return true;
	}

	public void despawn(boolean announce) {
		boolean was = active;
		removeEntity();
		active = false;
		revealed = false;
		buyers.clear();
		long now = System.currentTimeMillis();
		if (nextArrival <= now) {
			nextArrival = now + (long) (intervalHours * 3_600_000.0D);
		}
		dirty = true;
		if (was && announce && announceDeparture) {
			msg.broadcast("trader-left", "%next%", Msg.duration(untilArrival()));
		}
	}

	private void spawnEntity(Location spot) {
		if (spot == null || spot.getWorld() == null) {
			return;
		}
		try {
			Entity entity = spot.getWorld().spawnEntity(spot, entityType());
			entity.setPersistent(true);
			entity.setInvulnerable(true);
			entity.setSilent(true);
			entity.setCustomNameVisible(true);
			entity.customName(Msg.text(traderName));
			entity.getPersistentDataContainer().set(traderKey, PersistentDataType.BYTE, (byte) 1);
			if (entity instanceof LivingEntity living) {
				living.setAI(false);
				living.setRemoveWhenFarAway(false);
				living.setCanPickupItems(false);
				living.setCollidable(false);
			}
			if (entity instanceof Mob mob) {
				mob.setAware(false);
			}
			if (entity instanceof WanderingTrader wandering) {
				wandering.setDespawnDelay(Integer.MAX_VALUE);
			}
			entityId = entity.getUniqueId();
			dirty = true;
		} catch (Throwable error) {
			plugin.getLogger().log(Level.WARNING, "Не смог поставить торговца", error);
		}
	}

	private void removeEntity() {
		if (entityId == null) {
			return;
		}
		try {
			Entity entity = Bukkit.getEntity(entityId);
			if (entity != null) {
				entity.remove();
			}
		} catch (Throwable ignored) {
			// сущность уже уехала
		}
		entityId = null;
		dirty = true;
	}

	private EntityType entityType() {
		try {
			return EntityType.valueOf(entityName.toUpperCase(Locale.ROOT));
		} catch (Throwable ignored) {
			return EntityType.WANDERING_TRADER;
		}
	}

	private World resolveWorld() {
		if (worldName != null && !worldName.isBlank() && !"auto".equalsIgnoreCase(worldName)) {
			World world = Bukkit.getWorld(worldName);
			if (world != null) {
				return world;
			}
			plugin.getLogger().warning("Мир " + worldName + " не найден, беру первый");
		}
		return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
	}

	/** Случайная точка: в границе, дальше min-distance от спавна, на твёрдом блоке. */
	private Location findSpot(World world) {
		Location spawn = world.getSpawnLocation();
		for (int attempt = 0; attempt < 60; attempt++) {
			int cx = spawn.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
			int cz = spawn.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
			double distance = Math.hypot(cx - spawn.getX(), cz - spawn.getZ());
			if (distance < minDistance) {
				continue;
			}
			Location probe = new Location(world, cx, 64.0D, cz);
			if (!world.getWorldBorder().isInside(probe)) {
				continue;
			}
			Block ground = world.getHighestBlockAt(cx, cz);
			Block above = ground.getRelative(0, 1, 0);
			if (safeBlocksOnly && !isGoodGround(ground, above)) {
				continue;
			}
			return new Location(world, cx + 0.5D, above.getY(), cz + 0.5D);
		}
		return null;
	}

	private boolean isGoodGround(Block ground, Block above) {
		if (ground.isLiquid() || !ground.getType().isSolid()) {
			return false;
		}
		Material air = above.getType();
		if (air != Material.AIR && air != Material.CAVE_AIR && air != Material.SNOW) {
			return false;
		}
		String name = ground.getType().name();
		if (name.endsWith("_LEAVES") || name.endsWith("_LOG") || name.contains("MAGMA") || name.contains("CAMPFIRE")) {
			return false;
		}
		return true;
	}
}
