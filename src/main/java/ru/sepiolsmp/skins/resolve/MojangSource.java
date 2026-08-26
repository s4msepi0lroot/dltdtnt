package ru.sepiolsmp.skins.resolve;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import com.google.gson.JsonObject;

import ru.sepiolsmp.skins.model.SkinData;
import ru.sepiolsmp.skins.net.Http;
import ru.sepiolsmp.skins.net.Json;

/**
 * Licensed players first: nick -&gt; Mojang uuid -&gt; signed textures.
 * The value we get here is signed by Mojang and hosted on textures.minecraft.net,
 * so every client renders it without any client-side mod.
 */
public final class MojangSource {

	private final Http http;
	private final Logger logger;
	private final String profileEndpoint;
	private final String sessionEndpoint;
	private final int timeoutSeconds;

	public MojangSource(Http http, Logger logger, String profileEndpoint, String sessionEndpoint,
			int timeoutSeconds) {
		this.http = http;
		this.logger = logger;
		this.profileEndpoint = profileEndpoint;
		this.sessionEndpoint = sessionEndpoint;
		this.timeoutSeconds = timeoutSeconds;
	}

	public SkinData fetch(String name) {
		try {
			String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
			Http.Response profile = http.get(String.format(profileEndpoint, encoded), timeoutSeconds);
			if (profile.code() == 204 || profile.code() == 404) {
				return null; // not a licensed nickname
			}
			if (!profile.ok()) {
				logger.fine("Mojang profile lookup for " + name + " answered " + profile.code());
				return null;
			}
			JsonObject profileJson = Json.parse(profile.body());
			String uuid = Json.string(profileJson, "id");
			if (uuid == null || uuid.isBlank()) {
				return null;
			}

			Http.Response session = http.get(String.format(sessionEndpoint, uuid), timeoutSeconds);
			if (!session.ok()) {
				logger.fine("Mojang session lookup for " + name + " answered " + session.code());
				return null;
			}
			JsonObject sessionJson = Json.parse(session.body());
			JsonObject textures = Json.texturesProperty(sessionJson);
			if (textures == null) {
				return null;
			}
			String value = Json.string(textures, "value");
			if (value == null) {
				return null;
			}
			SkinData data = SkinData.of(value, Json.string(textures, "signature"), "mojang");
			return data.skinUrl == null ? null : data; // a licensed account with no skin set
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (Exception e) {
			logger.warning("Mojang lookup failed for " + name + ": " + e.getMessage());
			return null;
		}
	}
}
