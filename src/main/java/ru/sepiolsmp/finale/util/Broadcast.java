package ru.sepiolsmp.finale.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.logging.Logger;

/**
 * Всё общение с игроками в одном месте: чат, тайтлы, активбар, звук.
 * Цвета пишутся привычными &-кодами и переводятся в Adventure-компоненты.
 * Звуки задаются строковыми ключами ("entity.wither.spawn"), а не enum'ом Sound -
 * так код не развалится при обновлении Minecraft.
 */
public final class Broadcast {

	private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

	private final Logger logger;

	public Broadcast(Logger logger) {
		this.logger = logger;
	}

	public static Component text(String legacy) {
		return LEGACY.deserialize(legacy == null ? "" : legacy);
	}

	/** Текст без цветовых кодов - для лога в консоль. */
	public static String plain(String legacy) {
		return legacy == null ? "" : legacy.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
	}

	public void chat(String legacy) {
		if (legacy == null || legacy.isBlank()) {
			return;
		}
		Component component = text(legacy);
		for (Player player : Bukkit.getOnlinePlayers()) {
			player.sendMessage(component);
		}
		logger.info(plain(legacy));
	}

	public void to(CommandSender receiver, String legacy) {
		if (receiver == null || legacy == null || legacy.isBlank()) {
			return;
		}
		receiver.sendMessage(text(legacy));
	}

	public void actionBar(String legacy) {
		if (legacy == null || legacy.isBlank()) {
			return;
		}
		Component component = text(legacy);
		for (Player player : Bukkit.getOnlinePlayers()) {
			player.sendActionBar(component);
		}
	}

	public void title(String main, String sub, int fadeInTicks, int stayTicks, int fadeOutTicks) {
		if ((main == null || main.isBlank()) && (sub == null || sub.isBlank())) {
			return;
		}
		Title title = buildTitle(main, sub, fadeInTicks, stayTicks, fadeOutTicks);
		for (Player player : Bukkit.getOnlinePlayers()) {
			player.showTitle(title);
		}
	}

	public void titleTo(Player player, String main, String sub, int fadeInTicks, int stayTicks, int fadeOutTicks) {
		if (player == null) {
			return;
		}
		player.showTitle(buildTitle(main, sub, fadeInTicks, stayTicks, fadeOutTicks));
	}

	public void sound(String key, float volume, float pitch) {
		if (key == null || key.isBlank()) {
			return;
		}
		for (Player player : Bukkit.getOnlinePlayers()) {
			try {
				player.playSound(player.getLocation(), key, volume, pitch);
			} catch (RuntimeException ignored) {
				// неверный ключ звука не должен ронять финал
			}
		}
	}

	private static Title buildTitle(String main, String sub, int fadeInTicks, int stayTicks, int fadeOutTicks) {
		return Title.title(
				text(main),
				text(sub),
				Title.Times.times(ticks(fadeInTicks), ticks(stayTicks), ticks(fadeOutTicks)));
	}

	private static Duration ticks(int ticks) {
		return Duration.ofMillis(Math.max(0, ticks) * 50L);
	}
}
