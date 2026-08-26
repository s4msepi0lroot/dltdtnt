package ru.sepiolsmp.blackmarket.cmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.sepiolsmp.blackmarket.SepiolBlackMarket;
import ru.sepiolsmp.blackmarket.econ.EconomyLink;
import ru.sepiolsmp.blackmarket.market.Catalog;
import ru.sepiolsmp.blackmarket.market.Lot;
import ru.sepiolsmp.blackmarket.market.Trader;
import ru.sepiolsmp.blackmarket.market.Wanted;
import ru.sepiolsmp.blackmarket.util.Msg;

/** /blackmarket - всё управление рынком: игрокам, стаффу и админам. */
public final class BlackMarketCommand implements CommandExecutor, TabCompleter {

	private static final List<String> SUBS = List.of(
			"info", "find", "where", "shop", "buy", "list", "wanted",
			"scan", "raid", "clear", "spawn", "despawn", "restock", "reload", "export", "help");

	private final SepiolBlackMarket plugin;

	public BlackMarketCommand(SepiolBlackMarket plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		Msg msg = plugin.messages();
		if (!sender.hasPermission("sepiolblackmarket.use")) {
			msg.send(sender, "no-permission");
			return true;
		}
		if (args.length == 0) {
			info(sender);
			return true;
		}
		switch (args[0].toLowerCase(Locale.ROOT)) {
			case "info", "статус" -> info(sender);
			case "find", "найти" -> find(sender);
			case "where", "где" -> where(sender);
			case "shop", "рынок" -> shop(sender);
			case "buy", "купить" -> buy(sender, args);
			case "list", "лоты" -> list(sender, args);
			case "wanted", "розыск" -> wanted(sender, args);
			case "scan", "досмотр" -> scan(sender, args);
			case "raid", "облава" -> raid(sender, args);
			case "clear" -> clear(sender, args);
			case "spawn" -> spawn(sender, args);
			case "despawn" -> despawn(sender);
			case "restock" -> restock(sender);
			case "reload" -> reload(sender);
			case "export" -> export(sender);
			case "help", "?" -> help(sender);
			default -> msg.send(sender, "usage");
		}
		return true;
	}

	// ------------------------------------------------------------------
	// игрок
	// ------------------------------------------------------------------

	private void info(CommandSender sender) {
		Msg msg = plugin.messages();
		Trader trader = plugin.trader();
		Catalog catalog = plugin.catalog();
		Wanted wanted = plugin.wanted();
		EconomyLink economy = plugin.economy();

		msg.send(sender, "info-header");
		if (!trader.enabled()) {
			msg.send(sender, "disabled");
		}
		line(sender, "trader", msg.raw(trader.active() ? "info-labels.here" : "info-labels.away"));
		if (trader.active()) {
			line(sender, "left", Msg.duration(trader.leftMillis()));
		} else {
			line(sender, "next", Msg.duration(trader.untilArrival()));
		}
		line(sender, "lots", catalog.size());
		line(sender, "wanted", wanted.count());
		line(sender, "economy", economy.modeName());
		if (sender instanceof Player player) {
			if (economy.available()) {
				line(sender, "balance", msg.money(economy.balance(player.getUniqueId())));
			}
			boolean visible = trader.knows(player.getUniqueId()) || player.hasPermission("sepiolblackmarket.staff");
			if (trader.active()) {
				line(sender, "coords", visible ? trader.coordsText() : msg.raw("info-labels.hidden"));
			}
		} else if (trader.active()) {
			line(sender, "coords", trader.coordsText());
		}
	}

	private void line(CommandSender to, String labelKey, Object value) {
		Msg msg = plugin.messages();
		msg.plain(to, msg.get("info-line", "%label%", msg.raw("info-labels." + labelKey), "%value%", value));
	}

