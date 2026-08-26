package ru.sepiolsmp.finale.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * plugins/SepiolFinale/state.yml - память финала между перезапусками.
 * Здесь живёт текущая фаза, список мёртвых и снимок настроек мира,
 * без которого /finale abort не смог бы ничего вернуть.
 */
public final class StateStore {

	private final File file;
	private final Logger logger;
	private YamlConfiguration data;

	public StateStore(File file, Logger logger) {
		this.file = file;
		this.logger = logger;
		this.data = YamlConfiguration.loadConfiguration(file);
	}

	public void set(String path, Object value) {
		data.set(path, value);
	}

	public String getString(String path, String fallback) {
		return data.getString(path, fallback);
	}

	public boolean getBoolean(String path, boolean fallback) {
		return data.getBoolean(path, fallback);
	}

	public long getLong(String path, long fallback) {
		return data.contains(path) ? data.getLong(path) : fallback;
	}

	public List<String> getStringList(String path) {
		return data.getStringList(path);
	}

	public boolean has(String path) {
		return data.contains(path);
	}

	/** Секция для записи: создаётся, если ещё нет. */
	public ConfigurationSection section(String path) {
		ConfigurationSection existing = data.getConfigurationSection(path);
		return existing != null ? existing : data.createSection(path);
	}

	/** Секция для чтения: null, если снимка нет. */
	public ConfigurationSection existingSection(String path) {
		return data.getConfigurationSection(path);
	}

	public void save() {
		try {
			File parent = file.getParentFile();
			if (parent != null && !parent.exists() && !parent.mkdirs()) {
				logger.warning("Не создалась папка для state.yml");
			}
			data.save(file);
		} catch (IOException exception) {
			logger.warning("Не смог сохранить state.yml: " + exception.getMessage());
		}
	}

	/** Полный сброс после abort. */
	public void wipe() {
		data = new YamlConfiguration();
		if (file.exists() && !file.delete()) {
			logger.warning("Не смог удалить state.yml, перезапишу пустыми данными.");
			save();
		}
	}
}
