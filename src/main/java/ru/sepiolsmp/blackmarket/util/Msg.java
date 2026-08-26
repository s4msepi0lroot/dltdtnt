package ru.sepiolsmp.blackmarket.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** Сообщения, цвета и числа. Всё из config.yml, в коде текста нет. */
public final class Msg {

	private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

	private FileConfiguration cfg;
	private String prefix = "";
	private int decimals = 2;
	private String singular = "сепиол";
	private String few = "сепиола";
	private String many = "сепиолов";

	public Msg(FileConfiguration cfg) {
		reload(cfg);
	}

	public void reload(FileConfiguration newCfg) {
		this.cfg = newCfg;
		this.prefix = cfg.getString("messages.prefix", "");
		this.decimals = Math.max(0, Math.min(4, cfg.getInt("economy.decimals", 2)));
		this.singular = cfg.getString("economy.currency.singular", "сепиол");
		this.few = cfg.getString("economy.currency.few", "сепиола");
		this.many = cfg.getString("economy.currency.many", "сепиолов");
	}

	public static Component text(String legacy) {
		return LEGACY.deserialize(legacy == null ? "" : legacy);
	}

	/** Сырая строка из messages.<path>. */
	public String raw(String path) {
		String value = cfg.getString("messages." + path);
		return value == null ? "" : value;
	}

	public List<String> lines(String path) {
		List<String> list = cfg.getStringList("messages." + path);
		return list == null ? new ArrayList<>() : list;
	}

	/** Подстановка парами: fill(t, "%qty%", 3, "%item%", "..."). */
	public String fill(String template, Object... pairs) {
		if (template == null) {
			return "";
		}
		String out = template;
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			Object key = pairs[i];
			if (key == null) {
				continue;
			}
			out = out.replace(key.toString(), String.valueOf(pairs[i + 1]));
		}
		return out;
	}

	/** Собранная строка без префикса. */
	public String get(String path, Object... pairs) {
		return fill(raw(path), pairs);
	}

	public void send(CommandSender to, String path, Object... pairs) {
		String body = get(path, pairs);
		if (body.isEmpty()) {
			return;
		}
		to.sendMessage(text(prefix + body));
	}

	/** Отправить готовую legacy-строку с префиксом. */
	public void raw(CommandSender to, String legacy) {
		to.sendMessage(text(prefix + (legacy == null ? "" : legacy)));
	}

	/** Отправить строку без префикса. */
	public void plain(CommandSender to, String legacy) {
		to.sendMessage(text(legacy == null ? "" : legacy));
	}

	public void broadcast(String path, Object... pairs) {
		String body = get(path, pairs);
		if (body.isEmpty()) {
			return;
		}
		Bukkit.broadcast(text(prefix + body));
	}

	/** Только тем, у кого есть право (стафф-оповещения). */
	public void broadcastPerm(String permission, String path, Object... pairs) {
		String body = get(path, pairs);
		if (body.isEmpty()) {
			return;
		}
		Component component = text(prefix + body);
		Bukkit.getConsoleSender().sendMessage(component);
		for (Player online : Bukkit.getOnlinePlayers()) {
			if (online.hasPermission(permission)) {
				online.sendMessage(component);
			}
		}
	}

	public String num(double value) {
		BigDecimal bd = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros();
		return bd.toPlainString();
	}

	public String money(double value) {
		return num(value) + " " + word(value);
	}

	/** Русское склонение: 1 сепиол, 2 сепиола, 5 сепиолов. */
	public String word(double value) {
		long n = (long) Math.floor(Math.abs(value));
		long mod100 = n % 100L;
		long mod10 = n % 10L;
		if (mod100 >= 11L && mod100 <= 14L) {
			return many;
		}
		if (mod10 == 1L) {
			return singular;
		}
		if (mod10 >= 2L && mod10 <= 4L) {
			return few;
		}
		return many;
	}

	/** Человеческая длительность: 2 ч 15 мин, 40 мин, 30 с. */
	public static String duration(long millis) {
		long total = Math.max(0L, millis) / 1000L;
		long hours = total / 3600L;
		long minutes = (total % 3600L) / 60L;
		long seconds = total % 60L;
		if (hours > 0L) {
			return minutes > 0L ? hours + " ч " + minutes + " мин" : hours + " ч";
		}
		if (minutes > 0L) {
			return minutes + " мин";
		}
		return seconds + " с";
	}

	public int decimals() {
		return decimals;
	}

	public String prefix() {
		return prefix;
	}

	public String currencySingular() {
		return singular;
	}

	public String currencyFew() {
		return few;
	}

	public String currencyMany() {
		return many;
	}
}