	private void find(CommandSender sender) {
		Msg msg = plugin.messages();
		if (!(sender instanceof Player player)) {
			msg.send(sender, "player-only");
			return;
		}
		if (!player.hasPermission("sepiolblackmarket.find")) {
			msg.send(sender, "no-permission");
			return;
		}
		Trader trader = plugin.trader();
		if (!trader.enabled()) {
			msg.send(sender, "disabled");
			return;
		}
		if (!trader.active()) {
			msg.send(player, "trader-absent", "%next%", Msg.duration(trader.untilArrival()));
			return;
		}
		if (trader.location() == null) {
			msg.send(player, "coords-none");
			return;
		}
		if (trader.knows(player.getUniqueId())) {
			msg.send(player, "coords-already", "%coords%", trader.coordsText());
			return;
		}
		double price = trader.coordsPrice();
		if (price <= 0.0D) {
			trader.grantCoords(player.getUniqueId());
			msg.send(player, "coords-free", "%coords%", trader.coordsText());
			return;
		}
		EconomyLink economy = plugin.economy();
		if (!economy.available()) {
			msg.send(player, "economy-missing");
			return;
		}
		double balance = economy.balance(player.getUniqueId());
		if (balance + 0.0001D < price) {
			msg.send(player, "coords-not-enough", "%price%", msg.money(price), "%balance%", msg.money(balance));
			return;
		}
		if (!economy.withdraw(player.getUniqueId(), price, "координаты барыги")) {
			msg.send(player, "buy-failed");
			return;
		}
		trader.grantCoords(player.getUniqueId());
		plugin.deals().coords(player, price, trader.coordsText());
		msg.send(player, "coords-bought",
				"%coords%", trader.coordsText(),
				"%price%", msg.money(price),
				"%balance%", msg.money(economy.balance(player.getUniqueId())),
				"%left%", Msg.duration(trader.leftMillis()));
	}

	private void where(CommandSender sender) {
		Msg msg = plugin.messages();
		Trader trader = plugin.trader();
		if (!trader.active()) {
			msg.send(sender, "trader-absent", "%next%", Msg.duration(trader.untilArrival()));
			return;
		}
		boolean staff = sender.hasPermission("sepiolblackmarket.staff");
		if (!(sender instanceof Player player)) {
			msg.send(sender, "trader-present", "%coords%", trader.coordsText(), "%left%", Msg.duration(trader.leftMillis()));
			return;
		}
		if (trader.revealed() || staff) {
			msg.send(player, "trader-present", "%coords%", trader.coordsText(), "%left%", Msg.duration(trader.leftMillis()));
			return;
		}
		if (trader.knows(player.getUniqueId())) {
			msg.send(player, "trader-known", "%coords%", trader.coordsText(), "%left%", Msg.duration(trader.leftMillis()));
			return;
		}
		msg.send(player, "coords-unknown", "%price%", msg.money(trader.coordsPrice()));
	}

	private void shop(CommandSender sender) {
		Msg msg = plugin.messages();
		if (!(sender instanceof Player player)) {
			msg.send(sender, "player-only");
			return;
		}
		Trader trader = plugin.trader();
		if (!trader.active()) {
			msg.send(player, "trader-absent", "%next%", Msg.duration(trader.untilArrival()));
			return;
		}
		if (!trader.nearby(player)) {
			msg.send(player, "shop-too-far");
			return;
		}
		plugin.menu().open(player, 0);
	}

	private void buy(CommandSender sender, String[] args) {
		Msg msg = plugin.messages();
		if (!(sender instanceof Player player)) {
			msg.send(sender, "player-only");
			return;
		}
		if (args.length < 2) {
			msg.send(sender, "usage");
			return;
		}
		Lot lot = plugin.catalog().lot(args[1]);
		if (lot == null) {
			msg.send(sender, "buy-unknown-lot", "%item%", args[1]);
			return;
		}
		plugin.menu().attemptBuy(player, lot);
	}

