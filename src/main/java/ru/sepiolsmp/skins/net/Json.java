package ru.sepiolsmp.skins.net;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Gson helpers. Gson ships with the server, so no shading needed. */
public final class Json {

	private static final Gson GSON = new Gson();

	private Json() {
	}

	public static JsonObject parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			JsonElement element = JsonParser.parseString(raw);
			return element.isJsonObject() ? element.getAsJsonObject() : null;
		} catch (Exception e) {
			return null;
		}
	}

	public static JsonElement parseAny(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return JsonParser.parseString(raw);
		} catch (Exception e) {
			return null;
		}
	}

	public static String string(JsonObject object, String key) {
		if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
			return null;
		}
		try {
			return object.get(key).getAsString();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Breadth-first search for the first primitive with this key anywhere in the
	 * tree. MineSkin has changed its response shape more than once, so we stay
	 * tolerant instead of hardcoding a path.
	 */
	public static String findString(JsonElement root, String key) {
		if (root == null) {
			return null;
		}
		Deque<JsonElement> queue = new ArrayDeque<>();
		queue.add(root);
		while (!queue.isEmpty()) {
			JsonElement current = queue.poll();
			if (current.isJsonObject()) {
				JsonObject object = current.getAsJsonObject();
				for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
					JsonElement value = entry.getValue();
					if (entry.getKey().equals(key) && value.isJsonPrimitive()) {
						String text = value.getAsString();
						if (!text.isBlank()) {
							return text;
						}
					}
					if (value.isJsonObject() || value.isJsonArray()) {
						queue.add(value);
					}
				}
			} else if (current.isJsonArray()) {
				JsonArray array = current.getAsJsonArray();
				for (JsonElement element : array) {
					if (element.isJsonObject() || element.isJsonArray()) {
						queue.add(element);
					}
				}
			}
		}
		return null;
	}

	/** Mojang-style properties array: [{name:"textures", value:"...", signature:"..."}]. */
	public static JsonObject texturesProperty(JsonObject profile) {
		if (profile == null || !profile.has("properties") || !profile.get("properties").isJsonArray()) {
			return null;
		}
		for (JsonElement element : profile.getAsJsonArray("properties")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject property = element.getAsJsonObject();
			if ("textures".equals(string(property, "name")) && string(property, "value") != null) {
				return property;
			}
		}
		return null;
	}

	public static String toJson(Object value) {
		return GSON.toJson(value);
	}

	public static <T> T fromJson(String raw, Class<T> type) {
		try {
			return GSON.fromJson(raw, type);
		} catch (Exception e) {
			return null;
		}
	}
}
