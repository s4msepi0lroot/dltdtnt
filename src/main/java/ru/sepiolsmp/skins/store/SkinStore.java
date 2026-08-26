package ru.sepiolsmp.skins.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;

import ru.sepiolsmp.skins.model.SkinData;
import ru.sepiolsmp.skins.net.Json;

/**
 * Disk + memory cache keyed by lowercase nickname.
 *
 * Nickname, not UUID, on purpose: with AuthMe and online-mode=false the UUID is
 * derived from the name anyway, and the same nick must look the same whether the
 * player joins from a licensed launcher or a cracked one.
 */
public final class SkinStore {

	private final Path dir;
	private final Logger logger;
	private final Map<String, SkinData> memory = new ConcurrentHashMap<>();
	private final Map<String, Long> misses = new ConcurrentHashMap<>();

	public SkinStore(Path dataFolder, Logger logger) {
		this.dir = dataFolder.resolve("cache");
		this.logger = logger;
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			logger.warning("Cannot create the skin cache folder: " + e.getMessage());
		}
	}

	public static String key(String name) {
		if (name == null) {
			return "";
		}
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.\\-]", "_");
	}

	public SkinData get(String name) {
		String id = key(name);
		if (id.isEmpty()) {
			return null;
		}
		SkinData cached = memory.get(id);
		if (cached != null) {
			return cached;
		}
		Path file = dir.resolve(id + ".json");
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try {
			SkinData data = Json.fromJson(Files.readString(file, StandardCharsets.UTF_8), SkinData.class);
			if (data == null) {
				return null;
			}
			if (data.skinUrl == null) {
				data.fillFromValue();
			}
			memory.put(id, data);
			return data;
		} catch (IOException e) {
			logger.warning("Broken skin cache entry " + file.getFileName() + ": " + e.getMessage());
			return null;
		}
	}

	public void put(String name, SkinData data) {
		String id = key(name);
		if (id.isEmpty() || data == null) {
			return;
		}
		memory.put(id, data);
		misses.remove(id);
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve(id + ".json"), Json.toJson(data), StandardCharsets.UTF_8);
		} catch (IOException e) {
			logger.warning("Cannot store the skin of " + name + ": " + e.getMessage());
		}
	}

	public void remove(String name) {
		String id = key(name);
		memory.remove(id);
		misses.remove(id);
		try {
			Files.deleteIfExists(dir.resolve(id + ".json"));
		} catch (IOException e) {
			logger.warning("Cannot drop the skin of " + name + ": " + e.getMessage());
		}
	}

	public void markMissing(String name) {
		misses.put(key(name), System.currentTimeMillis());
	}

	public boolean isMissing(String name, long minutes) {
		Long stamp = misses.get(key(name));
		if (stamp == null) {
			return false;
		}
		if (System.currentTimeMillis() - stamp > TimeUnit.MINUTES.toMillis(Math.max(1, minutes))) {
			misses.remove(key(name));
			return false;
		}
		return true;
	}

	public boolean isFresh(SkinData data, long hours) {
		if (data == null || !data.hasTextures()) {
			return false;
		}
		if (data.pinned) {
			return true;
		}
		return data.ageMillis() < TimeUnit.HOURS.toMillis(Math.max(1, hours));
	}

	public List<String> cachedNames() {
		List<String> names = new ArrayList<>();
		try (Stream<Path> files = Files.list(dir)) {
			files.filter(path -> path.getFileName().toString().endsWith(".json"))
					.forEach(path -> {
						String file = path.getFileName().toString();
						names.add(file.substring(0, file.length() - 5));
					});
		} catch (IOException e) {
			logger.warning("Cannot list the skin cache: " + e.getMessage());
		}
		return names;
	}

	public int purge() {
		int removed = 0;
		for (String name : cachedNames()) {
			remove(name);
			removed++;
		}
		memory.clear();
		misses.clear();
		return removed;
	}

	public void invalidateMemory() {
		memory.clear();
		misses.clear();
	}
}
