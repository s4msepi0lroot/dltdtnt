package ru.sepiolsmp.economy.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Сообщения, цвета и русские числительные валюты. */
public final class Msg {

	private final FileConfiguration cfg;
	private final String prefix;
	private final String singular;
	private final String few;
	private final String many;
	private final int decimals;

	public Msg(FileConfiguration cfg) {
		this.cfg = cfg;
		this.prefix = cfg.getString("messages.prefix", "&6[Банк]&r ");
		this.singular = cfg.getString("economy.currency.singular", "сепиол");
		this.few = cfg.getString("economy.currency.few", "сепиола");
		this.many = cfg.getString("economy.currency.many", "сепиолов");
		this.decimals = Math.max(0, Math.min(4, cfg.getInt("economy.decimals", 2)));
	}

	/** Легаси-коды цвета (&a, &c) → Adventure Component. */
	public static Component text(String legacy) {
		return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy == null ? "" : legacy);
	}

	public String raw(String path) {
		String value = cfg.getString("messages." + path);
		return value == null ? "" : value;
	}

	/** Подстановка парами: fill(t, "%player%", nick, "%amount%", sum). */
	public String fill(String template, Object... kv) {
		String out = template == null ? "" : template;
		for (int i = 0; i + 1 < kv.length; i += 2) {
			out = out.replace(String.valueOf(kv[i]), String.valueOf(kv[i + 1]));
		}
		return out;
	}

	public void send(CommandSender to, String path, Object... kv) {
		String body = raw(path);
		if (body.isEmpty()) {
			return;
		}
		to.sendMessage(text(prefix + fill(body, kv)));
	}

	/** Строка без префикса (для списков и таблиц). */
	public void raw(CommandSender to, String legacy) {
		to.sendMessage(text(legacy));
	}

	public void plain(CommandSender to, String legacy) {
		to.sendMessage(text(prefix + legacy));
	}

	public String num(double value) {
		BigDecimal bd = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros();
		String s = bd.toPlainString();
		if (s.equals("-0")) {
			s = "0";
		}
		return s;
	}

	/** Например: 1 сепиол, 3 сепиола, 20 сепиолов, 1.5 сепиола. */
	public String money(double value) {
		return num(value) + " " + word(value);
	}

	public String word(double value) {
		double abs = Math.abs(value);
		if (Math.abs(abs - Math.rint(abs)) > 1.0E-9) {
			return few;
		}
		long n = (long) Math.rint(abs);
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

	public int decimals() {
		return decimals;
	}

	public String prefix() {
		return prefix;
	}

	public String currencyPlural() {
		return many;
	}

	public String currencySingular() {
		return singular;
	}
}
