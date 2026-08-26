package ru.sepiolsmp.skins.store;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import ru.sepiolsmp.skins.model.SkinData;
import ru.sepiolsmp.skins.net.Http;
import ru.sepiolsmp.skins.net.Json;

/**
 * Writes the skin png, a rendered head and a small json next to the plugin, so
 * that SepiolCore can serve local avatars from its own port. That keeps the web
 * profile independent from minotar/crafatar and from any external service.
 *
 * Layout: plugins/SepiolSkins/web/{skins,heads,textures}/&lt;nick&gt;.(png|json)
 */
public final class WebExporter {

	private final Path root;
	private final Http http;
	private final Logger logger;
	private final boolean enabled;
	private final int headSize;
	private final int timeoutSeconds;

	public WebExporter(Path dataFolder, Http http, Logger logger, boolean enabled, int headSize,
			int timeoutSeconds) {
		this.root = dataFolder.resolve("web");
		this.http = http;
		this.logger = logger;
		this.enabled = enabled;
		this.headSize = Math.max(8, Math.min(512, headSize));
		this.timeoutSeconds = timeoutSeconds;
	}

	public Path root() {
		return root;
	}

	/** Blocking, network + disk. Call from an async thread only. */
	public void export(String name, SkinData data) {
		if (!enabled || data == null) {
			return;
		}
		String id = SkinStore.key(name);
		if (id.isEmpty()) {
			return;
		}
		try {
			Files.createDirectories(root.resolve("skins"));
			Files.createDirectories(root.resolve("heads"));
			Files.createDirectories(root.resolve("textures"));

			if (data.skinUrl != null && !data.skinUrl.isBlank()) {
				byte[] png = http.bytes(data.skinUrl, timeoutSeconds);
				Files.write(root.resolve("skins").resolve(id + ".png"), png);
				BufferedImage skin = ImageIO.read(new ByteArrayInputStream(png));
				if (skin != null) {
					BufferedImage head = renderHead(skin);
					ImageIO.write(head, "png", root.resolve("heads").resolve(id + ".png").toFile());
				}
			}

			Map<String, Object> meta = new LinkedHashMap<>();
			meta.put("name", name);
			meta.put("source", data.source);
			meta.put("model", data.model == null ? "classic" : data.model);
			meta.put("skinUrl", data.skinUrl);
			meta.put("capeUrl", data.capeUrl);
			meta.put("pinned", data.pinned);
			meta.put("updated", data.fetchedAt);
			Files.writeString(root.resolve("textures").resolve(id + ".json"), Json.toJson(meta),
					StandardCharsets.UTF_8);
		} catch (IOException | InterruptedException e) {
			logger.warning("Cannot export web assets for " + name + ": " + e.getMessage());
		} catch (Exception e) {
			logger.warning("Unexpected export failure for " + name + ": " + e);
		}
	}

	/** 8x8 head from the skin, plus the hat layer, scaled with nearest neighbour. */
	private BufferedImage renderHead(BufferedImage skin) {
		BufferedImage head = new BufferedImage(headSize, headSize, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = head.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		try {
			graphics.drawImage(skin.getSubimage(8, 8, 8, 8), 0, 0, headSize, headSize, null);
			if (skin.getWidth() >= 64 && skin.getHeight() >= 64) {
				graphics.drawImage(skin.getSubimage(40, 8, 8, 8), 0, 0, headSize, headSize, null);
			}
		} finally {
			graphics.dispose();
		}
		return head;
	}
}
