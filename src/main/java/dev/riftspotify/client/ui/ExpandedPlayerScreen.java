package dev.riftspotify.client.ui;

import dev.riftspotify.client.config.RiftConfig;
import dev.riftspotify.client.spotify.CoverArtCache;
import dev.riftspotify.client.spotify.LrcLine;
import dev.riftspotify.client.spotify.SpotifyClient;
import dev.riftspotify.client.spotify.SpotifyTrack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ExpandedPlayerScreen extends Screen {
    private final Screen parent;
    public ExpandedPlayerScreen(Screen parent) { super(Component.literal("RIFT PLAYER")); this.parent = parent; }

    @Override protected void init() {
        int cx = width / 2;
        addRenderableWidget(Button.builder(Component.literal("PREV"), b -> SpotifyClient.previous()).bounds(cx - 150, height - 56, 70, 26).build());
        addRenderableWidget(Button.builder(Component.literal("PLAY / PAUSE"), b -> SpotifyClient.playPause()).bounds(cx - 70, height - 56, 140, 26).build());
        addRenderableWidget(Button.builder(Component.literal("NEXT"), b -> SpotifyClient.next()).bounds(cx + 80, height - 56, 70, 26).build());
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), b -> minecraft.setScreen(parent)).bounds(18, height - 56, 70, 26).build());
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xff000000);
        int panel = Math.min(760, width - 56), left = (width - panel) / 2;
        g.fill(left, 28, left + panel, height - 20, 0xff111111);
        g.fill(left, 28, left + 3, height - 20, 0xff0099ff);
        SpotifyTrack track = SpotifyClient.track();
        if (track == null) {
            g.drawString(font, Component.literal("NOTHING PLAYING"), left + 32, 64, 0xffffffff, true);
            g.drawString(font, Component.literal("Start a track in Spotify, then return here."), left + 32, 84, 0xff999999, false);
        } else {
            g.fill(left + 32, 64, left + 202, 234, 0xff0d2c44);
            if (CoverArtCache.texture() != null) {
                g.blit(CoverArtCache.texture(), left + 32, 64, 0, 0, 170, 170, 170, 170);
            } else {
                g.fill(left + 50, 82, left + 184, 216, 0xff0099ff);
            }
            g.drawString(font, Component.literal(track.name()), left + 230, 72, 0xffffffff, true);
            g.drawString(font, Component.literal(track.artist()), left + 230, 94, 0xff999999, false);
            g.drawString(font, Component.literal(track.album()), left + 230, 114, 0xff666666, false);
            int barLeft = left + 32, barRight = left + panel - 32, barY = 256;
            g.fill(barLeft, barY, barRight, barY + 3, 0xff333333);
            float progress = track.durationMs() == 0 ? 0 : (float) SpotifyClient.position() / track.durationMs();
            g.fill(barLeft, barY, barLeft + (int)((barRight - barLeft) * progress), barY + 3, 0xff0099ff);
            g.drawString(font, Component.literal(track.progressLabel()), barLeft, 268, 0xff999999, false);
            g.drawString(font, Component.literal(track.durationLabel()), barRight - font.width(track.durationLabel()), 268, 0xff999999, false);
            g.drawString(font, Component.literal("LYRICS / " + SpotifyClient.lyrics().source()), left + 32, 308, 0xff00bb88, true);
            int active = LrcLine.activeIndex(SpotifyClient.lyrics().lines(), SpotifyClient.position());
            for (int i = Math.max(0, active - 2); i < Math.min(SpotifyClient.lyrics().lines().size(), active + 4); i++) {
                int color = i == active ? 0xffffffff : 0xff666666;
                g.drawString(font, Component.literal(SpotifyClient.lyrics().lines().get(i).text()), left + 32, 336 + (i - active + 2) * 18, color, i == active);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
}
