package ru.s4fmer.badhabits.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import ru.s4fmer.badhabits.BadHabits;
import ru.s4fmer.badhabits.network.BhStatusHolder;

/**
 * The HUD bars (dose + addiction), drawn in the lower left corner above the hotbar.
 *
 * <p>Everything is drawn with solid rectangles and text, so no extra texture has to be loaded and the
 * overlay looks identical on every resource pack. The bars only appear when the server actually sends
 * status packets and the player has something in the system.</p>
 */
@EventBusSubscriber(modid = BadHabits.MODID, value = Dist.CLIENT)
public final class BhHud {

    private static final int WIDTH = 66;
    private static final int ROW_HEIGHT = 22;

    private BhHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        if (!BhStatusHolder.fresh()) {
            return;
        }

        boolean showNicotine = BhStatusHolder.nicotineAddiction() > 0.05F || BhStatusHolder.nicotineDose() > 0.05F;
        boolean showNarcotic = BhStatusHolder.narcoticAddiction() > 0.05F || BhStatusHolder.narcoticDose() > 0.05F;
        if (!showNicotine && !showNarcotic) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int rows = (showNicotine ? 1 : 0) + (showNarcotic ? 1 : 0);
        int x = 6;
        int y = minecraft.getWindow().getGuiScaledHeight() - 34 - rows * ROW_HEIGHT;

        if (showNicotine) {
            drawRow(graphics, minecraft, x, y,
                    Component.translatable("badhabits.hud.nicotine"),
                    BhStatusHolder.nicotineAddiction(),
                    BhStatusHolder.nicotineDose(),
                    BhStatusHolder.nicotineStage());
            y += ROW_HEIGHT;
        }
        if (showNarcotic) {
            drawRow(graphics, minecraft, x, y,
                    Component.translatable("badhabits.hud.narcotic"),
                    BhStatusHolder.narcoticAddiction(),
                    BhStatusHolder.narcoticDose(),
                    BhStatusHolder.narcoticStage());
        }
    }

    private static void drawRow(GuiGraphics graphics, Minecraft minecraft, int x, int y,
                                Component label, float addiction, float dose, int stage) {
        graphics.fill(x, y, x + WIDTH, y + 20, 0x77000000);
        graphics.fill(x, y, x + WIDTH, y + 1, 0x33FFFFFF);

        graphics.drawString(minecraft.font, label, x + 3, y + 3, 0x00E3E3E3, false);

        String value = String.valueOf(Math.round(addiction));
        int valueWidth = minecraft.font.width(value);
        graphics.drawString(minecraft.font, value, x + WIDTH - 3 - valueWidth, y + 3,
                stage > 0 ? 0x00FF6B6B : 0x00BFBFBF, false);

        int barX = x + 3;
        int barWidth = WIDTH - 6;

        // addiction bar
        graphics.fill(barX, y + 13, barX + barWidth, y + 16, 0xFF2A2A2A);
        int addictionFill = Math.round(barWidth * clamp01(addiction / 100.0F));
        if (addictionFill > 0) {
            graphics.fill(barX, y + 13, barX + addictionFill, y + 16, addictionColor(addiction, stage));
        }

        // dose bar
        graphics.fill(barX, y + 16, barX + barWidth, y + 18, 0xFF1B1B1B);
        int doseFill = Math.round(barWidth * clamp01(dose / 60.0F));
        if (doseFill > 0) {
            graphics.fill(barX, y + 16, barX + doseFill, y + 18, dose >= 55.0F ? 0xFFFFAA00 : 0xFF4FC3F7);
        }

        if (stage > 0) {
            graphics.drawString(minecraft.font,
                    Component.translatable("badhabits.hud.withdrawal", stage),
                    x + 3, y + 21 - 12, 0x00FF5555, false);
        }
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        return Math.min(value, 1.0F);
    }

    private static int addictionColor(float addiction, int stage) {
        if (stage > 0) {
            return 0xFFFF4444;
        }
        if (addiction >= 70.0F) {
            return 0xFFFF7043;
        }
        if (addiction >= 35.0F) {
            return 0xFFFFD54F;
        }
        return 0xFF81C784;
    }
}
