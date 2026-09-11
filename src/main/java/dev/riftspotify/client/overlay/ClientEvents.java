package dev.riftspotify.client.overlay;

import dev.riftspotify.client.spotify.SpotifyClient;
import dev.riftspotify.client.ui.SpotifySettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        SpotifyClient.tick();
        PlayerOverlay.render(event.getGuiGraphics(), event.getGuiGraphics().guiWidth(), event.getGuiGraphics().guiHeight());
        PlayerOverlay.renderLyrics(event.getGuiGraphics(), event.getGuiGraphics().guiWidth(), event.getGuiGraphics().guiHeight());
    }

    @SubscribeEvent
    public static void onMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (PlayerOverlay.hit((int) event.getMouseX(), (int) event.getMouseY(), event.getScreen().width)) {
            event.setCanceled(true);
            PlayerOverlay.openExpanded();
        }
    }

    @SubscribeEvent
    public static void onLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !dev.riftspotify.client.config.RiftConfig.get().showWorldLyrics) return;
        if (SpotifyClient.track() == null || SpotifyClient.lyrics().lines().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int active = dev.riftspotify.client.spotify.LrcLine.activeIndex(SpotifyClient.lyrics().lines(), SpotifyClient.position());
        if (active < 0) return;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(mc.player.getX() - camera.x, mc.player.getY() + 2.2 - camera.y, mc.player.getZ() - camera.z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025f, -0.025f, 0.025f);
        String text = SpotifyClient.lyrics().lines().get(active).text();
        int w = mc.font.width(text);
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        mc.font.drawInBatch(Component.literal(text), -w / 2f, 0, 0xffffffff, true, pose.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH, 0x550099ff, LightTexture.FULL_BRIGHT);
        buffer.endBatch();
        pose.popPose();
    }
}
