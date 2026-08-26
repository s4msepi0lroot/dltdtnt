package ru.sepiolsmp.skins;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import ru.sepiolsmp.skins.model.SkinData;

/**
 * The whole trick lives here: AsyncPlayerPreLoginEvent already runs off the main
 * thread and still lets us edit the profile the player will join with. We put the
 * textures property in there, so the skin is visible from the very first tick -
 * for the player and for everyone looking at them, on any client.
 */
public final class LoginListener implements Listener {

	private final SepiolSkins plugin;

	public LoginListener(SepiolSkins plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPreLogin(AsyncPlayerPreLoginEvent event) {
		if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
			return;
		}
		String name = event.getName();
		if (name == null || name.isBlank()) {
			return;
		}

		SkinData data = plugin.store().get(name);
		if (plugin.resolver().needsRefresh(data)) {
			int budget = plugin.getConfig().getInt("resolve.login-timeout-seconds", 4);
			SkinData resolved = plugin.resolver().resolveWithTimeout(name, budget, false);
			if (resolved != null && resolved.hasTextures()) {
				data = resolved;
			}
		}
		if (data == null || !data.hasTextures()) {
			data = plugin.resolver().fallback();
		}
		if (data == null || !data.hasTextures()) {
			return; // nothing found anywhere, vanilla Steve/Alex it is
		}

		try {
			PlayerProfile profile = event.getPlayerProfile();
			if (profile == null) {
				return;
			}
			profile.setProperty(new ProfileProperty("textures", data.value, data.signature));
		} catch (Exception e) {
			// A skin is never worth a failed login.
			plugin.getLogger().warning("Cannot inject the skin of " + name + ": " + e.getMessage());
		}
	}
}
