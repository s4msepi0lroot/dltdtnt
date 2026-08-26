package ru.sepiolsmp.skins.resolve;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.JsonElement;

import ru.sepiolsmp.skins.model.SkinData;
import ru.sepiolsmp.skins.net.Http;
import ru.sepiolsmp.skins.net.Json;

/**
 * Turns any png url into signed, Mojang-hosted textures. This is the bridge that
 * makes Ely.by and custom skins visible for plain clients.
 *
 * The response shape of MineSkin has changed between API versions, so we search
 * the json tree for "value"/"signature" instead of a fixed path.
 */
public final class MineSkinSource {

	private final Http http;
	private final Logger logger;
	private final String endpoint;
	private final String apiKey;
	private final int timeoutSeconds;
	private final int maxWaitSeconds;

	public MineSkinSource(Http http, Logger logger, String endpoint, String apiKey, int timeoutSeconds,
			int maxWaitSeconds) {
		this.http = http;
		this.logger = logger;
		this.endpoint = endpoint;
		this.apiKey = apiKey;
		this.timeoutSeconds = timeoutSeconds;
		this.maxWaitSeconds = maxWaitSeconds;
	}

	/**
	 * @param skinUrl direct link to a 64x64 (or legacy 64x32) skin png
	 * @param model   classic or slim
	 * @param origin  where the png came from, kept for the profile page
	 */
	public SkinData upload(String skinUrl, String model, String origin) {
		if (skinUrl == null || skinUrl.isBlank()) {
			return null;
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("url", skinUrl);
		body.put("variant", "slim".equalsIgnoreCase(model) ? "slim" : "classic");
		body.put("visibility", 1);
		body.put("name", "sepiolsmp");
		String payload = Json.toJson(body);

		SkinData first = attempt(payload, skinUrl, model, origin, true);
		if (first != null) {
			return first;
		}
		return null;
	}

	private SkinData attempt(String payload, String skinUrl, String model, String origin, boolean allowRetry) {
		try {
			Http.Response response = http.postJson(endpoint, payload, apiKey, timeoutSeconds);
			if (response.code() == 429 && allowRetry) {
				long wait = waitSeconds(response.body());
				if (wait > 0 && wait <= maxWaitSeconds) {
					logger.info("MineSkin rate limit, waiting " + wait + "s and retrying once");
					Thread.sleep(wait * 1000L);
					return attempt(payload, skinUrl, model, origin, false);
				}
				logger.warning("MineSkin rate limit, skipping (set an api-key in config to raise it)");
				return null;
			}
			if (!response.ok()) {
				logger.warning("MineSkin answered " + response.code() + ": " + shorten(response.body()));
				return null;
			}
			JsonElement root = Json.parseAny(response.body());
			String value = Json.findString(root, "value");
			String signature = Json.findString(root, "signature");
			if (value == null) {
				logger.warning("MineSkin gave no textures value: " + shorten(response.body()));
				return null;
			}
			SkinData data = SkinData.of(value, signature, "mineskin");
			if (data.model == null) {
				data.model = model;
			}
			if (origin != null && !origin.isBlank()) {
				data.source = origin + "+mineskin";
			}
			return data;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (Exception e) {
			logger.warning("MineSkin upload failed for " + skinUrl + ": " + e.getMessage());
			return null;
		}
	}

	private long waitSeconds(String body) {
		JsonElement root = Json.parseAny(body);
		String delay = Json.findString(root, "delay");
		if (delay == null) {
			delay = Json.findString(root, "nextRequest");
		}
		if (delay == null) {
			return 5;
		}
		try {
			double parsed = Double.parseDouble(delay);
			if (parsed > 1_000_000_000d) {
				// absolute timestamp in seconds
				long diff = (long) parsed - System.currentTimeMillis() / 1000L;
				return Math.max(1, diff);
			}
			return Math.max(1, (long) Math.ceil(parsed));
		} catch (NumberFormatException e) {
			return 5;
		}
	}

	private static String shorten(String body) {
		if (body == null) {
			return "";
		}
		String trimmed = body.replace('\n', ' ').trim();
		return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
	}
}
