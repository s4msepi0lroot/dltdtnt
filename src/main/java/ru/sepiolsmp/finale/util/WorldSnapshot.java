package ru.sepiolsmp.finale.util;

import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Снимок настроек мира перед финалом: граница, погода, время, сложность, геймрулы.
 * Блоки это не возвращает (для этого есть CoreProtect и бэкапы), но после /finale abort
 * сервер снова играбелен, а не залит вечной грозой с границей в 96 блоков.
 */
public final class WorldSnapshot {

	private static final List<GameRule<Boolean>> BOOLEAN_RULES = List.of(
			GameRule.DO_DAYLIGHT_CYCLE,
			GameRule.DO_WEATHER_CYCLE,
			GameRule.KEEP_INVENTORY,
			GameRule.DO_IMMEDIATE_RESPAWN,
			GameRule.NATURAL_REGENERATION,
			GameRule.DO_MOB_SPAWNING,
			GameRule.DO_FIRE_TICK,
			GameRule.MOB_GRIEFING,
			GameRule.SHOW_DEATH_MESSAGES,
			GameRule.ANNOUNCE_ADVANCEMENTS);

	private WorldSnapshot() {
	}

	public static void capture(World world, ConfigurationSection out) {
		if (world == null || out == null) {
			return;
		}
		WorldBorder border = world.getWorldBorder();
		out.set("world", world.getName());
		out.set("border.size", border.getSize());
		out.set("border.center-x", border.getCenter().getX());
		out.set("border.center-z", border.getCenter().getZ());
		out.set("border.warning-distance", border.getWarningDistance());
		out.set("border.warning-time", border.getWarningTime());
		out.set("border.damage-amount", border.getDamageAmount());
		out.set("border.damage-buffer", border.getDamageBuffer());
		out.set("full-time", world.getFullTime());
		out.set("storm", world.hasStorm());
		out.set("thundering", world.isThundering());
		out.set("weather-duration", world.getWeatherDuration());
		out.set("thunder-duration", world.getThunderDuration());
		out.set("pvp", world.getPVP());
		out.set("difficulty", world.getDifficulty().name());
		for (GameRule<Boolean> rule : BOOLEAN_RULES) {
			Boolean value = world.getGameRuleValue(rule);
			out.set("gamerules." + rule.getName(), value == null ? Boolean.TRUE : value);
		}
		Integer randomTick = world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED);
		out.set("gamerules." + GameRule.RANDOM_TICK_SPEED.getName(), randomTick == null ? 3 : randomTick);
	}

	public static boolean restore(World world, ConfigurationSection in) {
		if (world == null || in == null) {
			return false;
		}
		WorldBorder border = world.getWorldBorder();
		border.setCenter(in.getDouble("border.center-x", 0.0), in.getDouble("border.center-z", 0.0));
		border.setSize(in.getDouble("border.size", 60000000.0));
		border.setWarningDistance(in.getInt("border.warning-distance", 5));
		border.setWarningTime(in.getInt("border.warning-time", 15));
		border.setDamageAmount(in.getDouble("border.damage-amount", 0.2));
		border.setDamageBuffer(in.getDouble("border.damage-buffer", 5.0));
		world.setFullTime(in.getLong("full-time", world.getFullTime()));
		world.setStorm(in.getBoolean("storm", false));
		world.setThundering(in.getBoolean("thundering", false));
		world.setWeatherDuration(in.getInt("weather-duration", 0));
		world.setThunderDuration(in.getInt("thunder-duration", 0));
		world.setPVP(in.getBoolean("pvp", true));
		try {
			world.setDifficulty(Difficulty.valueOf(in.getString("difficulty", world.getDifficulty().name())));
		} catch (IllegalArgumentException ignored) {
			// сложность останется как есть
		}
		ConfigurationSection rules = in.getConfigurationSection("gamerules");
		if (rules != null) {
			for (GameRule<Boolean> rule : BOOLEAN_RULES) {
				if (rules.contains(rule.getName())) {
					world.setGameRule(rule, rules.getBoolean(rule.getName()));
				}
			}
			String tickRule = GameRule.RANDOM_TICK_SPEED.getName();
			if (rules.contains(tickRule)) {
				world.setGameRule(GameRule.RANDOM_TICK_SPEED, rules.getInt(tickRule));
			}
		}
		return true;
	}
}
