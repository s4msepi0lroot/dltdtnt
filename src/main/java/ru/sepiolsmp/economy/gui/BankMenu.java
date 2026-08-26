package ru.sepiolsmp.economy.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.sepiolsmp.economy.SepiolEconomy;
import ru.sepiolsmp.economy.econ.Market;
import ru.sepiolsmp.economy.util.Msg;

import java.util.ArrayList;
import java.util.List;

/**
 * Витрина банка: постраничный курс со живыми ценами.
 * ЛКМ — сдать всё, Shift+ЛКМ — сдать один стак, ПКМ — только узнать цену.
 */
public final class BankMenu implements Listener {

	private static final int SIZE = 54;
	private static final int PAGE_SIZE = 45;
	private static final int SLOT_PREV = 45;
	private static final int SLOT_INFO = 49;
	private static final int SLOT_NEXT = 53;

	private final SepiolEconomy plugin;

	public BankMenu(SepiolEconomy plugin) {
		this.plugin = plugin;
	}

	private static final class Holder implements InventoryHolder {
		private final List<String> ids = new ArrayList<>();
		private int page;
		private Inventory inventory;

		@Override
		public Inventory getInventory() {
			return inventory;
		}
	}

	public void open(Player player, int page) {
		List<Market.Price> prices = plugin.market().sortedByPrice();
		int pages = Math.max(1, (prices.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int current = Math.max(0, Math.min(pages - 1, page));

		Holder holder = new Holder();
		holder.page = current;
		String title = plugin.getConfig().getString("bank.gui.title", "&8Банк сезона")
				+ " &8" + (current + 1) + "/" + pages;
		Inventory inventory = Bukkit.createInventory(holder, SIZE, Msg.text(title));
		holder.inventory = inventory;

		int from = current * PAGE_SIZE;
		int to = Math.min(prices.size(), from + PAGE_SIZE);
		for (int i = from; i < to; i++) {
			Market.Price price = prices.get(i);
			holder.ids.add(price.id);
			inventory.setItem(i - from, icon(player, price));
		}

		if (current > 0) {
			inventory.setItem(SLOT_PREV, simple(Material.ARROW, "&fНазад", List.of("&8Страница " + current)));
		}
		if (current < pages - 1) {
			inventory.setItem(SLOT_NEXT, simple(Material.ARROW, "&fДальше", List.of("&8Страница " + (current + 2))));
		}
		inventory.setItem(SLOT_INFO, info(player));

		player.openInventory(inventory);
	}

	private ItemStack icon(Player player, Market.Price price) {
		Material material = Material.matchMaterial(price.id);
		if (material == null || material.isAir()) {
			material = Material.PAPER;
		}
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return item;
		}
		meta.displayName(Msg.text("&e" + price.name));

		double unit = plugin.market().unitPrice(price.id);
		double factor = plugin.market().factor(price.id);
		int have = plugin.api().countSellable(player, price.id);

		List<Component> lore = new ArrayList<>();
		lore.add(Msg.text(plugin.msg().fill(plugin.msg().raw("gui-price"),
				"%price%", plugin.msg().money(unit))));
		lore.add(Msg.text(plugin.msg().fill(plugin.msg().raw("gui-factor"),
				"%factor%", plugin.msg().num(factor * 100.0))));
		if (have > 0) {
			Market.Quote quote = plugin.market().quote(price.id, have);
			lore.add(Msg.text(plugin.msg().fill(plugin.msg().raw("gui-have"),
					"%qty%", String.valueOf(have),
					"%amount%", plugin.msg().money(quote == null ? 0.0 : quote.total))));
			lore.add(Msg.text(plugin.msg().raw("gui-hint-sell")));
			lore.add(Msg.text(plugin.msg().raw("gui-hint-stack")));
		} else {
			lore.add(Msg.text(plugin.msg().raw("gui-nothing")));
		}
		lore.add(Msg.text("&8" + price.id));
		meta.lore(lore);
		item.setItemMeta(meta);
		return item;
	}

	private ItemStack info(Player player) {
		List<String> lore = new ArrayList<>();
		lore.add("&7На счете&8: &e" + plugin.msg().money(plugin.accounts().balance(player.getUniqueId())));
		double cap = plugin.market().dailyCap();
		lore.add("&7Напечатано за сутки&8: &f" + plugin.msg().money(plugin.market().emittedToday())
				+ (cap <= 0.0 ? "" : " &8/ &f" + plugin.msg().money(cap)));
		lore.add("&7Казна сезона&8: &f" + plugin.msg().money(plugin.accounts().treasury()));
		lore.add("&8Банк покупает, но ничего не продает");
		return simple(Material.GOLD_INGOT, "&6Банк сезона", lore);
	}

	private ItemStack simple(Material material, String name, List<String> loreLines) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return item;
		}
		meta.displayName(Msg.text(name));
		List<Component> lore = new ArrayList<>();
		for (String line : loreLines) {
			lore.add(Msg.text(line));
		}
		meta.lore(lore);
		item.setItemMeta(meta);
		return item;
	}

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		Inventory top = event.getView().getTopInventory();
		if (!(top.getHolder() instanceof Holder holder)) {
			return;
		}
		event.setCancelled(true);
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}
		int slot = event.getRawSlot();
		if (slot < 0 || slot >= SIZE) {
			return;
		}
		if (slot == SLOT_PREV) {
			open(player, holder.page - 1);
			return;
		}
		if (slot == SLOT_NEXT) {
			open(player, holder.page + 1);
			return;
		}
		if (slot == SLOT_INFO) {
			open(player, holder.page);
			return;
		}
		if (slot >= PAGE_SIZE || slot >= holder.ids.size()) {
			return;
		}
		String id = holder.ids.get(slot);

		if (event.isRightClick()) {
			Market.Price price = plugin.market().price(id);
			if (price != null) {
				plugin.msg().send(player, "bank-quote",
						"%item%", price.name,
						"%price%", plugin.msg().money(plugin.market().unitPrice(id)),
						"%factor%", plugin.msg().num(plugin.market().factor(id) * 100.0));
			}
			return;
		}

		int limit = event.isShiftClick() ? 64 : 0;
		plugin.bank().applySell(player, id, limit);
		open(player, holder.page);
	}
}
