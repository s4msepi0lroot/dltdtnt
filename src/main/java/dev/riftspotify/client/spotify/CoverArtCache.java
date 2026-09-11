package dev.riftspotify.client.spotify;

import com.mojang.blaze3d.platform.NativeImage;
import dev.riftspotify.RiftSpotify;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Downloads the current album image off-thread and installs it on the client thread. */
public final class CoverArtCache {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(RiftSpotify.MOD_ID, "spotify_cover");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static volatile String loadedTrackId = "";
    private static volatile boolean loading;

    private CoverArtCache() {}

    public static ResourceLocation texture() { return loadedTrackId.isBlank() ? null : TEXTURE; }

    public static void request(SpotifyTrack track) {
        if (track == null || track.coverUrl() == null || track.coverUrl().isBlank() || loading || track.id().equals(loadedTrackId)) return;
        loading = true;
        Thread.startVirtualThread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(track.coverUrl())).GET().build();
                byte[] bytes = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
                Minecraft.getInstance().execute(() -> {
                    try {
                        NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                        Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(image));
                        loadedTrackId = track.id();
                    } catch (Exception ignored) {}
                    loading = false;
                });
            } catch (Exception ignored) {
                loading = false;
            }
        });
    }
}
