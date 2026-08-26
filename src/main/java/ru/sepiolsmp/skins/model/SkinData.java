package ru.sepiolsmp.skins.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.google.gson.JsonObject;

import ru.sepiolsmp.skins.net.Json;

/**
 * One resolved skin. Serialized as-is to plugins/SepiolSkins/cache/&lt;nick&gt;.json,
 * so keep the fields simple and public.
 */
public final class SkinData {

	/** Base64 "textures" property value, exactly what the client expects. */
	public String value;
	/** Signature for the value. May be null - fine while online-mode is false. */
	public String signature;
	/** mojang | elyby | mineskin | manual | default */
	public String source;
	/** Direct png url when we know it (used for local avatars). */
	public String skinUrl;
	public String capeUrl;
	/** classic | slim */
	public String model;
	public long fetchedAt;
	/** Set by hand with /skin url - auto refresh must not overwrite it. */
	public boolean pinned;

	public static SkinData of(String value, String signature, String source) {
		SkinData data = new SkinData();
		data.value = value;
		data.signature = signature;
		data.source = source;
		data.fetchedAt = System.currentTimeMillis();
		data.fillFromValue();
		return data;
	}

	/** Half-resolved result: we know the png, but not a signed textures value yet. */
	public static SkinData ofUrl(String skinUrl, String capeUrl, String model, String source) {
		SkinData data = new SkinData();
		data.skinUrl = skinUrl;
		data.capeUrl = capeUrl;
		data.model = model;
		data.source = source;
		data.fetchedAt = System.currentTimeMillis();
		return data;
	}

	public boolean hasTextures() {
		return value != null && !value.isBlank();
	}

	public boolean isSlim() {
		return "slim".equalsIgnoreCase(model);
	}

	public long ageMillis() {
		return System.currentTimeMillis() - fetchedAt;
	}

	/** Pulls skin/cape urls and the model out of the base64 textures value. */
	public void fillFromValue() {
		if (!hasTextures()) {
			return;
		}
		try {
			String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
			JsonObject root = Json.parse(decoded);
			if (root == null || !root.has("textures") || !root.get("textures").isJsonObject()) {
				return;
			}
			JsonObject textures = root.getAsJsonObject("textures");
			if (textures.has("SKIN") && textures.get("SKIN").isJsonObject()) {
				JsonObject skin = textures.getAsJsonObject("SKIN");
				String url = Json.string(skin, "url");
				if (url != null) {
					this.skinUrl = url;
				}
				if (skin.has("metadata") && skin.get("metadata").isJsonObject()) {
					String metaModel = Json.string(skin.getAsJsonObject("metadata"), "model");
					if (metaModel != null) {
						this.model = metaModel;
					}
				} else if (this.model == null) {
					this.model = "classic";
				}
			}
			if (textures.has("CAPE") && textures.get("CAPE").isJsonObject()) {
				String url = Json.string(textures.getAsJsonObject("CAPE"), "url");
				if (url != null) {
					this.capeUrl = url;
				}
			}
		} catch (Exception ignored) {
			// A broken cache entry must never break a login.
		}
	}
}
