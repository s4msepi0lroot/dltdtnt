package dev.riftspotify.client.ui;

import dev.riftspotify.client.config.RiftConfig;
import dev.riftspotify.client.spotify.SpotifyClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SpotifySettingsScreen extends Screen {
    private final Screen parent;
    private EditBox clientId;

    public SpotifySettingsScreen(Screen parent) { super(Component.literal("RIFT / SPOTIFY")); this.parent = parent; }

    @Override protected void init() {
        RiftConfig cfg = RiftConfig.get();
        int left = Math.max(24, width / 2 - 220);
        clientId = new EditBox(font, left, 100, 440, 24, Component.literal("Spotify Client ID"));
        clientId.setValue(cfg.clientId);
        clientId.setHint(Component.literal("paste your Spotify app Client ID"));
        addRenderableWidget(clientId);
        addRenderableWidget(Button.builder(Component.literal(SpotifyClient.connected() ? "CONNECTED · DISCONNECT" : "CONNECT SPOTIFY"), b -> {
            if (SpotifyClient.connected()) SpotifyClient.disconnect(); else SpotifyClient.login(clientId.getValue());
        }).bounds(left, 136, 210, 28).build());
        addRenderableWidget(Button.builder(Component.literal("OPEN FULL PLAYER"), b -> minecraft.setScreen(new ExpandedPlayerScreen(this))).bounds(left + 230, 136, 210, 28).build());
        addRenderableWidget(Button.builder(Component.literal(toggleLabel("Show compact player on every HUD / screen", cfg.showMiniPlayer)), b -> {
            cfg.showMiniPlayer = !cfg.showMiniPlayer; cfg.save();
        }).bounds(left, 184, 440, 24).build());
        addRenderableWidget(Button.builder(Component.literal(toggleLabel("Show synced lyrics at the bottom of the HUD", cfg.showHudLyrics)), b -> {
            cfg.showHudLyrics = !cfg.showHudLyrics; cfg.save();
        }).bounds(left, 214, 440, 24).build());
        addRenderableWidget(Button.builder(Component.literal(toggleLabel("Show lyrics floating around the player in 3D", cfg.showWorldLyrics)), b -> {
            cfg.showWorldLyrics = !cfg.showWorldLyrics; cfg.save();
        }).bounds(left, 244, 440, 24).build());
        addRenderableWidget(Button.builder(Component.literal(toggleLabel("Fetch lyrics automatically (LRCLIB)", cfg.autoFetchLyrics)), b -> {
            cfg.autoFetchLyrics = !cfg.autoFetchLyrics; cfg.save();
        }).bounds(left, 274, 440, 24).build());
        addRenderableWidget(Button.builder(Component.literal("BACK"), b -> minecraft.setScreen(parent)).bounds(left, height - 44, 90, 26).build());
    }

    private static String toggleLabel(String label, boolean enabled) {
        return (enabled ? "[ON]  " : "[OFF] ") + label;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xff000000);
        int left = Math.max(24, width / 2 - 220);
        g.fill(left - 16, 42, left + 456, height - 24, 0xff111111);
        g.fill(left - 16, 42, left - 13, height - 24, 0xff0099ff);
        g.drawString(font, Component.literal("RIFT / SPOTIFY"), left, 58, 0xffffffff, true);
        g.drawString(font, Component.literal("A live music layer for your Minecraft window"), left, 76, 0xff999999, false);
        g.drawString(font, Component.literal("CLIENT ID"), left, 91, 0xff666666, false);
        g.drawString(font, Component.literal("O  ·  open this panel anytime"), left, height - 26, 0xff666666, false);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
