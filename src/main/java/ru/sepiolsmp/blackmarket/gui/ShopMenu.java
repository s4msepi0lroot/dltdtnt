package ru.sepiolsmp.blackmarket.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.sepiolsmp.blackmarket.SepiolBlackMarket;
import ru.sepiolsmp.blackmarket.econ.EconomyLink;
import ru.sepiolsmp.blackmarket.market.Catalog;
import ru.sepiolsmp.blackmarket.market.Lot;
import ru.sepiolsmp.blackmarket.market.Trader;
import ru.sepiolsmp.blackmarket.util.Msg;

/** Витрина барыги и вся логика покупки. ЛКМ по лоту = сделка. */
public final class ShopMenu implements Listener {

	private static final int PAGE_SIZE = 45;
	private static final int SLOT_PREV = 45;
	private static final int SLOT_INFO = 49;
	private static final int SLOT_NEXT = 53;

	private static final class Holder implements InventoryHolder {
		private final int page;
		private final List<Lot> lots;
		private Inventory inventory;

		private Holder(int page, List<Lot> lots) {
			this.page = page;
			this.lots = lots;
		}

		@Override
		public Inventory getInventory() {
			return inventory;
		}
	}

	private final SepiolBlackMarket plugin;
	private final Map<UUID, Long> lastBuy = new HashMap<>();
	private int cooldownSeconds = 2;

	public ShopMenu(SepiolBlackMarket plugin) {
		this.plugin = plugin;
	}

	public void loadConfig(FileConfiguration cfg) {
		cooldownSeconds = Math.max(0, cfg.getInt("prices.buy-cooldown-seconds", 2));
	}

	// ------------------------------------------------------------------
	// отрисовка
	// ------------------------------------------------------------------

	public void open(Player player, int page) {
		Msg msg = plugin.messages();
		Catalog catalog = plugin.catalog();
		List<Lot> all = new ArrayList<>(catalog.lots());
		int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int current = Math.max(0, Math.min(page, pages - 1));
		int from = current * PAGE_SIZE;
		int to = Math.min(all.size(), from + PAGE_SIZE);
		List<Lot> shown = new ArrayList<>(all.subList(from, to));

		Holder holder = new Holder(current, shown);
		Component title = Msg.text(msg.raw("shop-title"));
		Inventory inventory = Bukkit.createInventory(holder, 54, title);
		holder.inventory = inventory;

		for (int index = 0; index < shown.size(); index++) {
			inventory.setItem(index, render(shown.get(index)));
		}
		if (current > 0) {
			inventory.setItem(SLOT_PREV, button(Material.ARROW, msg.raw("gui-nav-prev"), List.of()));
		}
		if (current < pages - 1) {
			inventory.setItem(SLOT_NEXT, button(Material.ARROW, msg.raw("gui-nav-next"), List.of()));
		}
		inventory.setItem(SLOT_INFO, infoItem(player, current + 1, pages));
		player.openInventory(inventory);
		playSound(player, plugin.trader().soundOpen());
	}

	private ItemStack render(Lot lot) {
		Msg msg = plugin.messages();
		Catalog catalog = plugin.catalog();
		ItemStack stack = catalog.icon(lot);
		ItemMeta meta = stack.getItemMeta();
		if (meta == null) {
			return stack;
		}
		meta.displayName(Msg.text(lot.name));
		List<Component> lore = new ArrayList<>();
		double price = catalog.price(lot.id);
		lore.add(Msg.text(msg.get("gui-price", "%price%", msg.money(price))));
		if (price > lot.base + 0.0001D) {
			lore.add(Msg.text(msg.get("gui-base", "%base%", msg.money(lot.base))));
		}
		lore.add(Msg.text(msg.get("gui-qty", "%qty%", lot.qty)));
		int left = catalog.left(lot.id);
		if (left < 0) {
			lore.add(Msg.text(msg.raw("gui-unlimited")));
		} else {
			lore.add(Msg.text(msg.get("gui-left", "%left%", left)));
		}
		lore.add(Msg.text(lot.contraband ? msg.raw("gui-contraband") : msg.raw("gui-legal")));
		for (String line : lot.lore) {
			lore.add(Msg.text(line));
		}
		lore.add(Msg.text(left == 0 ? msg.raw("gui-sold-out") : msg.raw("gui-hint-buy")));
		meta.lore(lore);
		stack.setItemMeta(meta);
		return stack;
	}

	private ItemStack infoItem(Player player, int page, int pages) {
		Msg msg = plugin.messages();
		EconomyLink economy = plugin.economy();
		Trader trader = plugin.trader();
		List<Component> lore = new ArrayList<>();
		double balance = economy.available() ? economy.balance(player.getUniqueId()) : 0.0D;
		lore.add(Msg.text(msg.get("gui-info-balance", "%balance%", msg.money(balance))));
		lore.add(Msg.text(msg.get("gui-info-left", "%left%", Msg.duration(trader.leftMillis()))));
		long wanted = plugin.wanted().leftMillis(player.getUniqueId());
		if (wanted > 0L) {
			lore.add(Msg.text(msg.get("gui-info-wanted", "%left%", Msg.duration(wanted))));
		}
		lore.add(Msg.text(msg.get("list-footer", "%page%", page, "%pages%", pages)));
		return button(Material.PAPER, msg.get("gui-info-name", "%player%", player.getName()), lore);
	}

