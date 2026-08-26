package ru.sepiolsmp.finale.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Мостик к SepiolCore без жёсткой зависимости при компиляции.
 * Задача одна: перед выключением сервера попросить ядро заморозить финальную
 * статистику сезона, чтобы веб-профили жили дальше уже без сервера.
 * Если SepiolCore нет или его API поменялось - финал просто продолжается.
 */
public final class CoreBridge {

	private static final List<String> SNAPSHOT_METHODS =
			List.of("snapshotnow", "writesnapshot", "savesnapshot", "takesnapshot", "snapshot");

	private CoreBridge() {
	}

	public static boolean snapshot(Logger logger) {
		Plugin core = Bukkit.getPluginManager().getPlugin("SepiolCore");
		if (core == null || !core.isEnabled()) {
			logger.info("SepiolCore не найден - финальный снимок статистики пропускаю.");
			return false;
		}
		if (callSnapshot(core, logger)) {
			logger.info("SepiolCore сделал финальный снимок статистики.");
			return true;
		}
		for (Method accessor : core.getClass().getMethods()) {
			if (accessor.getParameterCount() != 0 || accessor.getReturnType() == void.class) {
				continue;
			}
			String name = accessor.getName().toLowerCase(Locale.ROOT);
			if (!name.contains("snapshot") && !name.contains("service")) {
				continue;
			}
			try {
				Object service = accessor.invoke(core);
				if (service != null && callSnapshot(service, logger)) {
					logger.info("SepiolCore сделал финальный снимок статистики (через " + accessor.getName() + ").");
					return true;
				}
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// пробуем следующий вариант
			}
		}
		try {
			if (Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sepiol snapshot")) {
				logger.info("Финальный снимок заказан командой /sepiol snapshot.");
				return true;
			}
		} catch (RuntimeException exception) {
			logger.warning("Команда /sepiol snapshot не сработала: " + exception.getMessage());
		}
		logger.warning("SepiolCore найден, но способ сделать снимок не опознан. Финал продолжается.");
		return false;
	}

	private static boolean callSnapshot(Object target, Logger logger) {
		for (Method method : target.getClass().getMethods()) {
			if (method.getParameterCount() != 0) {
				continue;
			}
			if (!SNAPSHOT_METHODS.contains(method.getName().toLowerCase(Locale.ROOT))) {
				continue;
			}
			try {
				method.invoke(target);
				return true;
			} catch (ReflectiveOperationException | RuntimeException exception) {
				logger.warning("Снимок через " + method.getName() + " упал: " + exception.getMessage());
				return false;
			}
		}
		return false;
	}
}