	private void list(CommandSender sender, String[] args) {
		Msg msg = plugin.messages();
		Catalog catalog = plugin.catalog();
		List<Lot> lots = new ArrayList<>(catalog.lots());
		if (lots.isEmpty()) {
			msg.send(sender, "list-empty");
			return;
		}
		int perPage = 10;
		int pages = Math.max(1, (lots.size() + perPage - 1) / perPage);
		int page = 1;
		if (args.length > 1) {
			Integer parsed = number(sender, args[1]);
			if (parsed == null) {
				return;
			}
			page = Math.max(1, Math.min(pages, parsed));
		}
		msg.send(sender, "list-header", "%count%", lots.size());
		int from = (page - 1) * perPage;
		int to = Math.min(lots.size(), from + perPage);
		for (int index = from; index < to; index++) {
			Lot lot = lots.get(index);
			int left = catalog.left(lot.id);
			msg.plain(sender, msg.get("list-line",
					"%name%", lot.plainName(),
					"%item%", lot.id,
					"%price%", msg.money(catalog.price(lot.id)),
					"%qty%", lot.qty,
					"%left%", left < 0 ? msg.raw("gui-unlimited") : left,
					"%label%", lot.contraband ? msg.raw("gui-contraband") : msg.raw("gui-legal")));
		}
		msg.plain(sender, msg.get("list-footer", "%page%", page, "%pages%", pages));
	}

	// ------------------------------------------------------------------
	// стафф
	// ------------------------------------------------------------------

	private void wanted(CommandSender sender, String[] args) {
		Msg msg = plugin.messages();
		Wanted wanted = plugin.wanted();
		if (args.length == 1) {
			if (!(sender instanceof Player player)) {
				showWantedList(sender);
				return;
			}
			long left = wanted.leftMillis(player.getUniqueId());
			if (left > 0L) {
				msg.send(player, "wanted-self", "%left%", Msg.duration(left), "%minutes%", Math.max(1L, left / 60_000L));
			} else {
				msg.send(player, "wanted-none");
			}
			return;
		}
		if (args[1].equalsIgnoreCase("list") || args[1].equalsIgnoreCase("список")) {
			if (!sender.hasPermission("sepiolblackmarket.staff")) {
				msg.send(sender, "no-permission");
				return;
			}
			showWantedList(sender);
			return;
		}
		if (!sender.hasPermission("sepiolblackmarket.staff")) {
			msg.send(sender, "no-permission");
			return;
		}
		Player target = Bukkit.getPlayerExact(args[1]);
		if (target == null) {
			msg.send(sender, "unknown-player", "%player%", args[1]);
			return;
		}
		int minutes = plugin.getConfig().getInt("wanted.minutes", 30);
		if (args.length > 2) {
			Integer parsed = number(sender, args[2]);
			if (parsed == null) {
				return;
			}
			minutes = parsed;
		}
		int applied = wanted.markMinutes(target.getUniqueId(), target.getName(), minutes);
		plugin.deals().wanted(target.getName(), applied, "команда " + sender.getName());
		msg.send(sender, "wanted-set", "%player%", target.getName(), "%minutes%", applied);
	}

	private void showWantedList(CommandSender sender) {
		Msg msg = plugin.messages();
		List<Wanted.Entry> entries = plugin.wanted().list();
		if (entries.isEmpty()) {
			msg.send(sender, "wanted-list-empty");
			return;
		}
		msg.send(sender, "wanted-list-header", "%count%", entries.size());
		long now = System.currentTimeMillis();
		for (Wanted.Entry entry : entries) {
			msg.plain(sender, msg.get("wanted-list-line",
					"%player%", entry.name,
					"%name%", entry.name,
					"%left%", Msg.duration(entry.until - now),
					"%minutes%", Math.max(1L, (entry.until - now) / 60_000L),
					"%count%", entry.level));
		}
	}