	private ItemStack button(Material material, String name, List<Component> lore) {
		ItemStack stack = new ItemStack(material, 1);
		ItemMeta meta = stack.getItemMeta();
		if (meta != null) {
			meta.displayName(Msg.text(name));
			if (!lore.isEmpty()) {
				meta.lore(new ArrayList<>(lore));
			}
			stack.setItemMeta(meta);
		}
		return stack;
	}

	// ------------------------------------------------------------------
	// покупка
	// ------------------------------------------------------------------

	/** Главная точка входа для витрины и для /bm buy. */
	public boolean attemptBuy(Player player, Lot lot) {
		Msg msg = plugin.messages();
		Catalog catalog = plugin.catalog();
		Trader trader = plugin.trader();
		EconomyLink economy = plugin.economy();

		if (!player.hasPermission("sepiolblackmarket.buy")) {
			msg.send(player, "no-permission");
			return false;
		}
		if (!trader.active()) {
			msg.send(player, "trader-absent", "%next%", Msg.duration(trader.untilArrival()));
			playSound(player, trader.soundDeny());
			return false;
		}
		if (!trader.nearby(player)) {
			msg.send(player, "shop-too-far");
			playSound(player, trader.soundDeny());
			return false;
		}
		long now = System.currentTimeMillis();
		Long last = lastBuy.get(player.getUniqueId());
		if (cooldownSeconds > 0 && last != null && now - last < cooldownSeconds * 1000L) {
			long seconds = Math.max(1L, (cooldownSeconds * 1000L - (now - last)) / 1000L);
			msg.send(player, "buy-cooldown", "%seconds%", seconds);
			return false;
		}
		if (catalog.soldOut(lot.id)) {
			msg.send(player, "buy-sold-out");
			playSound(player, trader.soundDeny());
			return false;
		}
		if (!economy.available()) {
			msg.send(player, "economy-missing");
			return false;
		}
		double price = catalog.price(lot.id);
		double balance = economy.balance(player.getUniqueId());
		if (balance + 0.0001D < price) {
			msg.send(player, "buy-not-enough", "%price%", msg.money(price), "%balance%", msg.money(balance));
			playSound(player, trader.soundDeny());
			return false;
		}
		if (lot.hasItem() && player.getInventory().firstEmpty() == -1) {
			msg.send(player, "buy-full");
			playSound(player, trader.soundDeny());
			return false;
		}
		if (!economy.withdraw(player.getUniqueId(), price, "чёрный рынок: " + lot.id)) {
			msg.send(player, "buy-failed");
			return false;
		}

		lastBuy.put(player.getUniqueId(), now);
		boolean delivered = deliver(player, lot);
		if (!delivered) {
			economy.deposit(player.getUniqueId(), price, "возврат за " + lot.id);
			msg.send(player, "buy-failed");
			return false;
		}

		double before = catalog.price(lot.id);
		catalog.registerPurchase(lot.id);
		double after = catalog.price(lot.id);
		plugin.deals().buy(player, lot, price);
		playSound(player, trader.soundBuy());
		msg.send(player, "buy-ok",
				"%item%", lot.plainName(),
				"%qty%", lot.qty,
				"%price%", msg.money(price),
				"%balance%", msg.money(economy.balance(player.getUniqueId())));
		if (after > before + 0.0001D) {
			msg.send(player, "buy-price-up", "%price%", msg.money(after));
		}
		return true;
	}

	private boolean deliver(Player player, Lot lot) {
		try {
			if (lot.hasItem()) {
				ItemStack stack = plugin.catalog().build(lot);
				Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
				for (ItemStack rest : leftovers.values()) {
					player.getWorld().dropItemNaturally(player.getLocation(), rest);
				}
			}
			for (String raw : lot.commands) {
				String command = raw
						.replace("%player%", player.getName())
						.replace("%uuid%", player.getUniqueId().toString())
						.replace("%qty%", String.valueOf(lot.qty));
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
			}
			if ("clear-wanted".equals(lot.effect)) {
				plugin.wanted().clear(player.getUniqueId());
				plugin.messages().send(player, "clean-papers-used");
			}
			return true;
		} catch (Throwable error) {
			plugin.getLogger().warning("Сделка " + lot.id + " сорвалась: " + error.getMessage());
			return false;
		}
	}

	private void playSound(Player player, String sound) {
		if (sound == null || sound.isBlank()) {
			return;
		}
		try {
			player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
		} catch (Throwable ignored) {
			// звук не критичен
		}
	}

	// ------------------------------------------------------------------
	// события
	// ------------------------------------------------------------------

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
		if (event.getClickedInventory() != top) {
			return;
		}
		int slot = event.getSlot();
		if (slot == SLOT_PREV) {
			if (holder.page > 0) {
				open(player, holder.page - 1);
			}
			return;
		}
		if (slot == SLOT_NEXT) {
			open(player, holder.page + 1);
			return;
		}
		if (slot < 0 || slot >= PAGE_SIZE || slot >= holder.lots.size()) {
			return;
		}
		Lot lot = holder.lots.get(slot);
		attemptBuy(player, lot);
		open(player, holder.page);
	}

	@EventHandler
	public void onDrag(InventoryDragEvent event) {
		if (event.getView().getTopInventory().getHolder() instanceof Holder) {
			event.setCancelled(true);
		}
	}
}
