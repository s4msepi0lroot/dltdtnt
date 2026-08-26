package ru.sepiolsmp.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import ru.sepiolsmp.core.data.ModBridge;
import ru.sepiolsmp.core.data.PlayerIndex;
import ru.sepiolsmp.core.data.ProfileService;
import ru.sepiolsmp.core.data.SnapshotService;
import ru.sepiolsmp.core.data.StatsReader;
import ru.sepiolsmp.core.web.WebServer;

/**
 * SepiolCore - brick 1 of the sepiolSMP season 2 stack.
 *
 * Everything here is deliberately dependency free: the web profile is served by
 * the JDK http server (same approach Dynmap uses), player data is read straight
 * from disk, and the only Bukkit calls happen on the main thread inside
 * {@link SnapshotService}.
 */
public final class SepiolCore extends JavaPlugin {

	private SnapshotService snapshots;
	private ProfileService profiles;
	private WebServer web;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		startServices();
		SepiolCommand command = new SepiolCommand(this);
		if (getCommand("sepiol") != null) {
			getCommand("sepiol").setExecutor(command);
			getCommand("sepiol").setTabCompleter(command);
		}
	}

	@Override
	public void onDisable() {
		stopServices();
	}

	/** Full restart of the services, used by /sepiol reload. */
	public void reloadEverything() {
		stopServices();
		reloadConfig();
		startServices();
	}

	private void startServices() {
		Path levelRoot = resolveLevelRoot();
		getLogger().info("Level root: " + levelRoot.toAbsolutePath());

		StatsReader stats = new StatsReader(levelRoot.resolve("stats"));
		ModBridge mods = new ModBridge(this, levelRoot);
		PlayerIndex index = new PlayerIndex(this, levelRoot);

		snapshots = new SnapshotService(this);
		snapshots.start();
		profiles = new ProfileService(this, snapshots, stats, mods, index);

		if (!getConfig().getBoolean("web.enabled", true)) {
			getLogger().info("Web profiles are disabled in config.yml");
			return;
		}
		try {
			web = new WebServer(this, profiles, snapshots);
			web.start();
			getLogger().info("Web profiles are live on " + getConfig().getString("web.bind", "0.0.0.0")
					+ ":" + getConfig().getInt("web.port", 8300));
		} catch (IOException e) {
			web = null;
			getLogger().log(Level.SEVERE, "Could not bind the web profile port "
					+ getConfig().getInt("web.port", 8300) + ": " + e.getMessage(), e);
		}
	}

	private void stopServices() {
		if (web != null) {
			web.stop();
			web = null;
		}
		if (snapshots != null) {
			snapshots.stop();
			snapshots = null;
		}
		profiles = null;
	}

	/**
	 * The folder that holds stats/, playerdata/ and the json files written by
	 * BadHabits and BoozeCraft. On a normal Paper/Youer layout this is ./world.
	 */
	private Path resolveLevelRoot() {
		String configured = getConfig().getString("integration.level-root", "auto");
		if (configured != null && !configured.isBlank() && !"auto".equalsIgnoreCase(configured)) {
			Path path = Path.of(configured);
			return path.isAbsolute() ? path : serverRoot().resolve(configured);
		}
		if (!Bukkit.getWorlds().isEmpty()) {
			World overworld = Bukkit.getWorlds().get(0);
			return overworld.getWorldFolder().toPath();
		}
		return serverRoot().resolve("world");
	}

	/** plugins/SepiolCore -> plugins -> server root */
	public Path serverRoot() {
		return getDataFolder().getAbsoluteFile().getParentFile().getParentFile().toPath();
	}

	public ProfileService profiles() {
		return profiles;
	}

	public SnapshotService snapshots() {
		return snapshots;
	}

	public boolean webRunning() {
		return web != null;
	}

	public String publicUrl() {
		String url = getConfig().getString("web.public-url", "");
		if (url == null || url.isBlank()) {
			return "http://localhost:" + getConfig().getInt("web.port", 8300);
		}
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