	private void scan(CommandSender sender, String[] args) {
		Msg msg = plugin.messages();
		if (!sender.hasPermission("sepiolblackmarket.scan")) {
			msg.send(sender, "no-permission");
			return;
		}
		if (args.length < 2) {
			msg.send(sender, "usage");
			return;
		}
		Player target = Bukkit.getPlayerExact(args[1]);
		if (target == null) {
			msg.send(sender, "unknown-player", "%player%", args[1]);
			return;
		}
		int items = plugin.catalog().countContraband(target);
		plugin.deals().check(sender.getName(), target.getName(), items, false);
		if (items <= 0) {
			msg.send(sender, "scan-clean", "%player%", target.getName());
			return;
		}
		msg.send(sender, "scan-dirty", "%player%", target.getName(), "%count%", items);
		msg.send(target, "scan-victim", "%count%", items);
		if (plugin.getConfig().getBoolean("scan.auto-wanted", true)) {
			int minutes = plugin.wanted().mark(target, items);
			plugin.wanted().announceCatch(target.getName(), items);
			plugin.deals().wanted(target.getName(), minutes, "досмотр " + sender.getName());
		}
	}

	private void raid(CommandSender sender, String[] args) {
		Msg msg = plugin.messages();
		if (!sender.hasPermission("sepiolblackmarket.raid")) {
			msg.send(sender, "no-permission");
			return;
		}
		if (args.length < 2) {
			msg.send(sender, "usage");
			return;
		}
		Player target = Bukkit.getPlayerExact(args[1]);
		if (target == null) {
			msg.send(sender, "unknown-player", "%player%", args[1]);
			return;
		}
		int items = plugin.catalog().countContraband(target);
		if (items <= 0) {
			plugin.deals().check(sender.getName(), target.getName(), 0, true);
			msg.send(sender, "raid-clean", "%player%", target.getName());
			return;
		}
		int taken = plugin.getConfig().getBoolean("raid.confiscate", true)
				? plugin.catalog().confiscate(target)
				: 0;
		int minutes = plugin.wanted().markMinutes(
				target.getUniqueId(),
				target.getName(),
				plugin.getConfig().getInt("raid.wanted-minutes", 45));
		plugin.deals().check(sender.getName(), target.getName(), taken, true);
		plugin.deals().wanted(target.getName(), minutes, "облава " + sender.getName());
		msg.send(sender, "raid-done", "%player%", target.getName(), "%count%", taken, "%minutes%", minutes);
		msg.send(target, "raid-victim", "%count%", taken, "%minutes%", minutes);
		if (plugin.getConfig().getBoolean("raid.announce", true)) {
			msg.broadcast("raid-broadcast", "%player%", target.getName(), "%count%", taken);
		}
	}

	private void clear(CommandSender sender, String[] args) {
		Msg msg = plugin.messages();
		if (!sender.hasPermission("sepiolblackmarket.staff")) {
			msg.send(sender, "no-permission");
			return;
		}
		if (args.length < 2) {
			msg.send(sender, "usage");
			return;
		}
		if (args[1].equalsIgnoreCase("all") || args[1].equalsIgnoreCase("все")) {
			List<Wanted.Entry> entries = plugin.wanted().list();
			for (Wanted.Entry entry : entries) {
				plugin.wanted().clear(entry.id);
			}
			msg.send(sender, "admin-cleared", "%count%", entries.size());
			return;
		}
		Player online = Bukkit.getPlayerExact(args[1]);
		if (online != null) {
			plugin.wanted().clear(online.getUniqueId());
			msg.send(sender, "wanted-clear", "%player%", online.getName());
			return;
		}
		for (Wanted.Entry entry : plugin.wanted().list()) {
			if (entry.name.equalsIgnoreCase(args[1])) {
				plugin.wanted().clear(entry.id);
				msg.send(sender, "wanted-clear", "%player%", entry.name);
				return;
			}
		}
		msg.send(sender, "unknown-player", "%player%", args[1]);
	}

