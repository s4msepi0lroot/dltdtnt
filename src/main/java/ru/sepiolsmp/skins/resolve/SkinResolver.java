package ru.sepiolsmp.skins.resolve;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import ru.sepiolsmp.skins.model.SkinData;
import ru.sepiolsmp.skins.store.SkinStore;
import ru.sepiolsmp.skins.store.WebExporter;

/**
 * Priority chain: licensed account -&gt; Ely.by -&gt; (MineSkin re-upload) -&gt; default.
 * Everything here blocks on network, so it runs on its own pool. The login
 * listener only ever waits for a short budget and falls back to the cache.
 */
public final class SkinResolver {

	private final SkinStore store;
	private final WebExporter exporter;
	private final MojangSource mojang;
	private final ElySource ely;
	private final MineSkinSource mineskin;
	private final List<String> priority;
	private final long refreshHours;
	private final long negativeMinutes;
	private final SkinData fallback;
	private final Logger logger;

	private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
	private final ExecutorService pool;

	public SkinResolver(SkinStore store, WebExporter exporter, MojangSource mojang, ElySource ely,
			MineSkinSource mineskin, List<String> priority, long refreshHours, long negativeMinutes,
			SkinData fallback, Logger logger) {
		this.store = store;
		this.exporter = exporter;
		this.mojang = mojang;
		this.ely = ely;
		this.mineskin = mineskin;
		this.priority = priority;
		this.refreshHours = refreshHours;
		this.negativeMinutes = negativeMinutes;
		this.fallback = fallback;
		this.logger = logger;
		this.pool = Executors.newFixedThreadPool(3, runnable -> {
			Thread thread = new Thread(runnable, "SepiolSkins-net");
			thread.setDaemon(true);
			return thread;
		});
	}

	public SkinStore store() {
		return store;
	}

	public boolean needsRefresh(SkinData data) {
		return !store.isFresh(data, refreshHours);
	}

	/** Blocking full resolve. Async threads only. */
	public SkinData resolve(String name, boolean force) {
		String id = SkinStore.key(name);
		SkinData cached = store.get(name);
		if (!force) {
			if (store.isFresh(cached, refreshHours)) {
				return cached;
			}
			if (store.isMissing(name, negativeMinutes)) {
				return cached != null ? cached : fallback;
			}
		}
		if (!inFlight.add(id)) {
			// Somebody is already asking the APIs about this nick - do not hammer them.
			return cached != null ? cached : fallback;
		}
		try {
			for (String source : priority) {
				SkinData found = fetch(source, name);
				if (found == null) {
					continue;
				}
				if (!found.hasTextures()) {
					if (mineskin == null) {
						logger.warning("Got a skin url for " + name
								+ " but MineSkin is disabled, so plain clients will not see it");
						continue;
					}
					found = mineskin.upload(found.skinUrl, found.model, found.source);
					if (found == null || !found.hasTextures()) {
						continue;
					}
				}
				store.put(name, found);
				exportLater(name, found);
				logger.info("Skin for " + name + " resolved from " + found.source);
				return found;
			}
			store.markMissing(name);
			return cached != null ? cached : fallback;
		} finally {
			inFlight.remove(id);
		}
	}

	/**
	 * Waits at most {@code seconds} for a fresh skin. On timeout the lookup keeps
	 * running in the background and lands in the cache for the next login, while
	 * the player joins with whatever we already had. Logins must never hang.
	 */
	public SkinData resolveWithTimeout(String name, int seconds, boolean force) {
		Future<SkinData> future = pool.submit(() -> resolve(name, force));
		try {
			return future.get(Math.max(1, seconds), TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			logger.info("Skin lookup for " + name + " is slow, joining with the cached one");
			return store.get(name);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return store.get(name);
		} catch (Exception e) {
			logger.warning("Skin lookup for " + name + " failed: " + e.getMessage());
			return store.get(name);
		}
	}

	public void async(Runnable task) {
		pool.submit(task);
	}

	/** Manual skin: any png url, pinned so auto refresh never overwrites it. */
	public SkinData applyUrl(String name, String url, String model) {
		if (mineskin == null) {
			return null;
		}
		SkinData data = mineskin.upload(url, model, "manual");
		if (data == null || !data.hasTextures()) {
			return null;
		}
		data.source = "manual";
		data.pinned = true;
		store.put(name, data);
		exportLater(name, data);
		return data;
	}

	/** Copy the skin of another nickname (licensed or Ely.by) onto this player. */
	public SkinData applyFrom(String name, String donor) {
		SkinData data = resolve(donor, true);
		if (data == null || !data.hasTextures()) {
			return null;
		}
		SkinData copy = SkinData.of(data.value, data.signature, data.source);
		copy.pinned = true;
		store.put(name, copy);
		exportLater(name, copy);
		return copy;
	}

	public SkinData fallback() {
		return fallback;
	}

	public void shutdown() {
		pool.shutdownNow();
	}

	private void exportLater(String name, SkinData data) {
		pool.submit(() -> exporter.export(name, data));
	}

	private SkinData fetch(String source, String name) {
		try {
			if ("mojang".equalsIgnoreCase(source) && mojang != null) {
				return mojang.fetch(name);
			}
			if (("elyby".equalsIgnoreCase(source) || "ely.by".equalsIgnoreCase(source)) && ely != null) {
				return ely.fetch(name);
			}
			return null;
		} catch (Exception e) {
			logger.warning("Source " + source + " failed for " + name + ": " + e.getMessage());
			return null;
		}
	}
}
