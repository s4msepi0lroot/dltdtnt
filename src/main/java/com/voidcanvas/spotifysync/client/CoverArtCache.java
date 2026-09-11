package com.voidcanvas.spotifysync.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.voidcanvas.spotifysync.SpotifySync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Downloads album covers off-thread and uploads them into a dynamic texture on
 * the render thread. Only one cover is kept alive at a time, plus the average
 * colour of the artwork which is used to tint the UI accents.
 */
public final class CoverArtCache {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final HttpClient http;
    private final ExecutorService executor;

    private volatile String loadedUrl;
    private volatile String requestedUrl;
    private volatile ResourceLocation texture;
    private volatile int textureWidth = 1;
    private volatile int textureHeight = 1;
    private volatile int averageColor = 0x0099FF;

    public CoverArtCache(HttpClient http, ExecutorService executor) {
        this.http = http;
        this.executor = executor;
    }

    public ResourceLocation texture() {
        return texture;
    }

    public int textureWidth() {
        return textureWidth;
    }

    public int textureHeight() {
        return textureHeight;
    }

    /** Average artwork colour, used for the ambient glow behind the player. */
    public int averageColor() {
        return averageColor;
    }

    public boolean hasCover() {
        return texture != null;
    }

    /** Requests the given cover URL; no-op when it is already loaded. */
    public void request(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (Objects.equals(url, loadedUrl) || Objects.equals(url, requestedUrl)) {
            return;
        }
        requestedUrl = url;
        if (isLocalPath(url)) {
            executor.execute(() -> readLocal(url));
        } else {
            executor.execute(() -> download(url));
        }
    }

    private static boolean isLocalPath(String url) {
        return url.startsWith("file:") || url.length() > 2 && url.charAt(1) == ':';
    }

    /** Loads artwork extracted from the Windows media session off disk. */
    private void readLocal(String url) {
        try {
            String raw = url.startsWith("file:") ? url.substring("file:".length()) : url;
            java.nio.file.Path path = java.nio.file.Paths.get(raw);
            if (!java.nio.file.Files.isRegularFile(path)) {
                return;
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(path);
            Minecraft.getInstance().execute(() -> upload(url, bytes));
        } catch (Exception e) {
            SpotifySync.LOGGER.debug("[Spotify Sync] local cover read failed", e);
        }
    }

    private void download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "SpotifySync-Minecraft/1.0.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                return;
            }
            byte[] bytes = response.body();
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> upload(url, bytes));
        } catch (Exception e) {
            SpotifySync.LOGGER.debug("[Spotify Sync] cover download failed", e);
        }
    }

    private void upload(String url, byte[] bytes) {
        Minecraft minecraft = Minecraft.getInstance();
        try (NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes))) {
            int width = image.getWidth();
            int height = image.getHeight();
            int average = computeAverage(image);

            NativeImage copy = new NativeImage(width, height, false);
            image.copyRect(copy, 0, 0, 0, 0, width, height, false, false);

            ResourceLocation previous = texture;
            ResourceLocation next = SpotifySync.id("textures/dynamic/cover_" + COUNTER.incrementAndGet());
            minecraft.getTextureManager().register(next, new DynamicTexture(copy));

            textureWidth = width;
            textureHeight = height;
            averageColor = average;
            texture = next;
            loadedUrl = url;

            if (previous != null) {
                minecraft.getTextureManager().release(previous);
            }
        } catch (Exception e) {
            SpotifySync.LOGGER.debug("[Spotify Sync] cover upload failed", e);
        }
    }

    private static int computeAverage(NativeImage image) {
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        int samples = 0;
        int step = Math.max(1, Math.min(image.getWidth(), image.getHeight()) / 24);
        for (int x = 0; x < image.getWidth(); x += step) {
            for (int y = 0; y < image.getHeight(); y += step) {
                int abgr = image.getPixelRGBA(x, y);
                red += abgr & 0xFF;
                green += (abgr >> 8) & 0xFF;
                blue += (abgr >> 16) & 0xFF;
                samples++;
            }
        }
        if (samples == 0) {
            return 0x0099FF;
        }
        int r = (int) (red / samples);
        int g = (int) (green / samples);
        int b = (int) (blue / samples);
        return (r << 16) | (g << 8) | b;
    }

    public void clear() {
        ResourceLocation previous = texture;
        texture = null;
        loadedUrl = null;
        requestedUrl = null;
        if (previous != null) {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().getTextureManager().release(previous));
        }
    }
}
