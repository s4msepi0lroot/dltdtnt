package ru.sepiolsmp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** /sepiol reload | web | profile [nick] */
public final class SepiolCommand implements CommandExecutor, TabCompleter {

	private static final String PREFIX = ChatColor.DARK_PURPLE + "[sepiolSMP] " + ChatColor.RESET;

	private final SepiolCore plugin;

	public SepiolCommand(SepiolCore plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);

		switch (sub) {
			case "reload" -> {
				if (!sender.hasPermission("sepiolcore.admin")) {
					sender.sendMessage(PREFIX + ChatColor.RED + "Нет прав.");
					return true;
				}
				plugin.reloadEverything();
				sender.sendMessage(PREFIX + ChatColor.GREEN + "Конфиг перечитан, веб-сервер перезапущен.");
			}
			case "web" -> {
				if (!sender.hasPermission("sepiolcore.admin")) {
					sender.sendMessage(PREFIX + ChatColor.RED + "Нет прав.");
					return true;
				}
				sender.sendMessage(PREFIX + "Веб-профили: "
						+ (plugin.webRunning() ? ChatColor.GREEN + "работают" : ChatColor.RED + "выключены"));
				sender.sendMessage(PREFIX + ChatColor.GRAY + "Порт: " + plugin.getConfig().getInt("web.port", 8300)
						+ " | адрес: " + plugin.publicUrl());
			}
			case "profile" -> {
				if (!sender.hasPermission("sepiolcore.profile")) {
					sender.sendMessage(PREFIX + ChatColor.RED + "Нет прав.");
					return true;
				}
				String nick = args.length > 1 ? args[1] : sender.getName();
				sender.sendMessage(PREFIX + "Профиль " + ChatColor.AQUA + nick + ChatColor.RESET + ": "
						+ ChatColor.UNDERLINE + plugin.publicUrl() + "/?player=" + nick);
			}
			default -> {
				sender.sendMessage(PREFIX + ChatColor.GRAY + "/sepiol profile [ник] " + ChatColor.DARK_GRAY + "- ссылка на веб-профиль");
				if (sender.hasPermission("sepiolcore.admin")) {
					sender.sendMessage(PREFIX + ChatColor.GRAY + "/sepiol web " + ChatColor.DARK_GRAY + "- статус веб-сервера");
					sender.sendMessage(PREFIX + ChatColor.GRAY + "/sepiol reload " + ChatColor.DARK_GRAY + "- перезагрузка");
				}
			}
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> out = new ArrayList<>();
		if (args.length == 1) {
			out.add("profile");
			if (sender.hasPermission("sepiolcore.admin")) {
				out.add("web");
				out.add("reload");
			}
			out.removeIf(value -> !value.startsWith(args[0].toLowerCase(Locale.ROOT)));
		}
		return out;
	}
}
