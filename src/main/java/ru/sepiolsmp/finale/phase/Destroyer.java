package ru.sepiolsmp.finale.phase;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Работяга апокалипсиса: гниёние блоков, растворение мира, тнт, взрывы, молнии, волны мобов.
 *
 * Главные правила, которые спасают TPS:
 *  - работаем только в уже загруженных чанках вокруг живых игроков;
 *  - блоки меняем без физики (setType(..., false)), иначе каскады обновлений убьют сервер;
 *  - у всего есть бюджет на тик, выдаваемый движком.
 */
public final class Destroyer {

	/** Это не трогаем: или бессмысленно, или ломает сервер. */
	private static final Set<Material> KEEP = EnumSet.of(
			Material.BEDROCK,
			Material.BARRIER,
			Material.LIGHT,
			Material.END_PORTAL,
			Material.END_PORTAL_FRAME,
			Material.END_GATEWAY,
			Material.NETHER_PORTAL,
			Material.COMMAND_BLOCK,
			Material.CHAIN_COMMAND_BLOCK,
			Material.REPEATING_COMMAND_BLOCK,
			Material.STRUCTURE_BLOCK,
			Material.STRUCTURE_VOID,
			Material.JIGSAW,
			Material.WATER,
			Material.LAVA);

	private final Random random = new Random();
	private final Logger logger;

	public Destroyer(Logger logger) {
		this.logger = logger;
	}

	/** Превращает случайные блоки рядом с игроками в мёртвую породу. */
	public int corrode(World world, List<Player> targets, int budget, int radius, List<Material> palette) {
		if (world == null || targets.isEmpty() || palette.isEmpty() || budget <= 0) {
			return 0;
		}
		int done = 0;
		int attempts = budget * 3;
		for (int i = 0; i < attempts && done < budget; i++) {
			Block block = randomSurfaceBlock(world, targets, radius);
			if (block == null) {
				continue;
			}
			block.setType(palette.get(random.nextInt(palette.size())), false);
			done++;
		}
		return done;
	}

	/** Сносит верхние слои мира в ничто: земля перестаёт существовать. */
	public int dissolve(World world, List<Player> targets, int budget, int radius, int layers) {
		if (world == null || targets.isEmpty() || budget <= 0) {
			return 0;
		}
		int done = 0;
		int depth = Math.max(1, layers);
		for (int i = 0; i < budget && done < budget; i++) {
			Block top = randomSurfaceBlock(world, targets, radius);
			if (top == null) {
				continue;
			}
			for (int layer = 0; layer < depth && done < budget; layer++) {
				Block block = top.getRelative(0, -layer, 0);
				if (block.getY() <= world.getMinHeight() + 1) {
					break;
				}
				if (block.getType().isAir() || KEEP.contains(block.getType())) {
					continue;
				}
				block.setType(Material.AIR, false);
				done++;
			}
		}
		return done;
	}

	/** Дождь из тнт над головами. */
	public void rainTnt(World world, List<Player> targets, int count) {
		for (int i = 0; i < count; i++) {
			Player player = pick(targets);
			if (player == null) {
				return;
			}
			Location location = player.getLocation().clone().add(offset(8), 18 + random.nextInt(10), offset(8));
			if (!loaded(world, location)) {
				continue;
			}
			try {
				TNTPrimed tnt = world.spawn(location, TNTPrimed.class);
				tnt.setFuseTicks(30 + random.nextInt(50));
			} catch (RuntimeException exception) {
				logger.warning("Не смог создать тнт: " + exception.getMessage());
				return;
			}
		}
	}

	/** Взрывы по поверхности рядом с игроками. */
	public void explode(World world, List<Player> targets, int count, float power, boolean fire) {
		for (int i = 0; i < count; i++) {
			Player player = pick(targets);
			if (player == null) {
				return;
			}
			Location location = player.getLocation().clone().add(offset(24), 0, offset(24));
			if (!loaded(world, location)) {
				continue;
			}
			location.setY(world.getHighestBlockYAt(location) + 1);
			world.createExplosion(location, power, fire, true);
		}
	}

	/** Молнии: по умолчанию только эффект, иногда - настоящая. */
	public void lightning(World world, List<Player> targets, int count, int radius, boolean damaging) {
		for (int i = 0; i < count; i++) {
			Player player = pick(targets);
			if (player == null) {
				return;
			}
			Location location = player.getLocation().clone().add(offset(radius), 0, offset(radius));
			if (!loaded(world, location)) {
				continue;
			}
			location.setY(world.getHighestBlockYAt(location) + 1);
			if (damaging) {
				world.strikeLightning(location);
			} else {
				world.strikeLightningEffect(location);
			}
		}
	}

	/** Волна нежити вокруг каждого живого. */
	public int spawnWave(World world, List<Player> targets, int perPlayer, int radius, List<String> types) {
		if (world == null || types.isEmpty() || perPlayer <= 0) {
			return 0;
		}
		int spawned = 0;
		for (Player player : targets) {
			for (int i = 0; i < perPlayer; i++) {
				EntityType type = entityType(types.get(random.nextInt(types.size())));
				if (type == null) {
					continue;
				}
				Location location = player.getLocation().clone().add(offset(radius), 0, offset(radius));
				if (!loaded(world, location)) {
					continue;
				}
				location.setY(world.getHighestBlockYAt(location) + 1);
				try {
					world.spawnEntity(location, type);
					spawned++;
				} catch (RuntimeException exception) {
					logger.warning("Не спавнится " + type + ": " + exception.getMessage());
				}
			}
		}
		return spawned;
	}

	/** Палитра из конфига: непонятные имена отбрасываются с предупреждением. */
	public static List<Material> palette(List<String> names, Logger logger) {
		List<Material> out = new ArrayList<>();
		for (String raw : names) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
			if (material == null || !material.isBlock()) {
				logger.warning("Неизвестный блок в палитре: " + raw);
				continue;
			}
			out.add(material);
		}
		return out;
	}

	private EntityType entityType(String raw) {
		try {
			return EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			logger.warning("Неизвестный тип моба: " + raw);
			return null;
		}
	}

	private Block randomSurfaceBlock(World world, List<Player> targets, int radius) {
		Player player = pick(targets);
		if (player == null) {
			return null;
		}
		int x = player.getLocation().getBlockX() + offset(radius);
		int z = player.getLocation().getBlockZ() + offset(radius);
		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			return null;
		}
		int y = world.getHighestBlockYAt(x, z);
		if (y <= world.getMinHeight() + 1) {
			return null;
		}
		Block block = world.getBlockAt(x, y, z);
		if (block.getType().isAir() || KEEP.contains(block.getType())) {
			return null;
		}
		return block;
	}

	private boolean loaded(World world, Location location) {
		return world != null && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
	}

	private int offset(int radius) {
		int span = Math.max(1, radius);
		return random.nextInt(span * 2 + 1) - span;
	}

	private Player pick(List<Player> targets) {
		return targets.isEmpty() ? null : targets.get(random.nextInt(targets.size()));
	}
}
