package ru.sepiolsmp.economy.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sepiolsmp.economy.SepiolEconomy;
import ru.sepiolsmp.economy.econ.Accounts;
import ru.sepiolsmp.economy.econ.EconomyApi;
import ru.sepiolsmp.economy.econ.Market;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /bank — банк сезона.
 *   /bank              — открыть витрину курса
 *   /bank quote [кол-во] — сколько дадут за предмет в руке
 *   /bank sell [кол-во|all|hand]
 *   /bank list [страница]
 *   /bank info | /bank top
 */
public final class BankCommand implements CommandExecutor, TabCompleter {

	private static final List<String> SUBS = List.of("quote", "sell", "list", "info", "top", "help");
	private static final int PAGE_SIZE = 10;

	private final SepiolEconomy plugin;

	public BankCommand(SepiolEconomy plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!plugin.api().bankEnabled()) {
			plugin.msg().send(sender, "bank-disabled");
			return true;
		}

		String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

		if (sub.isEmpty()) {
			if (sender instanceof Player player && plugin.getConfig().getBoolean("bank.gui.enabled", true)) {
				plugin.menu().open(player, 0);
			} else {
				plugin.msg().send(sender, "usage-bank");
			}
			return true;
		}

		switch (sub) {
			case "quote" -> quote(sender, args);
			case "sell" -> sell(sender, args);
			case "list" -> list(sender, args);
			case "info" -> info(sender);
			case "top" -> top(sender);
			default -> plugin.msg().send(sender, "usage-bank");
		}
		return true;
	}

	// ---------------------------------------------------------------- подкоманды

	private void quote(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			plugin.msg().send(sender, "player-only");
			return;
		}
		ItemStack hand = player.getInventory().getItemInMainHand();
		if (hand == null || hand.getType().isAir()) {
			plugin.msg().send(player, "bank-empty-hand");
			return;
		}
		String id = EconomyApi.idOf(hand);
		if (plugin.market().isContraband(id)) {
			plugin.msg().send(player, "bank-contraband");
			return;
		}
		Market.Price price = plugin.market().price(id);
		if (price == null) {
			plugin.msg().send(player, "bank-not-accepted", "%item%", id);
			return;
		}

		double unit = plugin.market().unitPrice(id);
		double factor = plugin.market().factor(id);
		plugin.msg().send(player, "bank-quote",
				"%item%", price.name,
				"%price%", plugin.msg().money(unit),
				"%factor%", plugin.msg().num(factor * 100.0));

		int requested = args.length > 1 ? parseInt(args[1], 0) : 0;
		if (requested > 0) {
			Market.Quote quote = plugin.market().quote(id, requested);
			if (quote != null) {
				plugin.msg().send(player, "bank-quote-stack",
						"%qty%", String.valueOf(quote.qty),
						"%stack%", plugin.msg().money(quote.total));
			}
		}

		Market.Quote have = plugin.api().preview(player, id);
		if (have != null) {
			plugin.msg().send(player, "bank-quote-have",
					"%qty%", String.valueOf(have.qty),
					"%amount%", plugin.msg().money(have.total));
		}
	}

	private void sell(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			plugin.msg().send(sender, "player-only");
			return;
		}
		ItemStack hand = player.getInventory().getItemInMainHand();
		if (hand == null || hand.getType().isAir()) {
			plugin.msg().send(player, "bank-empty-hand");
			return;
		}
		String id = EconomyApi.idOf(hand);
		int limit = 0;
		if (args.length > 1) {
			String arg = args[1].toLowerCase(Locale.ROOT);
			if (arg.equals("hand")) {
				limit = hand.getAmount();
			} else if (!arg.equals("all")) {
				limit = parseInt(arg, -1);
				if (limit <= 0) {
					plugin.msg().send(player, "invalid-amount");
					return;
				}
			}
		}
		applySell(player, id, limit);
	}

	/** Используется и командой, и кликом в витрине. */
	public void applySell(Player player, String id, int limit) {
		EconomyApi.SellResult result = plugin.api().sell(player, id, limit);
		Market.Price price = plugin.market().price(result.id());
		String itemName = price == null ? result.id() : price.name;
		switch (result.status()) {
			case OK -> {
				plugin.msg().send(player, "bank-sold",
						"%qty%", String.valueOf(result.qty()),
						"%item%", itemName,
						"%amount%", plugin.msg().money(result.total()));
				if (result.capped()) {
					plugin.msg().send(player, "bank-sold-capped");
				}
				plugin.msg().send(player, "bank-price-drop",
						"%item%", itemName,
						"%factor%", plugin.msg().num(plugin.market().factor(result.id()) * 100.0));
			}
			case CONTRABAND -> plugin.msg().send(player, "bank-contraband");
			case NOT_ACCEPTED -> plugin.msg().send(player, "bank-not-accepted", "%item%", itemName);
			case CUSTOM_ITEM -> plugin.msg().send(player, "bank-custom-item");
			case COOLDOWN -> plugin.msg().send(player, "bank-cooldown");
			case DISABLED -> plugin.msg().send(player, "bank-disabled");
			default -> plugin.msg().send(player, "bank-nothing");
		}
	}

	private void list(CommandSender sender, String[] args) {
		List<Market.Price> prices = plugin.market().sortedByPrice();
		if (prices.isEmpty()) {
			plugin.msg().send(sender, "bank-disabled");
			return;
		}
		int pages = (prices.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		int page = args.length > 1 ? Math.max(1, Math.min(pages, parseInt(args[1], 1))) : 1;
		int from = (page - 1) * PAGE_SIZE;
		int to = Math.min(prices.size(), from + PAGE_SIZE);

		plugin.msg().send(sender, "list-header",
				"%page%", String.valueOf(page),
				"%pages%", String.valueOf(pages));
		for (int i = from; i < to; i++) {
			Market.Price price = prices.get(i);
			plugin.msg().raw(sender, plugin.msg().fill(plugin.msg().raw("list-line"),
					"%item%", price.name,
					"%price%", plugin.msg().money(plugin.market().unitPrice(price.id)),
					"%factor%", plugin.msg().num(plugin.market().factor(price.id) * 100.0)));
		}
		if (page < pages) {
			plugin.msg().raw(sender, plugin.msg().fill(plugin.msg().raw("list-footer"),
					"%next%", String.valueOf(page + 1)));
		}
	}

	private void info(CommandSender sender) {
		plugin.msg().send(sender, "bank-info-header");
		line(sender, "Позиций в курсе", String.valueOf(plugin.market().size()));
		line(sender, "Стартовый капитал", plugin.msg().money(plugin.accounts().startingBalance()));
		line(sender, "Комиссия за перевод", plugin.msg().num(plugin.api().feePercent()) + "%");
		line(sender, "Напечатано за сутки", plugin.msg().money(plugin.market().emittedToday()));
		double cap = plugin.market().dailyCap();
		line(sender, "Лимит эмиссии", cap <= 0.0 ? "без лимита" : plugin.msg().money(cap));
		if (cap > 0.0) {
			line(sender, "Осталось до лимита", plugin.msg().money(plugin.market().dailyLeft()));
		}
		line(sender, "Восстановление курса", "вдвое за " + plugin.msg().num(plugin.market().halfLifeHours()) + " ч");
		line(sender, "Всего в обороте", plugin.msg().money(plugin.accounts().supply()));
		line(sender, "Казна сезона", plugin.msg().money(plugin.accounts().treasury()));
		line(sender, "Сжегли комиссией", plugin.msg().money(plugin.accounts().burned()));
		line(sender, "Счетов открыто", String.valueOf(plugin.accounts().count()));
	}

	private void top(CommandSender sender) {
		List<Accounts.Entry> top = plugin.accounts().top(10);
		if (top.isEmpty()) {
			plugin.msg().send(sender, "top-empty");
			return;
		}
		plugin.msg().send(sender, "top-header");
		int pos = 1;
		for (Accounts.Entry entry : top) {
			plugin.msg().raw(sender, plugin.msg().fill(plugin.msg().raw("top-line"),
					"%pos%", String.valueOf(pos),
					"%player%", entry.name,
					"%amount%", plugin.msg().money(entry.balance)));
			pos++;
		}
	}

	private void line(CommandSender sender, String label, String value) {
		plugin.msg().raw(sender, plugin.msg().fill(plugin.msg().raw("bank-info-line"),
				"%label%", label,
				"%value%", value));
	}

	private static int parseInt(String raw, int fallback) {
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		List<String> out = new ArrayList<>();
		if (args.length == 1) {
			String prefix = args[0].toLowerCase(Locale.ROOT);
			for (String sub : SUBS) {
				if (sub.startsWith(prefix)) {
					out.add(sub);
				}
			}
			return out;
		}
		if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
			out.add("all");
			out.add("hand");
			out.add("64");
			return out;
		}
		if (args.length == 2 && args[0].equalsIgnoreCase("quote")) {
			out.add("1");
			out.add("64");
			return out;
		}
		if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
			out.add("1");
			out.add("2");
			out.add("3");
		}
		return out;
	}
}
