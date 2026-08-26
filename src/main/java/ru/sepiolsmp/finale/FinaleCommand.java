package ru.sepiolsmp.finale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.sepiolsmp.finale.phase.Phase;
import ru.sepiolsmp.finale.util.Broadcast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * /finale - edinstvennaya tochka vhoda v apokalipsis.
 *
 * Zashchita ot sluchaynogo zapuska: snachala /finale arm vydaet odnorazovyy kod,
 * i tolko /finale start <kod> zapuskaet scenariy. Repeticiya (/finale dry)
 * koda ne trebuet, potomu chto nichego ne lomaet.
 */
public final class FinaleCommand implements CommandExecutor, TabCompleter {

	private static final List<String> SUBS =
			List.of("status", "arm", "start", "dry", "skip", "phase", "abort", "restore", "reload", "help");

	private final SepiolFinale plugin;
	private final Random random = new Random();

	private String code;
	private long codeExpiresAt;

	public FinaleCommand(SepiolFinale plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!sender.hasPermission("sepiolfinale.admin")) {
			reply(sender, plugin.msg("no-permission"));
			return true;
		}
		String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
		switch (sub) {
			case "status", "info" -> status(sender);
			case "arm" -> arm(sender);
			case "start", "go" -> start(sender, args, false);
			case "dry", "dry-run", "test" -> start(sender, args, true);
			case "skip", "next" -> skip(sender);
			case "phase" -> phase(sender, args);
			case "abort", "stop" -> abort(sender);
			case "restore" -> restore(sender);
			case "reload" -> {
				plugin.reloadAll();
				reply(sender, "&aКонфиг перечитан.");
			}
			default -> help(sender);
		}
		return true;
	}

	private void status(CommandSender sender) {
		for (String line : plugin.engine().statusLines()) {
			reply(sender, line);
		}
		if (!plugin.engine().phase().running()) {
			reply(sender, "&7Запуск: &f/finale arm&7, затем &f/finale start <код>&7. Репетиция: &f/finale dry");
		}
	}

	private void arm(CommandSender sender) {
		if (plugin.engine().phase().running()) {
			reply(sender, plugin.msg("already-running").replace("%phase%", plugin.engine().phase().title()));
			return;
		}
		int ttl = Math.max(30, plugin.getConfig().getInt("safety.code-ttl-seconds", 300));
		code = String.format("%04d", random.nextInt(10000));
		codeExpiresAt = System.currentTimeMillis() + ttl * 1000L;
		reply(sender, plugin.msg("armed")
				.replace("%code%", code)
				.replace("%ttl%", String.valueOf(ttl)));
		plugin.getLogger().warning("Финал заряжен игроком " + sender.getName() + ". Код: " + code);
	}

	private void start(CommandSender sender, String[] args, boolean rehearsal) {
		if (plugin.engine().phase().running()) {
			reply(sender, plugin.msg("already-running").replace("%phase%", plugin.engine().phase().title()));
			return;
		}
		boolean needCode = !rehearsal && plugin.getConfig().getBoolean("safety.require-code", true);
		if (needCode && !codeMatches(args)) {
			reply(sender, plugin.msg("bad-code"));
			return;
		}
		code = null;
		if (!plugin.engine().start(rehearsal)) {
			reply(sender, "&cНе смог запустить финал, смотри консоль.");
			return;
		}
		if (rehearsal) {
			double scale = plugin.getConfig().getDouble("dry-run.time-scale", 0.1);
			String times = scale > 0 ? String.valueOf(Math.round(1.0 / scale)) : "1";
			reply(sender, plugin.msg("dry-run-started").replace("%scale%", times));
		}
		plugin.getLogger().warning((rehearsal ? "Репетиция финала" : "ФИНАЛ")
				+ " запущен игроком " + sender.getName() + ".");
	}

	private void skip(CommandSender sender) {
		if (!plugin.engine().phase().running()) {
			reply(sender, plugin.msg("not-running"));
			return;
		}
		plugin.engine().advance();
		reply(sender, "&aПерешёл к фазе: &f" + plugin.engine().phase().title());
	}

	private void phase(CommandSender sender, String[] args) {
		if (args.length < 2) {
			reply(sender, "&7Фазы: &fomens, rift, convergence, ash, silence");
			return;
		}
		Phase target = Phase.fromKey(args[1]);
		if (target == Phase.IDLE) {
			reply(sender, "&cНе знаю такую фазу. Доступно: omens, rift, convergence, ash, silence");
			return;
		}
		plugin.engine().jumpTo(target);
		reply(sender, "&aФаза включена вручную: &f" + target.title());
	}

	private void abort(CommandSender sender) {
		if (!plugin.engine().abort()) {
			reply(sender, plugin.msg("not-running"));
		}
	}

	private void restore(CommandSender sender) {
		if (plugin.engine().restoreWorld()) {
			reply(sender, plugin.msg("restored"));
		} else {
			reply(sender, "&cСнимка мира нет - восстанавливать нечего.");
		}
	}

	private void help(CommandSender sender) {
		reply(sender, "&8--- &4/finale &8---");
		reply(sender, "&f/finale status &7- где мы сейчас");
		reply(sender, "&f/finale dry &7- репетиция: все фазы быстро и без последствий");
		reply(sender, "&f/finale arm &7- выдать код запуска");
		reply(sender, "&f/finale start <код> &7- начать настоящий финал");
		reply(sender, "&f/finale skip &7- следующая фаза сразу");
		reply(sender, "&f/finale phase <имя> &7- включить конкретную фазу");
		reply(sender, "&f/finale abort &7- отмена и откат настроек мира");
		reply(sender, "&f/finale restore &7- вернуть настройки мира из снимка");
		reply(sender, "&f/finale reload &7- перечитать config.yml");
	}

	private boolean codeMatches(String[] args) {
		if (code == null || args.length < 2) {
			return false;
		}
		if (System.currentTimeMillis() > codeExpiresAt) {
			code = null;
			return false;
		}
		return code.equals(args[1].trim());
	}

	private void reply(CommandSender sender, String legacy) {
		if (legacy == null || legacy.isBlank()) {
			return;
		}
		sender.sendMessage(Broadcast.text(legacy));
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (!sender.hasPermission("sepiolfinale.admin")) {
			return List.of();
		}
		if (args.length == 1) {
			return filter(SUBS, args[0]);
		}
		if (args.length == 2 && args[0].equalsIgnoreCase("phase")) {
			return filter(List.of("omens", "rift", "convergence", "ash", "silence"), args[1]);
		}
		return List.of();
	}

	private static List<String> filter(List<String> options, String prefix) {
		String lower = prefix.toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>();
		for (String option : options) {
			if (option.startsWith(lower)) {
				out.add(option);
			}
		}
		return out;
	}
}
