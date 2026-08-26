package ru.sepiolsmp.economy.cmd;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.sepiolsmp.economy.SepiolEconomy;
import ru.sepiolsmp.economy.econ.EconomyApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /pay &lt;ник&gt; &lt;сумма&gt; — перевод с комиссией в казну сезона. */
public final class PayCommand implements CommandExecutor, TabCompleter {

	private final SepiolEconomy plugin;

	public PayCommand(SepiolEconomy plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			plugin.msg().send(sender, "player-only");
			return true;
		}
		if (args.length < 2) {
			plugin.msg().send(player, "usage-pay");
			return true;
		}

		UUID target = resolve(args[0]);
		if (target == null) {
			plugin.msg().send(player, "unknown-player", "%player%", args[0]);
			return true;
		}

		double amount;
		try {
			amount = Double.parseDouble(args[1].replace(',', '.'));
		} catch (NumberFormatException ex) {
			plugin.msg().send(player, "invalid-amount");
			return true;
		}
		if (amount <= 0.0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
			plugin.msg().send(player, "invalid-amount");
			return true;
		}

		EconomyApi.PayResult result = plugin.api().pay(player.getUniqueId(), target, amount);
		String targetName = plugin.accounts().nameOf(target);
		switch (result.status()) {
			case OK -> {
				plugin.msg().send(player, "pay-sent",
						"%amount%", plugin.msg().money(result.amount()),
						"%player%", targetName,
						"%fee%", plugin.msg().money(result.fee()));
				Player online = Bukkit.getPlayer(target);
				if (online != null) {
					plugin.msg().send(online, "pay-received",
							"%amount%", plugin.msg().money(result.amount()),
							"%player%", player.getName());
				}
			}
			case DISABLED -> plugin.msg().send(player, "pay-disabled");
			case SELF -> plugin.msg().send(player, "pay-self");
			case TOO_SMALL -> plugin.msg().send(player, "pay-too-small",
					"%min%", plugin.msg().money(plugin.api().minPayAmount()));
			case NOT_ENOUGH -> plugin.msg().send(player, "pay-not-enough",
					"%need%", plugin.msg().money(result.amount() + result.fee()));
			case COOLDOWN -> plugin.msg().send(player, "pay-cooldown",
					"%seconds%", String.valueOf(result.waitSeconds()));
			case OFFLINE -> plugin.msg().send(player, "pay-offline");
			case MAX_BALANCE -> plugin.msg().send(player, "pay-max-balance");
			default -> plugin.msg().send(player, "unknown-player", "%player%", args[0]);
		}
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
		if (args.length == 1) {
			String prefix = args[0].toLowerCase(Locale.ROOT);
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
					out.add(player.getName());
				}
			}
			return out;
		}
		if (args.length == 2) {
			out.add("10");
			out.add("50");
			out.add("100");
		}
		return out;
	}
}
