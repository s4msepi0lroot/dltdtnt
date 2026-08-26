package ru.sepiolsmp.economy.cmd;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.sepiolsmp.economy.SepiolEconomy;
import ru.sepiolsmp.economy.econ.Accounts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /balance [ник|top] — свой счёт, счёт другого игрока или топ сезона. */
public final class BalanceCommand implements CommandExecutor, TabCompleter {

	private final SepiolEconomy plugin;

	public BalanceCommand(SepiolEconomy plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			if (!(sender instanceof Player player)) {
				plugin.msg().send(sender, "player-only");
				return true;
			}
			plugin.msg().send(player, "balance-self",
					"%amount%", plugin.msg().money(plugin.accounts().balance(player.getUniqueId())));
			return true;
		}

		if (args[0].equalsIgnoreCase("top")) {
			int size = plugin.getConfig().getInt("web.top-size", 25);
			List<Accounts.Entry> top = plugin.accounts().top(Math.min(Math.max(size, 5), 25));
			if (top.isEmpty()) {
				plugin.msg().send(sender, "top-empty");
				return true;
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
			return true;
		}

		if (!sender.hasPermission("sepioleconomy.balance.other")) {
			plugin.msg().send(sender, "no-permission");
			return true;
		}
		UUID target = resolve(args[0]);
		if (target == null) {
			plugin.msg().send(sender, "unknown-player", "%player%", args[0]);
			return true;
		}
		plugin.msg().send(sender, "balance-other",
				"%player%", plugin.accounts().nameOf(target),
				"%amount%", plugin.msg().money(plugin.accounts().balance(target)));
		return true;
	}

	private UUID resolve(String name) {
		Player online = Bukkit.getPlayerExact(name);
		if (online != null) {
			return online.getUniqueId();
		}
		return plugin.accounts().byName(name);
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		List<String> out = new ArrayList<>();
		if (args.length != 1) {
			return out;
		}
		String prefix = args[0].toLowerCase(Locale.ROOT);
		if ("top".startsWith(prefix)) {
			out.add("top");
		}
		if (!sender.hasPermission("sepioleconomy.balance.other")) {
			return out;
		}
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
				out.add(player.getName());
			}
		}
		return out;
	}
}
