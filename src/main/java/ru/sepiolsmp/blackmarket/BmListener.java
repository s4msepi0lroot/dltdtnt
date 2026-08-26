package ru.sepiolsmp.blackmarket;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import ru.sepiolsmp.blackmarket.market.Trader;
import ru.sepiolsmp.blackmarket.util.Msg;

/** События: клик по барыге, его неуязвимость, свет розыска при входе. */
public final class BmListener implements Listener {

	private final SepiolBlackMarket plugin;

	public BmListener(SepiolBlackMarket plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onInteract(PlayerInteractEntityEvent event) {
		Trader trader = plugin.trader();
		if (!trader.isTraderEntity(event.getRightClicked())) {
			return;
		}
		event.setCancelled(true);
		if (event.getHand() != EquipmentSlot.HAND) {
			return;
		}
		Player player = event.getPlayer();
		Msg msg = plugin.messages();
		if (!player.hasPermission("sepiolblackmarket.use")) {
			msg.send(player, "no-permission");
			return;
		}
		if (!trader.active()) {
			msg.send(player, "trader-absent", "%next%", Msg.duration(trader.untilArrival()));
			return;
		}
		// тот, кто дошёл ногами, адрес уже знает
		trader.grantCoords(player.getUniqueId());
		plugin.menu().open(player, 0);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		if (plugin.trader().isTraderEntity(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntityDeath(EntityDeathEvent event) {
		if (plugin.trader().isTraderEntity(event.getEntity())) {
			event.getDrops().clear();
			event.setDroppedExp(0);
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		plugin.wanted().apply(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent event) {
		if (!plugin.wanted().keepOnDeath()) {
			plugin.wanted().clear(event.getEntity().getUniqueId());
		}
	}
}
