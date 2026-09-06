package dev.vitalstages.client;

import dev.vitalstages.config.HudConfig;
import dev.vitalstages.health.BodyPart;
import dev.vitalstages.health.LimbStatus;
import dev.vitalstages.network.HealthSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Анатомия дополняет vanilla HUD. Символы дублируют цвет; маленький экран уменьшает панель целиком. */
public final class AnatomyHud {
    private static final int WIDTH = 144, HEIGHT = 192;
    private static final int TEXT = 0xFFF2F1EC, MUTED = 0xFFBAC1C8;
    private AnatomyHud() {}
    public static void render(GuiGraphics gui, HealthSyncPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !HudConfig.SHOW_HUD.get()) return;
        float scale = Math.min(HudConfig.SCALE.get().floatValue(), Math.min(
                (gui.guiWidth() - 16) / (float) WIDTH, (gui.guiHeight() - 56) / (float) HEIGHT));
        if (scale <= 0) return;
        int x = Mth.clamp(HudConfig.X.get(), 0, Math.max(0, gui.guiWidth() - (int) Math.ceil(WIDTH * scale)));
        int y = Mth.clamp(HudConfig.Y.get(), 0, Math.max(0, gui.guiHeight() - 48 - (int) Math.ceil(HEIGHT * scale)));
        gui.pose().pushPose();
        try {
            gui.pose().translate(x, y, 0);
            gui.pose().scale(scale, scale, 1);
            gui.fill(0, 0, WIDTH, HEIGHT, 0xE8192027);
            gui.fill(0, 0, 3, HEIGHT, 0xFF629CC7);
            gui.drawString(mc.font, Component.translatable("vitalstages.hud.title"), 10, 7, TEXT);
            if (p.medicineFlags() != 0) {
                String marks = ((p.medicineFlags() & 1) != 0 ? "P" : "") + ((p.medicineFlags() & 2) != 0 ? "A" : "");
                gui.fill(112, 4, 136, 19, 0xFF304456);
                gui.drawCenteredString(mc.font, marks, 124, 7, 0xFFD5EAFE);
            }
            bar(gui, 24, p.blood(), 0xFFE97366, "vitalstages.hud.blood");
            bar(gui, 46, p.consciousness(), 0xFF74AEE8, "vitalstages.hud.consciousness");
            part(gui, p, BodyPart.HEAD, 62, 68, 22, 16);
            part(gui, p, BodyPart.TORSO, 50, 88, 46, 28);
            part(gui, p, BodyPart.LEFT_ARM, 25, 88, 21, 28);
            part(gui, p, BodyPart.RIGHT_ARM, 100, 88, 21, 28);
            part(gui, p, BodyPart.LEFT_LEG, 50, 120, 21, 30);
            part(gui, p, BodyPart.RIGHT_LEG, 75, 120, 21, 30);
            gui.drawString(mc.font, Component.translatable("vitalstages.hud.legend_fracture"), 8, 157, MUTED);
            gui.drawString(mc.font, Component.translatable("vitalstages.hud.legend_wound"), 8, 168, MUTED);
            gui.drawString(mc.font, Component.translatable("vitalstages.hud.footer",
                    Math.round(p.health() * 10) / 10.0f, Math.round(p.temperature() * 10) / 10.0f), 8, 180, MUTED);
        } finally { gui.pose().popPose(); }
    }
    private static void bar(GuiGraphics gui, int y, float value, int color, String label) {
        var font = Minecraft.getInstance().font;
        gui.drawString(font, Component.translatable(label, Math.round(value)), 8, y - 4, TEXT);
        gui.fill(8, y + 7, 136, y + 11, 0xFF38424E);
        int width = Math.round(128 * Mth.clamp(value / 100, 0, 1));
        if (width > 0) gui.fill(8, y + 7, 8 + width, y + 11, color);
    }
    private static void part(GuiGraphics gui, HealthSyncPayload p, BodyPart part, int x, int y, int w, int h) {
        var font = Minecraft.getInstance().font;
        int status = (p.packedLimbs() >>> (part.ordinal() * 2)) & 3;
        boolean splinted = status == LimbStatus.FRACTURED.ordinal() && (p.splintedMask() & part.bit()) != 0;
        boolean bleeding = (p.openCutMask() & part.bit()) != 0;
        int color = splinted ? 0xFF7BC7AA : status == LimbStatus.FRACTURED.ordinal() ? 0xFFE97366
                : status == LimbStatus.BRUISED.ordinal() ? 0xFFE4AD6A : 0xFF899BA8;
        String mark = splinted ? "+" : status == LimbStatus.FRACTURED.ordinal() ? "x"
                : status == LimbStatus.BRUISED.ordinal() ? "!" : "-";
        gui.fill(x, y, x + w, y + h, color);
        gui.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF242D36);
        Component label = Component.translatable("vitalstages.part." + part.name().toLowerCase(java.util.Locale.ROOT));
        if (h < 22) {
            gui.drawCenteredString(font, label.copy().append((mark.equals("-") ? "" : mark) + (bleeding ? "B" : "")), x + w / 2, y + 4, TEXT);
        } else {
            gui.drawCenteredString(font, label, x + w / 2, y + 4, TEXT);
            gui.drawCenteredString(font, mark + (bleeding ? "B" : ""), x + w / 2, y + h - 11, color);
        }
        // Внешняя красная полоска + отдельный символ B отличают открытую рану от одного перелома.
        if (bleeding) {
            gui.fill(x - 3, y, x - 1, y + h, 0xFFE97366);
            // B уже нарисован внутри ячейки: не перекрывает соседнюю часть тела.
        }
    }
}