	// ------------------------------------------------------------------
	// админ
	// ------------------------------------------------------------------

	private void spawn(CommandSender sender, String[] args) {
		Msg msg = plugin.messages();
		if (!sender.hasPermission("sepiolblackmarket.admin")) {
			msg.send(sender, "no-permission");
			return;
		}
		boolean here = args.length > 1 && (args[1].equalsIgnoreCase("here") || args[1].equalsIgnoreCase("тут"));
		boolean ok;
		if (here && sender instanceof Player player) {
			ok = plugin.trader().spawn(player.getLocation());
		} else {
			ok = plugin.trader().spawn(null);
		}
		if (ok) {
			msg.send(sender, "admin-spawned", "%coords%", plugin.trader().coordsText());
		} else {
			msg.send(sender, "trader-no-place", "%radius%", plugin.getConfig().getInt("trader.radius", 5200));
		}
	}

	private void despawn(CommandSender sender) {
		Msg msg = plugin.messages();
		if (!sender.hasPermission("sepiolblackmarket.admin")) {
			msg.send(sender, "no-permission");
			return;
		}
		plugin.trader().despawn(false);
		msg.send(sender, "admin-despawned", "%next%", Msg.duration(plugin.trader().untilArrival()));
	}

	private void restock(CommandSender sender) {
		Msg msg = plugin.messages();
		if (!sender.hasPermission("sepiolblackmarket.admin")) {
			msg.send(sender, "no-permission");
			return;
		}
		plugin.catalog().restock();
		msg.send(sender, "admin-restocked", "%count%", plugin.catalog().size());
	}

	private void reload(CommandSender sender) {
		Msg msg = plugin.messages();
		if (!sender.hasPermission("sepiolblackmarket.admin")) {
			msg.send(sender, "no-permission");
			return;
		}
		plugin.reloadEverything();
		plugin.messages().send(sender, "admin-reloaded", "%count%", plugin.catalog().size());
	}

	private void export(CommandSender sender) {
		Msg msg = plugin.messages();
		if (!sender.hasPermission("sepiolblackmarket.admin")) {
			msg.send(sender, "no-permission");
			return;
		}
		plugin.web().export();
		msg.send(sender, "admin-exported", "%value%", plugin.web().fileName(), "%name%", plugin.web().fileName());
	}

	private void help(CommandSender sender) {
		Msg msg = plugin.messages();
		List<String> lines = msg.lines("help");
		if (lines.isEmpty()) {
			msg.send(sender, "usage");
			return;
		}
		for (String line : lines) {
			msg.plain(sender, line);
		}
	}

	private Integer number(CommandSender sender, String raw) {
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException ignored) {
			plugin.messages().send(sender, "invalid-number", "%value%", raw);
			return null;
		}
	}

	// ------------------------------------------------------------------
	// автодополнение
	// ------------------------------------------------------------------

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return filter(SUBS, args[0]);
		}
		String sub = args[0].toLowerCase(Locale.ROOT);
		if (args.length == 2) {
			switch (sub) {
				case "buy", "купить" -> {
					return filter(plugin.catalog().ids(), args[1]);
				}
				case "scan", "досмотр", "raid", "облава", "wanted", "розыск", "clear" -> {
					List<String> names = new ArrayList<>();
					for (Player online : Bukkit.getOnlinePlayers()) {
						names.add(online.getName());
					}
					if ("clear".equals(sub)) {
						names.add("all");
					}
					if ("wanted".equals(sub)) {
						names.add("list");
					}
					return filter(names, args[1]);
				}
				case "spawn" -> {
					return filter(List.of("here"), args[1]);
				}
				default -> {
					return Collections.emptyList();
				}
			}
		}
		return Collections.emptyList();
	}

	private static List<String> filter(List<String> source, String prefix) {
		String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>();
		for (String value : source) {
			if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
				out.add(value);
			}
		}
		return out;
	}
}
