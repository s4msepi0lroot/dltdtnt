package ru.sepiolsmp.finale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.sepiolsmp.finale.phase.FinaleEngine;
import ru.sepiolsmp.finale.phase.Phase;
import ru.sepiolsmp.finale.util.Broadcast;
import ru.sepiolsmp.finale.util.StateStore;

import java.io.File;

/**
 * SepiolFinale - upravlyaemyy konec sezona.
 *
 * Plagin specialno spit do komandy: on mozhet spokoyno lezhat v plugins/ ves sezon
 * i nichego ne delat. V den finala osnovatel pishet /finale arm i /finale start.
 */
public final class SepiolFinale extends JavaPlugin {

	private Broadcast broadcast;
	private StateStore state;
	private FinaleEngine engine;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		broadcast = new Broadcast(getLogger());
		state = new StateStore(new File(getDataFolder(), "state.yml"), getLogger());
		engine = new FinaleEngine(this, broadcast);

		FinaleCommand command = new FinaleCommand(this);
		PluginCommand registered = getCommand("finale");
		if (registered != null) {
			registered.setExecutor(command);
			registered.setTabCompleter(command);
		}
		getServer().getPluginManager().registerEvents(new FinaleListener(this), this);

		Phase saved = Phase.fromKey(state.getString("phase", "idle"));
		if (saved.running()) {
			if (getConfig().getBoolean("safety.auto-resume-after-restart", false)) {
				getLogger().warning("Финал был прерван перезапуском. Продолжаю с фазы " + saved.title() + ".");
				Bukkit.getScheduler().runTaskLater(this, () -> engine.resume(), 100L);
			} else {
				getLogger().warning("В state.yml остался незакрытый финал (фаза " + saved.title()
						+ "). Сам ничего не делаю: /finale restore вернёт настройки мира, "
						+ "/finale abort сбросит состояние.");
			}
		}
		getLogger().info("SepiolFinale готов. Плагин спит до команды /finale.");
	}

	@Override
	public void onDisable() {
		if (engine != null) {
			engine.stopOnDisable();
		}
		if (state != null) {
			state.save();
		}
	}

	public FinaleEngine engine() {
		return engine;
	}

	public StateStore state() {
		return state;
	}

	public Broadcast broadcast() {
		return broadcast;
	}

	/** Mir, kotoryy umiraet. */
	public World world() {
		String name = getConfig().getString("finale.world", "auto");
		if (name == null || name.isBlank() || name.equalsIgnoreCase("auto")) {
			return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
		}
		World world = Bukkit.getWorld(name);
		if (world != null) {
			return world;
		}
		getLogger().warning("Мир '" + name + "' не найден, беру первый мир сервера.");
		return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
	}

	/** Tochka, v kotoruyu shoditsya mir. */
	public Location center(World world) {
		String raw = getConfig().getString("finale.center", "spawn");
		if (raw != null && raw.contains(",")) {
			String[] parts = raw.split(",");
			if (parts.length >= 2) {
				try {
					double x = Double.parseDouble(parts[0].trim());
					double z = Double.parseDouble(parts[1].trim());
					Location location = new Location(world, x, world.getSeaLevel(), z);
					location.setY(world.getHighestBlockYAt(location) + 1);
					return location;
				} catch (NumberFormatException exception) {
					getLogger().warning("Не понял finale.center='" + raw + "', беру спавн.");
				}
			}
		}
		return world.getSpawnLocation();
	}

	/** Syroy tekst iz messages bez prefiksa. */
	public String text(String key) {
		return getConfig().getString("messages." + key, "");
	}

	/** Tekst s prefiksom; pustaya stroka, esli klyucha net - togda soobshchenie ne otpravitsya. */
	public String msg(String key) {
		String body = text(key);
		return body.isBlank() ? "" : text("prefix") + body;
	}

	public String seasonName() {
		return getConfig().getString("finale.season-name", "Season II");
	}

	public void reloadAll() {
		reloadConfig();
	}
}
