package ru.sepiolsmp.skins.resolve;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import com.google.gson.JsonObject;

import ru.sepiolsmp.skins.model.SkinData;
import ru.sepiolsmp.skins.net.Http;
import ru.sepiolsmp.skins.net.Json;

/**
 * Ely.by - the launcher most of our cracked players use.
 *
 * Two modes:
 *  - reupload = true (default): we only take the png url and the model here, and
 *    the resolver pushes it through MineSkin so the final texture lives on
 *    textures.minecraft.net. Vanilla clients refuse skin urls from other hosts,
 *    which is exactly the trap everybody hits with offline mode.
 *  - reupload = false: we take Ely.by's own signed profile. Faster and works
 *    without MineSkin, but only for clients that do not validate the host
 *    (i.e. players with a skin-loader mod in the pack).
 */
public final class ElySource {

	private final Http http;
	private final Logger logger;
	private final String texturesEndpoint;
	private final String signedEndpoint;
	private final boolean reupload;
	private final int timeoutSeconds;

	public ElySource(Http http, Logger logger, String texturesEndpoint, String signedEndpoint,
			boolean reupload, int timeoutSeconds) {
		this.http = http;
		this.logger = logger;
		this.texturesEndpoint = texturesEndpoint;
		this.signedEndpoint = signedEndpoint;
		this.reupload = reupload;
		this.timeoutSeconds = timeoutSeconds;
	}

	public SkinData fetch(String name) {
		String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
		if (!reupload) {
			SkinData signed = signed(name, encoded);
			if (signed != null) {
				return signed;
			}
		}
		return raw(name, encoded);
	}

	private SkinData signed(String name, String encoded) {
		try {
			Http.Response response = http.get(String.format(signedEndpoint, encoded), timeoutSeconds);
			if (!response.ok()) {
				return null;
			}
			JsonObject textures = Json.texturesProperty(Json.parse(response.body()));
			if (textures == null) {
				return null;
			}
			String value = Json.string(textures, "value");
			if (value == null) {
				return null;
			}
			return SkinData.of(value, Json.string(textures, "signature"), "elyby");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (Exception e) {
			logger.warning("Ely.by signed lookup failed for " + name + ": " + e.getMessage());
			return null;
		}
	}

	private SkinData raw(String name, String encoded) {
		try {
			Http.Response response = http.get(String.format(texturesEndpoint, encoded), timeoutSeconds);
			if (response.code() == 204 || response.code() == 404) {
				return null; // no Ely.by account with this nick
			}
			if (!response.ok()) {
				logger.fine("Ely.by textures lookup for " + name + " answered " + response.code());
				return null;
			}
			JsonObject root = Json.parse(response.body());
			if (root == null || !root.has("SKIN") || !root.get("SKIN").isJsonObject()) {
				return null;
			}
			JsonObject skin = root.getAsJsonObject("SKIN");
			String url = Json.string(skin, "url");
			if (url == null || url.isBlank()) {
				return null;
			}
			String model = "classic";
			if (skin.has("metadata") && skin.get("metadata").isJsonObject()) {
				String metaModel = Json.string(skin.getAsJsonObject("metadata"), "model");
				if (metaModel != null && !metaModel.isBlank()) {
					model = metaModel;
				}
			}
			String cape = null;
			if (root.has("CAPE") && root.get("CAPE").isJsonObject()) {
				cape = Json.string(root.getAsJsonObject("CAPE"), "url");
			}
			return SkinData.ofUrl(url, cape, model, "elyby");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (Exception e) {
			logger.warning("Ely.by lookup failed for " + name + ": " + e.getMessage());
			return null;
		}
	}
}
