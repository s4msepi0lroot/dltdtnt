package ru.sepiolsmp.finale;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import ru.sepiolsmp.finale.phase.Phase;

import java.util.List;
import java.util.Locale;

/**
 * Sledit za tem, chtoby smert v finale byla okonchatelnoy:
 * net respawna, net pobega cherez komandy, net vozvrata v igru relogom.
 */
public final class FinaleListener implements Listener {

	private final SepiolFinale plugin;

	public FinaleListener(SepiolFinale plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent event) {
		if (!plugin.engine().phase().running()) {
			return;
		}
		plugin.engine().markDead(event.getEntity());
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onRespawn(PlayerRespawnEvent event) {
		if (!plugin.engine().noRespawn()) {
			return;
		}
		Player player = event.getPlayer();
		Location watchPoint = plugin.engine().watchPoint();
		if (watchPoint != null) {
			event.setRespawnLocation(watchPoint);
		}
		plugin.broadcast().titleTo(player, plugin.text("you-are-dead"), plugin.text("you-are-dead-sub"), 10, 80, 20);
		plugin.getServer().getScheduler().runTask(plugin, () -> plugin.engine().forceSpectator(player));
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Phase phase = plugin.engine().phase();
		if (!phase.running()) {
			return;
		}
		Player player = event.getPlayer();
		boolean lateJoin = phase.terminal()
				&& plugin.getConfig().getBoolean("safety.block-new-joins-in-ash", true);
		if (lateJoin || plugin.engine().isDead(player.getUniqueId())) {
			plugin.broadcast().to(player, plugin.msg("joined-during-ash"));
			plugin.getServer().getScheduler().runTask(plugin, () -> plugin.engine().forceSpectator(player));
			return;
		}
		plugin.broadcast().to(player, plugin.msg("phase-progress")
				.replace("%phase%", phase.title())
				.replace("%time%", plugin.engine().timeLeftText()));
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		if (plugin.engine().phase().running()) {
			plugin.engine().recount();
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onCommand(PlayerCommandPreprocessEvent event) {
		Phase phase = plugin.engine().phase();
		if (!phase.terminal() || !plugin.getConfig().getBoolean("safety.block-commands-in-ash", true)) {
			return;
		}
		Player player = event.getPlayer();
		if (player.hasPermission("sepiolfinale.admin")) {
			return;
		}
		String message = event.getMessage().toLowerCase(Locale.ROOT);
		List<String> allowed = plugin.getConfig().getStringList("safety.allowed-commands");
		for (String raw : allowed) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			String prefix = raw.trim().toLowerCase(Locale.ROOT);
			if (!prefix.startsWith("/")) {
				prefix = "/" + prefix;
			}
			if (message.equals(prefix) || message.startsWith(prefix + " ")) {
				return;
			}
		}
		event.setCancelled(true);
		plugin.broadcast().to(player, plugin.msg("commands-blocked"));
	}

	@EventHandler(ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		if (frozen()) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent event) {
		if (frozen()) {
			event.setCancelled(true);
		}
	}

	/** S fazy Tishiny mir tolko smotryat. */
	private boolean frozen() {
		Phase phase = plugin.engine().phase();
		return phase == Phase.SILENCE || phase == Phase.DONE;
	}
}
