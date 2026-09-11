package com.voidcanvas.spotifysync.client;

import com.voidcanvas.spotifysync.SpotifySync;
import com.voidcanvas.spotifysync.client.hud.LyricsHud;
import com.voidcanvas.spotifysync.client.hud.MiniPlayerHud;
import com.voidcanvas.spotifysync.client.input.KeyBindings;
import com.voidcanvas.spotifysync.client.render.Lyrics3DRenderer;
import com.voidcanvas.spotifysync.client.screen.ExpandedPlayerScreen;
import com.voidcanvas.spotifysync.client.screen.SpotifySettingsScreen;
import com.voidcanvas.spotifysync.config.SyncConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Client wiring: HUD layers, key mappings, polling and screen interaction. */
public final class ClientEvents {

    private ClientEvents() {
    }

    /** Mod bus: registration only. */
    @EventBusSubscriber(modid = SpotifySync.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.CHAT, SpotifySync.id("mini_player"), MiniPlayerHud.INSTANCE);
            event.registerAbove(VanillaGuiLayers.CHAT, SpotifySync.id("lyrics"), LyricsHud.INSTANCE);
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(KeyBindings.OPEN_SETTINGS);
            event.register(KeyBindings.OPEN_PLAYER);
            event.register(KeyBindings.TOGGLE_HUD);
            event.register(KeyBindings.CYCLE_LYRICS);
            event.register(KeyBindings.PLAY_PAUSE);
            event.register(KeyBindings.NEXT_TRACK);
            event.register(KeyBindings.PREVIOUS_TRACK);
        }
    }

    /** Game bus: runtime behaviour. */
    @EventBusSubscriber(modid = SpotifySync.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {

        private GameBus() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            SpotifyManager manager = SpotifyManager.get();
            manager.tick();
            handleKeys(manager);
        }

        private static void handleKeys(SpotifyManager manager) {
            Minecraft minecraft = Minecraft.getInstance();
            SyncConfig config = SyncConfig.get();

            while (KeyBindings.OPEN_SETTINGS.consumeClick()) {
                minecraft.setScreen(new SpotifySettingsScreen());
            }
            while (KeyBindings.OPEN_PLAYER.consumeClick()) {
                minecraft.setScreen(new ExpandedPlayerScreen());
            }
            while (KeyBindings.TOGGLE_HUD.consumeClick()) {
                config.hudEnabled = !config.hudEnabled;
                config.save();
                feedback(minecraft, Component.translatable(config.hudEnabled
                        ? "spotifysync.message.hud_on" : "spotifysync.message.hud_off"));
            }
            while (KeyBindings.CYCLE_LYRICS.consumeClick()) {
                config.lyricsMode = config.lyricsMode.next();
                config.save();
                feedback(minecraft, Component.translatable("spotifysync.message.lyrics_mode")
                        .append(Component.literal(": "))
                        .append(Component.translatable(config.lyricsMode.translationKey())));
            }
            while (KeyBindings.PLAY_PAUSE.consumeClick()) {
                manager.togglePlayPause();
            }
            while (KeyBindings.NEXT_TRACK.consumeClick()) {
                manager.next();
            }
            while (KeyBindings.PREVIOUS_TRACK.consumeClick()) {
                manager.previous();
            }
        }

        private static void feedback(Minecraft minecraft, Component message) {
            if (minecraft.gui != null) {
                minecraft.gui.setOverlayMessage(message, false);
            }
        }

        /** Draws the mini player over any open screen so it stays clickable. */
        @SubscribeEvent
        public static void onScreenRender(ScreenEvent.Render.Post event) {
            SyncConfig config = SyncConfig.get();
            if (!config.hudEnabled || !config.showOnScreens) {
                return;
            }
            if (event.getScreen() instanceof ExpandedPlayerScreen
                    || event.getScreen() instanceof SpotifySettingsScreen) {
                return;
            }
            MiniPlayerHud.INSTANCE.renderWithCursor(event.getGuiGraphics(),
                    (int) event.getMouseX(), (int) event.getMouseY());
            if (!(event.getScreen() instanceof ChatScreen)) {
                LyricsHud.INSTANCE.renderBand(event.getGuiGraphics());
            }
        }

        /** Click on the mini player -> expanded player. */
        @SubscribeEvent
        public static void onScreenClick(ScreenEvent.MouseButtonPressed.Pre event) {
            SyncConfig config = SyncConfig.get();
            if (!config.hudEnabled || !config.showOnScreens || event.getButton() != 0) {
                return;
            }
            if (event.getScreen() instanceof ExpandedPlayerScreen
                    || event.getScreen() instanceof SpotifySettingsScreen) {
                return;
            }
            if (MiniPlayerHud.INSTANCE.isMouseOver(event.getMouseX(), event.getMouseY())) {
                event.setCanceled(true);
                Minecraft.getInstance().setScreen(new ExpandedPlayerScreen());
            }
        }

        /** 3D lyrics ring in world space. */
        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                return;
            }
            Lyrics3DRenderer.render(event.getPoseStack(), event.getCamera());
        }
    }
}
