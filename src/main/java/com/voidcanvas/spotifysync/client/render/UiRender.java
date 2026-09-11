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

/**
 * Low level drawing helpers implementing the void-canvas visual language:
 * slightly rounded near-black cards, hairline borders, accent rules, animated
 * film grain and scaled textured quads for album artwork.
 */
public final class UiRender {

    private UiRender() {
    }

    // ------------------------------------------------------------------ shapes

    /** Filled rectangle with slightly rounded corners (radius in GUI pixels). */
    public static void roundedRect(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + r, y, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + r, y + height - r, color);
        graphics.fill(x + width - r, y + r, x + width, y + height - r, color);
        for (int i = 0; i < r; i++) {
            int inset = r - (int) Math.floor(Math.sqrt(Math.max(0.0, r * r - (r - i - 1) * (r - i - 1))));
            graphics.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
            graphics.fill(x + inset, y + height - i - 1, x + width - inset, y + height - i, color);
        }
    }

    /** 1px hairline border following the same rounded silhouette. */
    public static void roundedBorder(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        graphics.fill(x + r, y, x + width - r, y + 1, color);
        graphics.fill(x + r, y + height - 1, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + 1, y + height - r, color);
        graphics.fill(x + width - 1, y + r, x + width, y + height - r, color);
        for (int i = 0; i < r; i++) {
            int inset = r - (int) Math.floor(Math.sqrt(Math.max(0.0, r * r - (r - i - 1) * (r - i - 1))));
            graphics.fill(x + inset, y + i, x + inset + 1, y + i + 1, color);
            graphics.fill(x + width - inset - 1, y + i, x + width - inset, y + i + 1, color);
            graphics.fill(x + inset, y + height - i - 1, x + inset + 1, y + height - i, color);
            graphics.fill(x + width - inset - 1, y + height - i - 1, x + width - inset, y + height - i, color);
        }
    }

    /** Card = near-black surface + hairline border, the system's base panel. */
    public static void card(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        roundedRect(graphics, x, y, width, height, UiTheme.RADIUS_CARD, UiTheme.withAlpha(UiTheme.SURFACE, alpha));
        roundedBorder(graphics, x, y, width, height, UiTheme.RADIUS_CARD, UiTheme.withAlpha(UiTheme.BORDER, alpha));
    }

    /** Card variant with the rationed accent hairline (featured panels only). */
    public static void accentCard(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        roundedRect(graphics, x, y, width, height, UiTheme.RADIUS_CARD_LG, UiTheme.withAlpha(UiTheme.SURFACE, alpha));
        roundedBorder(graphics, x, y, width, height, UiTheme.RADIUS_CARD_LG,
                UiTheme.withAlpha(UiTheme.accent(), alpha * 0.55f));
    }

    /** Translucent blue "glass" tile used for small stat modules. */
    public static void glassTile(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        roundedRect(graphics, x, y, width, height, UiTheme.RADIUS_CONTROL, UiTheme.withAlpha(UiTheme.GLASS, alpha));
    }

    /** Thin accent rule, the signature separator of the system. */
    public static void accentRule(GuiGraphics graphics, int x, int y, int width, float alpha) {
        graphics.fill(x, y, x + width, y + 1, UiTheme.accent(alpha));
    }

    public static void hairline(GuiGraphics graphics, int x, int y, int width, float alpha) {
        graphics.fill(x, y, x + width, y + 1, UiTheme.withAlpha(UiTheme.BORDER, alpha));
    }

    /** Progress track + accent fill + 2px playhead. */
    public static void progressBar(GuiGraphics graphics, int x, int y, int width, int height,
                                   float fraction, float alpha, boolean showHead) {
        int clampedHeight = Math.max(2, height);
        roundedRect(graphics, x, y, width, clampedHeight, clampedHeight / 2,
                UiTheme.withAlpha(UiTheme.TRACK, alpha));
        int filled = (int) (width * Math.max(0f, Math.min(1f, fraction)));
        if (filled > 0) {
            roundedRect(graphics, x, y, filled, clampedHeight, clampedHeight / 2, UiTheme.accent(alpha));
        }
        if (showHead && filled > 0) {
            int headX = Math.min(x + width - 1, x + filled);
            graphics.fill(headX - 1, y - 1, headX + 1, y + clampedHeight + 1,
                    UiTheme.withAlpha(UiTheme.TEXT_PRIMARY, alpha));
        }
    }

    /** Vertical fade into pure black, used to bleed media into the canvas. */
    public static void fadeToBlack(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        graphics.fillGradient(x, y, x + width, y + height,
                UiTheme.withAlpha(0x00000000, 0f), UiTheme.withAlpha(UiTheme.BACKGROUND, alpha));
    }

    /** Corner accent wash from the design spec (blue 10% to dark 50%). */
    public static void accentWash(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        graphics.fillGradient(x, y, x + width, y + height,
                UiTheme.accent(0.10f * alpha), UiTheme.withAlpha(0xFF1C1C1C, 0.5f * alpha));
    }

    /**
     * Subtle animated film grain. It textures flat black fields without
     * brightening them, exactly as the design guardrails require.
     */
    public static void grain(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        long frame = (System.nanoTime() / 90_000_000L);
        int step = 3;
        for (int gx = 0; gx < width; gx += step) {
            for (int gy = 0; gy < height; gy += step) {
                int hash = hash(gx + (int) (frame * 31L), gy - (int) (frame * 17L));
                int magnitude = hash & 0x7;
                if (magnitude < 5) {
                    continue;
                }
                int a = (int) ((magnitude - 4) * 6 * alpha);
                if (a <= 0) {
                    continue;
                }
                int color = (Math.min(a, 40) << 24) | 0xFFFFFF;
                graphics.fill(x + gx, y + gy, x + gx + 1, y + gy + 1, color);
            }
        }
    }

    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return h ^ (h >> 16);
    }

    // ------------------------------------------------------------------- text

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static void text(GuiGraphics graphics, String value, int x, int y, int color, boolean shadow) {
        graphics.drawString(font(), value, x, y, color, shadow);
    }

    public static void textCentered(GuiGraphics graphics, String value, int centerX, int y, int color, boolean shadow) {
        graphics.drawString(font(), value, centerX - font().width(value) / 2, y, color, shadow);
    }

    public static void textRight(GuiGraphics graphics, String value, int rightX, int y, int color, boolean shadow) {
        graphics.drawString(font(), value, rightX - font().width(value), y, color, shadow);
    }

    /** Draws text scaled around its top-left corner. */
    public static void textScaled(GuiGraphics graphics, String value, float x, float y, float scale,
                                  int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font(), value, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    public static void textScaledCentered(GuiGraphics graphics, String value, float centerX, float y, float scale,
                                          int color, boolean shadow) {
        float width = font().width(value) * scale;
        textScaled(graphics, value, centerX - width / 2f, y, scale, color, shadow);
    }

    /** Truncates with an ellipsis so labels never overflow their card. */
    public static String ellipsize(String value, int maxWidth) {
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

    /**
     * Horizontal marquee offset for text wider than the available space.
     * Returns 0 when the text fits.
     */
    public static int marqueeOffset(String value, int available, int pixelsPerSecond) {
        int width = font().width(value);
        if (width <= available) {
            return 0;
        }
        int travel = width - available + 16;
        double seconds = (System.nanoTime() / 1_000_000_000.0);
        double cycle = (travel * 2.0) / Math.max(1, pixelsPerSecond);
        double phase = (seconds % cycle) / cycle;
        double eased = phase < 0.5 ? phase * 2.0 : (1.0 - phase) * 2.0;
        return (int) (eased * travel);
    }

    // -------------------------------------------------------------- textures

    /**
     * Draws a texture scaled into the given rectangle with an alpha tint.
     *
     * <p>Implemented with an explicit quad instead of {@code GuiGraphics#blit}
     * so that arbitrary source sizes (Spotify covers are 640x640) map cleanly
     * onto any destination size.</p>
     */
    public static void image(GuiGraphics graphics, ResourceLocation texture,
                             float x, float y, float width, float height, float alpha) {
        image(graphics, texture, x, y, width, height, 1f, 1f, 1f, alpha);
    }

    public static void image(GuiGraphics graphics, ResourceLocation texture,
                             float x, float y, float width, float height,
                             float red, float green, float blue, float alpha) {
        if (texture == null || width <= 0f || height <= 0f) {
            return;
        }
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
    }

    /** Placeholder used while no artwork is available. */
    public static void coverPlaceholder(GuiGraphics graphics, int x, int y, int size, float alpha) {
        roundedRect(graphics, x, y, size, size, UiTheme.RADIUS_CONTROL,
                UiTheme.withAlpha(UiTheme.SURFACE_ALT, alpha));
        roundedBorder(graphics, x, y, size, size, UiTheme.RADIUS_CONTROL,
                UiTheme.withAlpha(UiTheme.BORDER, alpha));
        int inner = Math.max(4, size / 3);
        int cx = x + size / 2;
        int cy = y + size / 2;
        graphics.fill(cx - inner / 2, cy - 1, cx + inner / 2, cy, UiTheme.accent(alpha * 0.8f));
        graphics.fill(cx - inner / 3, cy + 2, cx + inner / 3, cy + 3, UiTheme.withAlpha(UiTheme.TEXT_MUTED, alpha));
    }

    // ------------------------------------------------------------------ icons

    /** Vector-ish play triangle. */
    public static void playIcon(GuiGraphics graphics, int x, int y, int size, int color) {
        for (int i = 0; i < size; i++) {
            int height = size - 2 * Math.abs(i - size / 2);
            if (height <= 0) {
                continue;
            }
            graphics.fill(x + i, y + (size - height) / 2, x + i + 1, y + (size + height) / 2, color);
        }
    }

    public static void pauseIcon(GuiGraphics graphics, int x, int y, int size, int color) {
        int bar = Math.max(1, size / 3);
        graphics.fill(x, y, x + bar, y + size, color);
        graphics.fill(x + size - bar, y, x + size, y + size, color);
    }

    public static void nextIcon(GuiGraphics graphics, int x, int y, int size, int color) {
        playIcon(graphics, x, y, size - 2, color);
        graphics.fill(x + size - 2, y, x + size - 1, y + size - 2, color);
    }

    public static void previousIcon(GuiGraphics graphics, int x, int y, int size, int color) {
        for (int i = 0; i < size - 2; i++) {
            int height = size - 2 - 2 * Math.abs((size - 2) / 2 - i);
            if (height <= 0) {
                continue;
            }
            int px = x + (size - 2) - i - 1 + 2;
            graphics.fill(px, y + (size - 2 - height) / 2, px + 1, y + (size - 2 + height) / 2, color);
        }
        graphics.fill(x, y, x + 1, y + size - 2, color);
    }

    public static void shuffleIcon(GuiGraphics graphics, int x, int y, int size, int color) {
        graphics.fill(x, y + 1, x + size, y + 2, color);
        graphics.fill(x, y + size - 2, x + size, y + size - 1, color);
        graphics.fill(x + size - 3, y, x + size - 2, y + 3, color);
        graphics.fill(x + size - 3, y + size - 3, x + size - 2, y + size, color);
    }

    public static void repeatIcon(GuiGraphics graphics, int x, int y, int size, int color) {
        graphics.fill(x, y, x + size, y + 1, color);
        graphics.fill(x, y, x + 1, y + size / 2, color);
        graphics.fill(x, y + size - 1, x + size, y + size, color);
        graphics.fill(x + size - 1, y + size / 2, x + size, y + size, color);
    }

    public static void chevronIcon(GuiGraphics graphics, int x, int y, int size, int color, boolean up) {
        int half = size / 2;
        for (int i = 0; i <= half; i++) {
            int rowY = up ? y + half - i : y + i;
            graphics.fill(x + half - i, rowY, x + half - i + 1, rowY + 1, color);
            graphics.fill(x + half + i, rowY, x + half + i + 1, rowY + 1, color);
        }
    }

    /** Small equaliser animation shown while a track is playing. */
    public static void equalizer(GuiGraphics graphics, int x, int y, int width, int height, int color, boolean animate) {
        int bars = Math.max(3, width / 3);
        int barWidth = Math.max(1, width / bars - 1);
        double time = System.nanoTime() / 1_000_000_000.0;
        for (int i = 0; i < bars; i++) {
            double phase = animate ? Math.sin(time * 4.0 + i * 1.7) * 0.5 + 0.5 : 0.25;
            int barHeight = (int) Math.max(1, phase * height);
            int barX = x + i * (barWidth + 1);
            graphics.fill(barX, y + height - barHeight, barX + barWidth, y + height, color);
        }
    }
}
