package dev.vitalstages.client;
import dev.vitalstages.network.HealthSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** POC без подмены чужого post-chain. Полный GLSL pipeline намеренно отложен. */
public final class VitalsOverlay {
    private VitalsOverlay() {}
    public static void render(GuiGraphics gui) {
        HealthSyncPayload p = ClientHealthState.current(); if (p == null) return;
        Minecraft mc = Minecraft.getInstance(); int w = gui.guiWidth(), h = gui.guiHeight();
        if (ClientHealthState.unconscious()) {
            gui.fill(0, 0, w, h, 0xFF000000);
            gui.drawCenteredString(mc.font, Component.translatable("vitalstages.unconscious"), w / 2, h / 2 - 12, 0xFFFFFFFF);
            int seconds = Math.max(0, (p.deathWindowTicks() - p.downTicks() + 19) / 20);
            gui.drawCenteredString(mc.font, Component.translatable("vitalstages.rescue_window", seconds), w / 2, h / 2 + 4, 0xFFB0B0B0);
            return;
        }
        int alpha = Math.round(ClientHealthState.vignette() * 255);
        int bx = Math.max(1, w / 5), by = Math.max(1, h / 4);
        gui.fillGradient(0, 0, w, by, alpha << 24, 0);
        gui.fillGradient(0, h - by, w, h, 0, alpha << 24);
        // GuiGraphics имеет вертикальный gradient; горизонтальные края — 32 недорогие полосы.
        for (int i = 0; i < 32; i++) {
            int a = Math.round(alpha * (1 - (i + 0.5f) / 32));
            int left = bx * i / 32, right = bx * (i + 1) / 32;
            gui.fill(left, 0, right, h, a << 24); gui.fill(w - right, 0, w - left, h, a << 24);
        }
        AnatomyHud.render(gui, p);
    }
}
