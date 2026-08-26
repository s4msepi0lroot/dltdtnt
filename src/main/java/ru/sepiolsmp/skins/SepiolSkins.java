package ru.sepiolsmp.skins;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import ru.sepiolsmp.skins.model.SkinData;
import ru.sepiolsmp.skins.net.Http;
import ru.sepiolsmp.skins.resolve.ElySource;
import ru.sepiolsmp.skins.resolve.MineSkinSource;
import ru.sepiolsmp.skins.resolve.MojangSource;
import ru.sepiolsmp.skins.resolve.SkinResolver;
import ru.sepiolsmp.skins.store.SkinStore;
import ru.sepiolsmp.skins.store.WebExporter;

/**
 * Brick 2 of sepiolSMP Season 2.
 *
 * AuthMe requires online-mode=false, and with offline mode Mojang stops handing
 * textures to the server, so every player would look like Steve. This plugin
 * resolves the skin server side and injects it into the login profile, which
 * means every client sees every skin: licensed, Ely.by or anything else.
 */
public final class SepiolSkins extends JavaPlugin {

	private SkinStore store;
	private SkinResolver resolver;
	private WebExporter exporter;
	private Http http;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		build();

		getServer().getPluginManager().registerEvents(new LoginListener(this), this);
		SkinCommand command = new SkinCommand(this);
		if (getCommand("skin") != null) {
			getCommand("skin").setExecutor(command);
			getCommand("skin").setTabCompleter(command);
		}

		if (Bukkit.getOnlineMode()) {
			getLogger().info("Server is in online mode, Mojang skins already work. "
					+ "The plugin stays useful for Ely.by nicknames and local avatars.");
		} else {
			getLogger().info("Offline mode detected (AuthMe setup) - skins are injected by this plugin.");
		}
		getLogger().info("SepiolSkins enabled. Priority: " + priority()
				+ ", web assets: " + getConfig().getBoolean("files.export-web-assets", true));
	}

	@Override
	public void onDisable() {
		if (resolver != null) {
			resolver.shutdown();
		}
	}

	// --------------------------------------------------------------- wiring

	private void build() {
		this.http = new Http();
		this.store = new SkinStore(getDataFolder().toPath(), getLogger());

		int timeout = getConfig().getInt("resolve.timeout-seconds", 6);
		this.exporter = new WebExporter(getDataFolder().toPath(), http, getLogger(),
				getConfig().getBoolean("files.export-web-assets", true),
				getConfig().getInt("files.head-size", 72), timeout);

		MojangSource mojang = getConfig().getBoolean("mojang.enabled", true)
				? new MojangSource(http, getLogger(),
						getConfig().getString("mojang.profile-endpoint",
								"https://api.mojang.com/users/profiles/minecraft/%s"),
						getConfig().getString("mojang.session-endpoint",
								"https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false"),
						timeout)
				: null;

		ElySource ely = getConfig().getBoolean("elyby.enabled", true)
				? new ElySource(http, getLogger(),
						getConfig().getString("elyby.textures-endpoint",
								"https://skinsystem.ely.by/textures/%s"),
						getConfig().getString("elyby.signed-endpoint",
								"https://skinsystem.ely.by/textures/signed/%s?proxy=true"),
						getConfig().getBoolean("elyby.reupload-through-mineskin", true), timeout)
				: null;

		MineSkinSource mineskin = getConfig().getBoolean("mineskin.enabled", true)
				? new MineSkinSource(http, getLogger(),
						getConfig().getString("mineskin.endpoint", "https://api.mineskin.org/generate/url"),
						getConfig().getString("mineskin.api-key", ""),
						getConfig().getInt("mineskin.timeout-seconds", 25),
						getConfig().getInt("mineskin.max-wait-seconds", 20))
				: null;

		SkinData fallback = null;
		String defaultValue = getConfig().getString("default-skin.value", "");
		if (defaultValue != null && !defaultValue.isBlank()) {
			fallback = SkinData.of(defaultValue, getConfig().getString("default-skin.signature", ""), "default");
		}

		this.resolver = new SkinResolver(store, exporter, mojang, ely, mineskin, priority(),
				getConfig().getLong("resolve.refresh-hours", 24),
				getConfig().getLong("resolve.negative-cache-minutes", 90), fallback, getLogger());
	}

	public void reloadEverything() {
		if (resolver != null) {
			resolver.shutdown();
		}
		reloadConfig();
		build();
	}

	private List<String> priority() {
		List<String> configured = getConfig().getStringList("resolve.priority");
		List<String> cleaned = new ArrayList<>();
		for (String entry : configured) {
			if (entry != null && !entry.isBlank()) {
				cleaned.add(entry.trim().toLowerCase(Locale.ROOT));
			}
		}
		if (cleaned.isEmpty()) {
			cleaned.add("mojang");
			cleaned.add("elyby");
		}
		return cleaned;
	}

	// --------------------------------------------------------------- helpers

	public SkinStore store() {
		return store;
	}

	public SkinResolver resolver() {
		return resolver;
	}

	public WebExporter exporter() {
		return exporter;
	}

	public String message(String key) {
		String prefix = getConfig().getString("messages.prefix", "");
		String body = getConfig().getString("messages." + key, key);
		return ChatColor.translateAlternateColorCodes('&', (prefix == null ? "" : prefix) + body);
	}

	public String color(String raw) {
		String prefix = getConfig().getString("messages.prefix", "");
		return ChatColor.translateAlternateColorCodes('&', (prefix == null ? "" : prefix) + raw);
	}

	/**
	 * Pushes a skin onto a player who is already online. Paper can do this without
	 * a relog; if the method is missing on this build we simply ask for a relog.
	 * Main thread only.
	 */
	public boolean applyLive(Player player, SkinData data) {
		if (player == null || data == null || !data.hasTextures()) {
			return false;
		}
		if (!getConfig().getBoolean("resolve.apply-live", true)) {
			return false;
		}
		try {
			PlayerProfile profile = player.getPlayerProfile();
			profile.setProperty(new ProfileProperty("textures", data.value, data.signature));
			Method setter = player.getClass().getMethod("setPlayerProfile", PlayerProfile.class);
			setter.invoke(player, profile);
			return true;
		} catch (NoSuchMethodException e) {
			return false;
		} catch (Exception e) {
			getLogger().warning("Live skin update failed for " + player.getName() + ": " + e.getMessage());
			return false;
		}
	}
}
