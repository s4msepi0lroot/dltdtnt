package com.voidcanvas.spotifysync.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Typography, artwork and icon helpers of the design system.
 *
 * <p>Split out of {@link UiRender} purely to keep both files readable; the
 * static methods are re-exported from {@code UiRender} so call sites only ever
 * talk to one class.</p>
 */
final class UiRenderText {

    private UiRenderText() {
    }

    static Font font() {
        return Minecraft.getInstance().font;
    }

    static void text(GuiGraphics graphics, String value, int x, int y, int color, boolean shadow) {
        graphics.drawString(font(), value, x, y, color, shadow);
    }

    static void textRight(GuiGraphics graphics, String value, int rightX, int y, int color, boolean shadow) {
        graphics.drawString(font(), value, rightX - font().width(value), y, color, shadow);
    }

    static void textCentered(GuiGraphics graphics, String value, int centerX, int y, int color, boolean shadow) {
        graphics.drawString(font(), value, centerX - font().width(value) / 2, y, color, shadow);
    }

    static void textScaled(GuiGraphics graphics, String value, float x, float y, float scale,
                           int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font(), value, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    static void drawTracked(GuiGraphics graphics, String value, float x, float y, float scale,
                            int color, float tracking) {
        if (value == null || value.isEmpty()) {
            return;
        }
        Font font = font();
        float cursor = 0f;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0f);
        graphics.pose().scale(scale, scale, 1f);
        for (int i = 0; i < value.length(); i++) {
            String glyph = String.valueOf(value.charAt(i));
            int width = font.width(glyph);
            graphics.drawString(font, glyph, Math.round(cursor), 0, color, false);
            cursor += width + width * tracking;
        }
        graphics.pose().popPose();
    }

    static float trackedWidth(String value, float scale, float tracking) {
        if (value == null || value.isEmpty()) {
            return 0f;
        }
        Font font = font();
        float width = 0f;
        for (int i = 0; i < value.length(); i++) {
            int glyph = font.width(String.valueOf(value.charAt(i)));
            width += glyph + glyph * tracking;
        }
        return width * scale;
    }

    static String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    static String ellipsize(String value, int maxWidth) {
        Font font = font();
        if (value == null || value.isEmpty() || font.width(value) <= maxWidth) {
            return value == null ? "" : value;
        }
        String ellipsis = "\u2026";
        int ellipsisWidth = font.width(ellipsis);
        StringBuilder builder = new StringBuilder();
        int width = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int charWidth = font.width(String.valueOf(c));
            if (width + charWidth + ellipsisWidth > maxWidth) {
                break;
            }
            builder.append(c);
            width += charWidth;
        }
        return builder.append(ellipsis).toString();
    }

    static int marqueeOffset(String value, int available, int pixelsPerSecond) {
        int width = font().width(value);
        if (width <= available) {
            return 0;
        }
        int travel = width - available + 16;
        double seconds = System.nanoTime() / 1_000_000_000.0;
        double cycle = (travel * 2.0) / Math.max(1, pixelsPerSecond);
        double phase = (seconds % cycle) / cycle;
        double eased = phase < 0.5 ? phase * 2.0 : (1.0 - phase) * 2.0;
        eased = eased * eased * (3.0 - 2.0 * eased);
        return (int) (eased * travel);
    }

    // -------------------------------------------------------------- textures

    /**
     * Immediate-mode textured quad.
     *
     * <p>{@code GuiGraphics} batches coloured quads and flushes them at the end
     * of the frame, so an immediate draw used to land behind the panel it was
     * meant to sit on, which is why album art never appeared. Flushing the
     * pending batch first restores the correct paint order.</p>
     */
    static void image(GuiGraphics graphics, ResourceLocation texture,
                      float x, float y, float width, float height,
                      float red, float green, float blue, float alpha) {
        if (texture == null || width <= 0f || height <= 0f) {
            return;
        }
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(red, green, blue, alpha);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, x, y, 0f).setUv(0f, 0f);
        buffer.addVertex(matrix, x, y + height, 0f).setUv(0f, 1f);
        buffer.addVertex(matrix, x + width, y + height, 0f).setUv(1f, 1f);
        buffer.addVertex(matrix, x + width, y, 0f).setUv(1f, 0f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        graphics.flush();
    }
}
