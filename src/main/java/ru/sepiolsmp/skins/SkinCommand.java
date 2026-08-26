package ru.sepiolsmp.skins;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import ru.sepiolsmp.skins.model.SkinData;

/** /skin - info, update, from, url, clear, reload, purge. */
public final class SkinCommand implements CommandExecutor, TabCompleter {

	private static final List<String> SUBS =
			Arrays.asList("info", "update", "from", "url", "clear", "reload", "purge", "help");

	private final SepiolSkins plugin;

	public SkinCommand(SepiolSkins plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!sender.hasPermission("sepiolskins.use")) {
			sender.sendMessage(plugin.message("no-permission"));
			return true;
		}
		String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);

		switch (sub) {
			case "help":
				help(sender, label);
				return true;
			case "info":
				info(sender, args.length > 1 ? args[1] : selfName(sender));
				return true;
			case "update":
				update(sender, args.length > 1 ? args[1] : selfName(sender));
				return true;
			case "from":
				if (args.length < 2) {
					sender.sendMessage(plugin.color("&7Использование: &f/" + label + " from <ник>"));
					return true;
				}
				from(sender, args.length > 2 ? args[2] : selfName(sender), args[1]);
				return true;
			case "url":
				if (args.length < 2) {
					sender.sendMessage(plugin.color("&7Использование: &f/" + label
							+ " url <ссылка на png> [slim]"));
					return true;
				}
				url(sender, selfName(sender), args[1],
						args.length > 2 && args[2].equalsIgnoreCase("slim") ? "slim" : "classic");
				return true;
			case "clear":
				clear(sender, args.length > 1 ? args[1] : selfName(sender));
				return true;
			case "reload":
				if (!sender.hasPermission("sepiolskins.admin")) {
					sender.sendMessage(plugin.message("no-permission"));
					return true;
				}
				plugin.reloadEverything();
				sender.sendMessage(plugin.color("&aКонфиг перезагружен."));
				return true;
			case "purge":
				if (!sender.hasPermission("sepiolskins.admin")) {
					sender.sendMessage(plugin.message("no-permission"));
					return true;
				}
				int removed = plugin.store().purge();
				sender.sendMessage(plugin.color("&aКеш очищен: &f" + removed + "&a записей."));
				return true;
			default:
				help(sender, label);
				return true;
		}
	}

	// ------------------------------------------------------------ subcommands

	private void help(CommandSender sender, String label) {
		sender.sendMessage(plugin.color("&dСкины sepiolSMP"));
		sender.sendMessage(plugin.color("&f/" + label + " info [ник] &7— что сейчас стоит и откуда"));
		sender.sendMessage(plugin.color("&f/" + label + " update [ник] &7— пересканировать лицензию и Ely.by"));
		sender.sendMessage(plugin.color("&f/" + label + " from <ник> &7— взять скин другого ника"));
		sender.sendMessage(plugin.color("&f/" + label + " url <png> [slim] &7— свой скин по ссылке"));
		sender.sendMessage(plugin.color("&f/" + label + " clear [ник] &7— сбросить"));
	}

	private void info(CommandSender sender, String name) {
		if (name == null) {
			sender.sendMessage(plugin.color("&7Укажи ник: &f/skin info <ник>"));
			return;
		}
		if (!isSelf(sender, name) && !sender.hasPermission("sepiolskins.other")) {
			sender.sendMessage(plugin.message("no-permission"));
			return;
		}
		SkinData data = plugin.store().get(name);
		if (data == null || !data.hasTextures()) {
			sender.sendMessage(plugin.color("&7У &f" + name + "&7 скина в кеше нет."));
			return;
		}
		long minutes = Duration.between(Instant.ofEpochMilli(data.fetchedAt), Instant.now()).toMinutes();
		sender.sendMessage(plugin.color("&d" + name + "&7: источник &f" + data.source
				+ "&7, модель &f" + (data.model == null ? "classic" : data.model)
				+ "&7, обновлён &f" + minutes + " мин &7назад"
				+ (data.pinned ? "&7, закреплён вручную" : "")));
	}

	private void update(CommandSender sender, String name) {
		if (name == null) {
			sender.sendMessage(plugin.color("&7Укажи ник: &f/skin update <ник>"));
			return;
		}
		if (!isSelf(sender, name) && !sender.hasPermission("sepiolskins.other")) {
			sender.sendMessage(plugin.message("no-permission"));
			return;
		}
		sender.sendMessage(plugin.message("searching"));
		plugin.resolver().async(() -> {
			SkinData data = plugin.resolver().resolve(name, true);
			deliver(sender, name, data);
		});
	}

	private void from(CommandSender sender, String target, String donor) {
		if (target == null) {
			sender.sendMessage(plugin.color("&7Из консоли: &f/skin from <ник-источник> <кому>"));
			return;
		}
		if (!isSelf(sender, target) && !sender.hasPermission("sepiolskins.other")) {
			sender.sendMessage(plugin.message("no-permission"));
			return;
		}
		sender.sendMessage(plugin.message("searching"));
		plugin.resolver().async(() -> deliver(sender, target, plugin.resolver().applyFrom(target, donor)));
	}

	private void url(CommandSender sender, String target, String link, String model) {
		if (target == null) {
			sender.sendMessage(plugin.color("&7Только для игроков."));
			return;
		}
		if (!link.startsWith("http://") && !link.startsWith("https://")) {
			sender.sendMessage(plugin.color("&cНужна прямая ссылка на png."));
			return;
		}
		sender.sendMessage(plugin.message("searching"));
		plugin.resolver().async(() -> deliver(sender, target, plugin.resolver().applyUrl(target, link, model)));
	}

	private void clear(CommandSender sender, String name) {
		if (name == null) {
			sender.sendMessage(plugin.color("&7Укажи ник: &f/skin clear <ник>"));
			return;
		}
		if (!isSelf(sender, name) && !sender.hasPermission("sepiolskins.other")) {
			sender.sendMessage(plugin.message("no-permission"));
			return;
		}
		plugin.store().remove(name);
		sender.sendMessage(plugin.message("cleared"));
		sender.sendMessage(plugin.message("relog"));
	}

	// ---------------------------------------------------------------- helpers

	private void deliver(CommandSender sender, String name, SkinData data) {
		if (data == null || !data.hasTextures()) {
			Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.message("not-found")));
			return;
		}
		Bukkit.getScheduler().runTask(plugin, () -> {
			sender.sendMessage(plugin.message("applied").replace("%source%", String.valueOf(data.source)));
			Player online = Bukkit.getPlayerExact(name);
			if (online == null || !plugin.applyLive(online, data)) {
				sender.sendMessage(plugin.message("relog"));
			}
		});
	}

	private String selfName(CommandSender sender) {
		return sender instanceof Player player ? player.getName() : null;
	}

	private boolean isSelf(CommandSender sender, String name) {
		return sender instanceof Player player && player.getName().equalsIgnoreCase(name);
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			List<String> out = new ArrayList<>();
			for (String sub : SUBS) {
				if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
					out.add(sub);
				}
			}
			return out;
		}
		if (args.length == 2 && !args[0].equalsIgnoreCase("url")) {
			List<String> out = new ArrayList<>();
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
					out.add(player.getName());
				}
			}
			return out;
		}
		return List.of();
	}
}
