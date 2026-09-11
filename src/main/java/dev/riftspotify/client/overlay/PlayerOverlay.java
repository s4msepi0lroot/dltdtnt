package dev.riftspotify.client.overlay;

import dev.riftspotify.client.config.RiftConfig;
import dev.riftspotify.client.spotify.CoverArtCache;
import dev.riftspotify.client.spotify.LrcLine;
import dev.riftspotify.client.spotify.Lyrics;
import dev.riftspotify.client.spotify.SpotifyClient;
import dev.riftspotify.client.spotify.SpotifyTrack;
import dev.riftspotify.client.ui.ExpandedPlayerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class PlayerOverlay {
    public static final int HEIGHT = 54;
    private PlayerOverlay() {}

    public static void render(GuiGraphics g, int width, int height) {
        if (!RiftConfig.get().showMiniPlayer) return;
        SpotifyTrack track = SpotifyClient.track();
        if (track == null) return;
        int x = width - 286, y = 10;
        g.fill(x, y, width - 10, y + HEIGHT, 0xf0151515);
        g.fill(x, y, x + 3, y + HEIGHT, 0xff0099ff);
        g.fill(x + 12, y + 10, x + 46, y + 44, 0xff0d2c44);
        if (CoverArtCache.texture() != null) {
            g.blit(CoverArtCache.texture(), x + 12, y + 10, 0, 0, 34, 34, 34, 34);
        } else {
            g.fill(x + 17, y + 15, x + 41, y + 39, 0xff0099ff);
        }
        g.drawString(Minecraft.getInstance().font, Component.literal(trim(track.name(), 24)), x + 56, y + 10, 0xffffffff, true);
        g.drawString(Minecraft.getInstance().font, Component.literal(trim(track.artist(), 28)), x + 56, y + 25, 0xff999999, false);
        g.drawString(Minecraft.getInstance().font, Component.literal(track.playing() ? "▶" : "Ⅱ"), x + 56, y + 39, 0xff00bb88, true);
        g.drawString(Minecraft.getInstance().font, Component.literal(track.progressLabel() + " / " + track.durationLabel()), x + 88, y + 39, 0xff666666, false);
        float progress = track.durationMs() == 0 ? 0 : (float) SpotifyClient.position() / track.durationMs();
        g.fill(x + 56, y + 50, width - 16, y + 52, 0xff333333);
        g.fill(x + 56, y + 50, x + 56 + (int)((width - x - 72) * progress), y + 52, 0xff0099ff);
    }

    public static void renderLyrics(GuiGraphics g, int width, int height) {
        if (!RiftConfig.get().showHudLyrics) return;
        Lyrics lyrics = SpotifyClient.lyrics();
        if (!lyrics.available()) return;
        int active = LrcLine.activeIndex(lyrics.lines(), SpotifyClient.position());
        String line = active >= 0 ? lyrics.lines().get(active).text() : lyrics.plainText().lines().findFirst().orElse("");
        if (line.isBlank()) return;
        int textWidth = Minecraft.getInstance().font.width(line);
        int x = Math.max(12, (width - textWidth) / 2);
        int y = height - 72;
        g.fill(x - 12, y - 8, x + textWidth + 12, y + 18, 0xd9000000);
        g.drawString(Minecraft.getInstance().font, Component.literal(line), x, y, 0xffffffff, true);
    }

    public static boolean hit(int mouseX, int mouseY, int width) { return mouseX >= width - 286 && mouseX <= width - 10 && mouseY >= 10 && mouseY <= 64; }
    public static void openExpanded() { Minecraft.getInstance().setScreen(new ExpandedPlayerScreen(Minecraft.getInstance().screen)); }
    private static String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…"; }
}
