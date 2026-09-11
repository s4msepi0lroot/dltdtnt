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
 * Drawing primitives for the Obsidian and Lime glassmorphism system.
 *
 * <p>Everything the UI needs lives here: the floating shell, glass panels with
 * a faux backdrop blur, pill buttons with a neon glow, the decorative grid,
 * noise overlays, glow spheres, tracked-out mono labels, tight display
 * headings and scaled album artwork.</p>
 */
public final class UiRender {

    private UiRender() {
    }

    // ------------------------------------------------------------------ shapes

    /** Filled rectangle with rounded corners (radius in GUI pixels). */
    public static void roundedRect(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
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
            int inset = cornerInset(r, i);
            graphics.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
            graphics.fill(x + inset, y + height - i - 1, x + width - inset, y + height - i, color);
        }
    }

    /** One pixel ring following the same rounded silhouette. */
    public static void roundedBorder(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        graphics.fill(x + r, y, x + width - r, y + 1, color);
        graphics.fill(x + r, y + height - 1, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + 1, y + height - r, color);
        graphics.fill(x + width - 1, y + r, x + width, y + height - r, color);
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(r, i);
            graphics.fill(x + inset, y + i, x + inset + 1, y + i + 1, color);
            graphics.fill(x + width - inset - 1, y + i, x + width - inset, y + i + 1, color);
            graphics.fill(x + inset, y + height - i - 1, x + inset + 1, y + height - i, color);
            graphics.fill(x + width - inset - 1, y + height - i - 1, x + width - inset, y + height - i, color);
        }
    }

    private static int cornerInset(int radius, int row) {
        double dy = radius - row - 1;
        return radius - (int) Math.floor(Math.sqrt(Math.max(0.0, radius * radius - dy * dy)));
    }

    /** Fully rounded pill fill. */
    public static void pill(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        roundedRect(graphics, x, y, width, height, height / 2, color);
    }

    public static void pillBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        roundedBorder(graphics, x, y, width, height, height / 2, color);
    }

    // ------------------------------------------------------------------ panels

    /** Floating shell: obsidian background, hairline ring, grid and noise. */
    public static void shell(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        int radius = UiTheme.radiusCard() + 2;
        roundedRect(graphics, x, y, width, height, radius, UiTheme.withAlpha(UiTheme.shell(), alpha));
        grid(graphics, x + 1, y + 1, width - 2, height - 2, alpha);
        noise(graphics, x + 1, y + 1, width - 2, height - 2, alpha);
        roundedBorder(graphics, x, y, width, height, radius, UiTheme.ring(alpha));
    }

    /** Glass panel: faux backdrop blur plus white overlay and hairline ring. */
    public static void glass(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        glass(graphics, x, y, width, height, UiTheme.radiusCard(), alpha);
    }

    public static void glass(GuiGraphics graphics, int x, int y, int width, int height, int radius, float alpha) {
        roundedRect(graphics, x, y, width, height, radius, UiTheme.scrim(alpha));
        roundedRect(graphics, x, y, width, height, radius, UiTheme.glass(alpha));
        roundedBorder(graphics, x, y, width, height, radius, UiTheme.ring(alpha));
    }

    /** Opaque card used for the biggest surfaces. */
    public static void card(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        int radius = UiTheme.radiusCard();
        roundedRect(graphics, x, y, width, height, radius, UiTheme.withAlpha(UiTheme.surface(), alpha));
        roundedRect(graphics, x, y, width, height, radius, UiTheme.glass(alpha * 0.6f));
        roundedBorder(graphics, x, y, width, height, radius, UiTheme.ring(alpha));
    }

    /** Solid accent card with noise, per the bento spec. */
    public static void accentCard(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        int radius = UiTheme.radiusCard();
        roundedRect(graphics, x, y, width, height, radius, UiTheme.accent(alpha));
        noise(graphics, x + 1, y + 1, width - 2, height - 2, alpha * 0.8f);
    }

    /** Small glass tile for technical metadata. */
    public static void glassTile(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        glass(graphics, x, y, width, height, Math.min(UiTheme.radiusCard(), height / 2), alpha);
    }

    /** Hairline separator in the border tint. */
    public static void hairline(GuiGraphics graphics, int x, int y, int width, float alpha) {
        graphics.fill(x, y, x + width, y + 1, UiTheme.ring(alpha));
    }

    /** Short accent rule, the signature separator of the system. */
    public static void accentRule(GuiGraphics graphics, int x, int y, int width, float alpha) {
        graphics.fill(x, y, x + width, y + 1, UiTheme.accent(alpha));
    }

    // -------------------------------------------------------------- decoration

    /** Linear gradient grid pattern behind panels. */
    public static void grid(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        if (!UiTheme.colors().gridPattern || width <= 0 || height <= 0) {
            return;
        }
        int color = UiTheme.argb(UiTheme.colors().border, UiTheme.colors().gridOpacity * 0.22f * alpha);
        int cell = UiTheme.GRID_CELL;
        for (int gx = cell; gx < width; gx += cell) {
            graphics.fill(x + gx, y, x + gx + 1, y + height, color);
        }
        for (int gy = cell; gy < height; gy += cell) {
            graphics.fill(x, y + gy, x + width, y + gy + 1, color);
        }
    }

    /** Grainy animated noise so dark panels never look flat. */
    public static void noise(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        if (!UiTheme.colors().noiseOverlay || width <= 0 || height <= 0) {
            return;
        }
        float strength = UiTheme.colors().noiseOpacity * alpha;
        if (strength <= 0.001f) {
            return;
        }
        long frame = System.nanoTime() / 110_000_000L;
        int step = 3;
        for (int gx = 0; gx < width; gx += step) {
            for (int gy = 0; gy < height; gy += step) {
                int hash = hash(gx + (int) (frame * 31L), gy - (int) (frame * 17L));
                int magnitude = hash & 0x7;
                if (magnitude < 5) {
                    continue;
                }
                int a = (int) ((magnitude - 4) * 26 * strength);
                if (a <= 0) {
                    continue;
                }
                graphics.fill(x + gx, y + gy, x + gx + 1, y + gy + 1,
                        (Math.min(a, 46) << 24) | (UiTheme.colors().glass & 0xFFFFFF));
            }
        }
    }

    /** Large soft radial glow sphere, approximated with concentric rings. */
    public static void glowSphere(GuiGraphics graphics, int centerX, int centerY, int radius, int rgb, float alpha) {
        if (!UiTheme.colors().glowSpheres || radius <= 0) {
            return;
        }
        float strength = UiTheme.colors().glowOpacity * alpha;
        int rings = 9;
        for (int i = rings; i > 0; i--) {
            float t = i / (float) rings;
            int r = (int) (radius * t);
            float ringAlpha = strength * 0.055f * (1f - t + 0.25f);
            roundedRect(graphics, centerX - r, centerY - r, r * 2, r * 2, r,
                    UiTheme.argb(rgb & 0xFFFFFF, ringAlpha));
        }
    }

    /** Neon glow around a pill button. */
    public static void neonGlow(GuiGraphics graphics, int x, int y, int width, int height, int rgb, float alpha) {
        for (int i = 1; i <= 4; i++) {
            float ringAlpha = alpha * (0.16f / i);
            roundedRect(graphics, x - i, y - i, width + i * 2, height + i * 2, (height + i * 2) / 2,
                    UiTheme.argb(rgb & 0xFFFFFF, ringAlpha));
        }
    }

    /** Vertical fade into the background colour. */
    public static void fadeToBackground(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        graphics.fillGradient(x, y, x + width, y + height, 0x00000000,
                UiTheme.argb(UiTheme.colors().background, alpha));
    }

    /** Data visualisation bars used by cards and the HUD. */
    public static void verticalBars(GuiGraphics graphics, int x, int y, int width, int height,
                                    int bars, int color, boolean animate, long seed) {
        int count = Math.max(1, bars);
        int gap = 2;
        int barWidth = Math.max(1, (width - gap * (count - 1)) / count);
        double time = System.nanoTime() / 1_000_000_000.0;
        for (int i = 0; i < count; i++) {
            double phase = animate
                    ? Math.sin(time * 3.2 + i * 0.9 + seed) * 0.5 + 0.5
                    : ((hash(i, (int) seed) >>> 8) & 0xFF) / 255.0;
            int barHeight = (int) Math.max(2, phase * height);
            int barX = x + i * (barWidth + gap);
            roundedRect(graphics, barX, y + height - barHeight, barWidth, barHeight,
                    Math.min(2, barWidth / 2), color);
        }
    }

    /** Pulsing dot of the system status tag. */
    public static void statusDot(GuiGraphics graphics, int x, int y, int rgb, float alpha) {
        float pulse = 0.55f + 0.45f * UiTheme.pulse(2.0);
        roundedRect(graphics, x - 1, y - 1, 5, 5, 2, UiTheme.argb(rgb & 0xFFFFFF, 0.22f * alpha * pulse));
        roundedRect(graphics, x, y, 3, 3, 1, UiTheme.argb(rgb & 0xFFFFFF, alpha));
    }

    /** Progress track, accent fill and playhead. */
    public static void progressBar(GuiGraphics graphics, int x, int y, int width, int height,
                                   float fraction, float alpha, boolean showHead) {
        int h = Math.max(2, height);
        roundedRect(graphics, x, y, width, h, h / 2, UiTheme.withAlpha(UiTheme.track(), alpha));
        int filled = (int) (width * Math.max(0f, Math.min(1f, fraction)));
        if (filled > 0) {
            roundedRect(graphics, x, y, filled, h, h / 2, UiTheme.accent(alpha));
        }
        if (showHead) {
            int headX = Math.max(x + 1, Math.min(x + width - 1, x + filled));
            roundedRect(graphics, headX - 2, y - 2, 4, h + 4, 2, UiTheme.textPrimary(alpha));
        }
    }

    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return h ^ (h >> 16);
    }

    // ------------------------------------------------------------------- text

    public static Font font() {
        return UiRenderText.font();
    }

    public static void text(GuiGraphics graphics, String value, int x, int y, int color, boolean shadow) {
        UiRenderText.text(graphics, value, x, y, color, shadow);
    }

    public static void textRight(GuiGraphics graphics, String value, int rightX, int y, int color, boolean shadow) {
        UiRenderText.textRight(graphics, value, rightX, y, color, shadow);
    }

    public static void textCentered(GuiGraphics graphics, String value, int centerX, int y, int color, boolean shadow) {
        UiRenderText.textCentered(graphics, value, centerX, y, color, shadow);
    }

    public static void textScaled(GuiGraphics graphics, String value, float x, float y, float scale,
                                  int color, boolean shadow) {
        UiRenderText.textScaled(graphics, value, x, y, scale, color, shadow);
    }

    public static void textScaledCentered(GuiGraphics graphics, String value, float centerX, float y, float scale,
                                          int color, boolean shadow) {
        UiRenderText.textScaled(graphics, value, centerX - font().width(value) * scale / 2f, y, scale, color, shadow);
    }

    public static void textScaledRight(GuiGraphics graphics, String value, float rightX, float y, float scale,
                                       int color, boolean shadow) {
        UiRenderText.textScaled(graphics, value, rightX - font().width(value) * scale, y, scale, color, shadow);
    }

    /** Display heading with the tight tracking of the design system. */
    public static void display(GuiGraphics graphics, String value, float x, float y, float scale, int color) {
        UiRenderText.drawTracked(graphics, value, x, y, scale, color, -0.06f);
    }

    public static float displayWidth(String value, float scale) {
        return UiRenderText.trackedWidth(value, scale, -0.06f);
    }

    /** Technical mono label: uppercase and tracked out, JetBrains Mono role. */
    public static void mono(GuiGraphics graphics, String value, float x, float y, float scale, int color) {
        UiRenderText.drawTracked(graphics, UiRenderText.upper(value), x, y, scale, color, 0.2f);
    }

    public static float monoWidth(String value, float scale) {
        return UiRenderText.trackedWidth(UiRenderText.upper(value), scale, 0.2f);
    }

    public static void monoRight(GuiGraphics graphics, String value, float rightX, float y, float scale, int color) {
        mono(graphics, value, rightX - monoWidth(value, scale), y, scale, color);
    }

    public static void monoCentered(GuiGraphics graphics, String value, float centerX, float y, float scale, int color) {
        mono(graphics, value, centerX - monoWidth(value, scale) / 2f, y, scale, color);
    }

    /** System status tag: pulsing dot plus tracked-out mono label. */
    public static void statusTag(GuiGraphics graphics, String label, int x, int y, int dotRgb, float alpha) {
        statusDot(graphics, x, y + 2, dotRgb, alpha);
        mono(graphics, label, x + 8, y, 0.7f, UiTheme.textSecondary(alpha));
    }

    public static float statusTagWidth(String label) {
        return 8f + monoWidth(label, 0.7f);
    }

    public static String ellipsize(String value, int maxWidth) {
        return UiRenderText.ellipsize(value, maxWidth);
    }

    public static int marqueeOffset(String value, int available, int pixelsPerSecond) {
        return UiRenderText.marqueeOffset(value, available, pixelsPerSecond);
    }

    /** Transition easing used by every animated widget. */
    public static float ease(float current, float target, float speed) {
        return current + (target - current) * Math.min(1f, Math.max(0f, speed));
    }

    // -------------------------------------------------------------- textures

    public static void image(GuiGraphics graphics, ResourceLocation texture,
                             float x, float y, float width, float height, float alpha) {
        UiRenderText.image(graphics, texture, x, y, width, height, 1f, 1f, 1f, alpha);
    }

    public static void image(GuiGraphics graphics, ResourceLocation texture,
                             float x, float y, float width, float height,
                             float red, float green, float blue, float alpha) {
        UiRenderText.image(graphics, texture, x, y, width, height, red, green, blue, alpha);
    }

    /** Album artwork with a rounded mask and the hairline ring. */
    public static void cover(GuiGraphics graphics, ResourceLocation texture,
                             int x, int y, int size, float alpha) {
        image(graphics, texture, x, y, size, size, alpha);
        maskCorners(graphics, x, y, size, size, UiTheme.radiusControl(), UiTheme.surface());
        roundedBorder(graphics, x, y, size, size, UiTheme.radiusControl(), UiTheme.ring(alpha));
    }

    /** Paints corners in the panel colour so a square texture reads as rounded. */
    public static void maskCorners(GuiGraphics graphics, int x, int y, int width, int height,
                                   int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(r, i);
            if (inset <= 0) {
                continue;
            }
            graphics.fill(x, y + i, x + inset, y + i + 1, color);
            graphics.fill(x + width - inset, y + i, x + width, y + i + 1, color);
            graphics.fill(x, y + height - i - 1, x + inset, y + height - i, color);
            graphics.fill(x + width - inset, y + height - i - 1, x + width, y + height - i, color);
        }
    }

    /** Placeholder artwork: glass square with an accent bar chart. */
    public static void coverPlaceholder(GuiGraphics graphics, int x, int y, int size, float alpha) {
        glass(graphics, x, y, size, size, UiTheme.radiusControl(), alpha);
        int inner = Math.max(10, size / 2);
        verticalBars(graphics, x + (size - inner) / 2, y + (size - inner / 2) / 2, inner, inner / 2,
                4, UiTheme.accent(0.6f * alpha), false, 7L);
    }

    // ------------------------------------------------------------------ icons

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
        roundedRect(graphics, x, y, bar, size, 1, color);
        roundedRect(graphics, x + size - bar, y, bar, size, 1, color);
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

    public static void closeIcon(GuiGraphics graphics, int x, int y, int size, int color) {
        for (int i = 0; i < size; i++) {
            graphics.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            graphics.fill(x + size - i - 1, y + i, x + size - i, y + i + 1, color);
        }
    }

    /** Small equaliser animation shown while a track is playing. */
    public static void equalizer(GuiGraphics graphics, int x, int y, int width, int height, int color, boolean animate) {
        verticalBars(graphics, x, y, width, height, Math.max(3, width / 4), color, animate, 0L);
    }
}
