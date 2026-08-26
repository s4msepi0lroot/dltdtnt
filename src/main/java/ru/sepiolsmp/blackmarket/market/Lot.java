package ru.sepiolsmp.blackmarket.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Одна позиция ассортимента. Всё из config.yml, ничего в коде. */
public final class Lot {

	public final String id;
	public final String name;
	public final String category;
	public final double base;
	/** Личный шаг цены в процентах, отрицательный = брать общий. */
	public final double step;
	public final int qty;
	/** Сколько раз лот купят за завоз, -1 = без лимита. */
	public final int stock;
	public final String item;
	public final String icon;
	public final boolean contraband;
	public final String effect;
	public final List<String> commands;
	public final List<String> lore;

	public Lot(
			String id,
			String name,
			String category,
			double base,
			double step,
			int qty,
			int stock,
			String item,
			String icon,
			boolean contraband,
			String effect,
			List<String> commands,
			List<String> lore) {
		this.id = id;
		this.name = name == null || name.isEmpty() ? id : name;
		this.category = category == null ? "" : category;
		this.base = Math.max(0.0D, base);
		this.step = step;
		this.qty = Math.max(1, qty);
		this.stock = stock;
		this.item = item == null || item.isEmpty() ? null : item;
		this.icon = icon == null || icon.isEmpty() ? null : icon;
		this.contraband = contraband;
		this.effect = effect == null ? "" : effect.trim().toLowerCase(Locale.ROOT);
		this.commands = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
		this.lore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
	}

	public boolean unlimited() {
		return stock < 0;
	}

	public boolean hasItem() {
		return item != null;
	}

	public boolean hasCommands() {
		return !commands.isEmpty();
	}

	public boolean hasEffect() {
		return !effect.isEmpty();
	}

	/** Имя без цветовых кодов - для журнала и веб-профиля. */
	public String plainName() {
		return name.replaceAll("(?i)&[0-9a-fk-or]", "");
	}

	@Override
	public String toString() {
		return "Lot{" + id + ", base=" + base + ", qty=" + qty + ", stock=" + stock + "}";
	}
}
