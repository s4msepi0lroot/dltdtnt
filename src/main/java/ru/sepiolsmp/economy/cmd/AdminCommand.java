package ru.sepiolsmp.economy.cmd;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.sepiolsmp.economy.SepiolEconomy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /seco — админская панель экономики.
 *   /seco give|take|set &lt;ник&gt; &lt;сумма&gt;
 *   /seco treasury [add|take &lt;сумма&gt;]
 *   /seco market reset [предмет]
 *   /seco reload | /seco info | /seco export
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

	private static final List<String> SUBS = List.of("give", "take", "set", "treasury", "market", "reload", "info", "export");

	private final SepiolEconomy plugin;

	public AdminCommand(SepiolEconomy plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!sender.hasPermission("sepioleconomy.admin")) {
			plugin.msg().send(sender, "no-permission");
			return true;
		}
		if (args.length == 0) {
			help(sender);
			return true;
		}

		switch (args[0].toLowerCase(Locale.ROOT)) {
			case "give" -> change(sender, args, "give");
			case "take" -> change(sender, args, "take");
			case "set" -> change(sender, args, "set");
			case "treasury" -> treasury(sender, args);
			case "market" -> market(sender, args);
			case "reload" -> {
				plugin.reloadEverything();
				plugin.msg().send(sender, "admin-reloaded", "%count%", String.valueOf(plugin.market().size()));
			}
			case "info" -> info(sender);
			case "export" -> {
				plugin.web().export();
				plugin.msg().plain(sender, "&aВыгрузка для веб-профиля обновлена.");
			}
			default -> help(sender);
		}
		return true;
	}

	private void change(CommandSender sender, String[] args, String mode) {
		if (args.length < 3) {
			plugin.msg().plain(sender, "&7/seco " + mode + " <ник> <сумма>");
			return;
		}
		UUID target = resolve(args[1]);
		if (target == null) {
			plugin.msg().send(sender, "unknown-player", "%player%", args[1]);
			return;
		}
		double amount = parse(args[2]);
		if (Double.isNaN(amount) || amount < 0.0) {
			plugin.msg().send(sender, "invalid-amount");
			return;
		}
		String name = plugin.accounts().nameOf(target);
		switch (mode) {
			case "give" -> {
				double added = plugin.accounts().deposit(target, amount, "admin-give:" + sender.getName());
				plugin.msg().send(sender, "admin-given",
						"%amount%", plugin.msg().money(added), "%player%", name);
			}
			case "take" -> {
				boolean ok = plugin.accounts().withdraw(target, amount, "admin-take:" + sender.getName());
				if (!ok) {
					plugin.msg().plain(sender, "&cНа счете недостаточно средств.");
					return;
				}
				plugin.msg().send(sender, "admin-taken",
						"%amount%", plugin.msg().money(amount), "%player%", name);
			}
			default -> {
				plugin.accounts().setBalance(target, amount, "admin-set:" + sender.getName());
				plugin.msg().send(sender, "admin-set",
						"%player%", name, "%amount%", plugin.msg().money(plugin.accounts().balance(target)));
			}
		}
		plugin.saveAll(false);
	}

	private void treasury(CommandSender sender, String[] args) {
		if (args.length >= 3) {
			double amount = parse(args[2]);
			if (Double.isNaN(amount) || amount <= 0.0) {
				plugin.msg().send(sender, "invalid-amount");
				return;
			}
			if (args[1].equalsIgnoreCase("add")) {
				plugin.accounts().addTreasury(amount);
			} else if (args[1].equalsIgnoreCase("take")) {
				plugin.accounts().addTreasury(-amount);
			} else {
				plugin.msg().plain(sender, "&7/seco treasury [add|take <сумма>]");
				return;
			}
			plugin.msg().send(sender, "treasury-changed", "%amount%", plugin.msg().money(plugin.accounts().treasury()));
			plugin.saveAll(false);
			return;
		}
		plugin.msg().send(sender, "treasury-info",
				"%amount%", plugin.msg().money(plugin.accounts().treasury()),
				"%burned%", plugin.msg().money(plugin.accounts().burned()));
	}

	private void market(CommandSender sender, String[] args) {
		if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
			String id = args.length >= 3 ? args[2] : "";
			plugin.market().resetSaturation(id);
			plugin.market().saveState();
			plugin.msg().send(sender, "admin-market-reset");
			return;
		}
		plugin.msg().plain(sender, "&7/seco market reset [предмет]");
	}

	private void info(CommandSender sender) {
		plugin.msg().plain(sender, "&6SepiolEconomy &7— состояние");
		plugin.msg().raw(sender, "&7Позиций курса&8: &f" + plugin.market().size());
		plugin.msg().raw(sender, "&7Счетов&8: &f" + plugin.accounts().count());
		plugin.msg().raw(sender, "&7В обороте&8: &f" + plugin.msg().money(plugin.accounts().supply()));
		plugin.msg().raw(sender, "&7Напечатано за сутки&8: &f"
				+ plugin.msg().money(plugin.market().emittedToday()) + " &8/ &f"
				+ (plugin.market().dailyCap() <= 0.0 ? "—" : plugin.msg().money(plugin.market().dailyCap())));
		plugin.msg().raw(sender, "&7Казна&8: &f" + plugin.msg().money(plugin.accounts().treasury())
				+ " &7сожжено&8: &f" + plugin.msg().money(plugin.accounts().burned()));
		plugin.msg().raw(sender, "&7Сброс лимита в&8: &f" + plugin.market().resetHour() + ":00");
	}

	private void help(CommandSender sender) {
		plugin.msg().plain(sender, "&6/seco &7give|take|set <ник> <сумма>");
		plugin.msg().raw(sender, "&6/seco &7treasury [add|take <сумма>]");
		plugin.msg().raw(sender, "&6/seco &7market reset [предмет]");
		plugin.msg().raw(sender, "&6/seco &7reload | info | export");
	}

	private UUID resolve(String name) {
		Player online = Bukkit.getPlayerExact(name);
		if (online != null) {
			return online.getUniqueId();
		}
		return plugin.accounts().byName(name);
	}

	private static double parse(String raw) {
		try {
			return Double.parseDouble(raw.replace(',', '.').trim());
		} catch (NumberFormatException ex) {
			return Double.NaN;
		}
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		List<String> out = new ArrayList<>();
		if (!sender.hasPermission("sepioleconomy.admin")) {
			return out;
		}
		if (args.length == 1) {
			String prefix = args[0].toLowerCase(Locale.ROOT);
			for (String sub : SUBS) {
				if (sub.startsWith(prefix)) {
					out.add(sub);
				}
			}
			return out;
		}
		String sub = args[0].toLowerCase(Locale.ROOT);
		if (args.length == 2 && (sub.equals("give") || sub.equals("take") || sub.equals("set"))) {
			String prefix = args[1].toLowerCase(Locale.ROOT);
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
					out.add(player.getName());
				}
			}
			return out;
		}
		if (args.length == 2 && sub.equals("treasury")) {
			out.add("add");
			out.add("take");
			return out;
		}
		if (args.length == 2 && sub.equals("market")) {
			out.add("reset");
			return out;
		}
		if (args.length == 3 && sub.equals("market") && args[1].equalsIgnoreCase("reset")) {
			String prefix = args[2].toLowerCase(Locale.ROOT);
			for (String id : plugin.market().ids()) {
				if (id.startsWith(prefix)) {
					out.add(id);
				}
				if (out.size() >= 40) {
					break;
				}
			}
		}
		return out;
	}
}
