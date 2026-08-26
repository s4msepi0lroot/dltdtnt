package ru.sepiolsmp.core.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import ru.sepiolsmp.core.SepiolCore;

/**
 * The only class that talks to the Bukkit API, and it does so strictly on the
 * main thread. The web threads read the immutable snapshot instead, which keeps
 * the HTTP layer from ever touching the server internals.
 */
public final class SnapshotService {

	public record Online(String name, UUID id, long playTicks, long deaths, long mobKills,
			long playerKills, double health, int xpLevel, String world, int x, int y, int z) {
	}

	public record Snapshot(long takenAt, double tps, int online, int max, List<Online> players) {

		public Online find(UUID id) {
			for (Online player : players) {
				if (player.id().equals(id)) {
					return player;
				}
			}
			return null;
		}
	}

	private final SepiolCore plugin;
	private volatile Snapshot current = new Snapshot(System.currentTimeMillis(), 20.0D, 0, 0, List.of());
	private BukkitTask task;

	public SnapshotService(SepiolCore plugin) {
		this.plugin = plugin;
	}

	public void start() {
		capture();
		task = Bukkit.getScheduler().runTaskTimer(plugin, this::capture, 20L, 40L);
	}

	public void stop() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	public Snapshot snapshot() {
		return current;
	}

	private void capture() {
		List<Online> players = new ArrayList<>();
		for (Player player : Bukkit.getOnlinePlayers()) {
			players.add(new Online(
					player.getName(),
					player.getUniqueId(),
					safeStat(player, Statistic.PLAY_ONE_MINUTE),
					safeStat(player, Statistic.DEATHS),
					safeStat(player, Statistic.MOB_KILLS),
					safeStat(player, Statistic.PLAYER_KILLS),
					player.getHealth(),
					player.getLevel(),
					player.getWorld().getName(),
					player.getLocation().getBlockX(),
					player.getLocation().getBlockY(),
					player.getLocation().getBlockZ()));
		}
		current = new Snapshot(System.currentTimeMillis(), tps(), players.size(),
				Bukkit.getMaxPlayers(), List.copyOf(players));
	}

	private static long safeStat(Player player, Statistic statistic) {
		try {
			return player.getStatistic(statistic);
		} catch (Exception e) {
			return 0L;
		}
	}

	private static double tps() {
		try {
			double[] values = Bukkit.getTPS();
			return values.length > 0 ? Math.min(20.0D, values[0]) : 20.0D;
		} catch (Throwable ignored) {
			return 20.0D;
		}
	}
}
