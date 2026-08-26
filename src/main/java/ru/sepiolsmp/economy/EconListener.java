package ru.sepiolsmp.economy;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Открытие счёта новому игроку и обновление кэша ников (оффлайн-режим + AuthMe). */
public final class EconListener implements Listener {

	private final SepiolEconomy plugin;

	public EconListener(SepiolEconomy plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		boolean created = plugin.accounts().ensure(player.getUniqueId(), player.getName());
		if (created) {
			plugin.msg().send(player, "account-created",
					"%amount%", plugin.msg().money(plugin.accounts().startingBalance()));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		// Сохранять на каждый выход не нужно — автосейв сам сбросит изменения на диск.
		plugin.accounts().ensure(event.getPlayer().getUniqueId(), event.getPlayer().getName());
	}
}
