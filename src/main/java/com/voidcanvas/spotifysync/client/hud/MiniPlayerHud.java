package com.voidcanvas.spotifysync.client.hud;

import com.voidcanvas.spotifysync.client.SpotifyManager;
import com.voidcanvas.spotifysync.client.render.UiRender;
import com.voidcanvas.spotifysync.client.render.UiTheme;
import com.voidcanvas.spotifysync.config.HudAnchor;
import com.voidcanvas.spotifysync.config.SyncConfig;
import com.voidcanvas.spotifysync.spotify.PlaybackState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

/**
 * The always-on mini player, rebuilt for the Obsidian and Lime system.
 *
 * <p>Anatomy: a floating glass card with a lime glow sphere behind the album
 * art, a mono status tag, the track title in tight display type, the artist in
 * secondary white, a hairline, a lime progress bar and a mono timecode. Every
 * band has its own fixed row so nothing can overlap, and the card grows when
 * the cover is hidden instead of squeezing the text.</p>
 */
public final class MiniPlayerHud implements LayeredDraw.Layer {

    public static final MiniPlayerHud INSTANCE = new MiniPlayerHud();

    /** Layout constants (GUI pixels). */
    private static final int WIDTH = 216;
    private static final int HEIGHT = 70;
    private static final int PADDING = 10;
    private static final int COVER = 50;

    private float appearAnimation;
    private float hoverAnimation;
    private int lastX;
    private int lastY;
    private int lastWidth = WIDTH;
    private int lastHeight = HEIGHT;
    private boolean lastVisible;

    private MiniPlayerHud() {
    }

    // --------------------------------------------------------------- geometry

    public static int width() {
        return WIDTH;
    }

    public static int height() {
        return HEIGHT;
    }

    /** True when the last rendered frame placed the widget under the cursor. */
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!lastVisible) {
            return false;
        }
        return mouseX >= lastX && mouseX <= lastX + lastWidth
                && mouseY >= lastY && mouseY <= lastY + lastHeight;
    }

    public boolean visible() {
        return lastVisible;
    }

    // ----------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        renderInternal(graphics, -1, -1, false);
    }

    /** Used from the screen overlay hook, where a real cursor exists. */
    public void renderWithCursor(GuiGraphics graphics, int mouseX, int mouseY) {
        renderInternal(graphics, mouseX, mouseY, true);
    }

    private void renderInternal(GuiGraphics graphics, int mouseX, int mouseY, boolean interactive) {
        SyncConfig config = SyncConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (!config.hudEnabled || minecraft.player == null || minecraft.options.hideGui) {
            lastVisible = false;
            return;
        }

        SpotifyManager manager = SpotifyManager.get();
        PlaybackState state = manager.state();
        boolean hasTrack = state.hasTrack();
        if (!hasTrack && config.hideWhenIdle) {
            appearAnimation = UiRender.ease(appearAnimation, 0f, 0.15f);
            if (appearAnimation < 0.02f) {
                lastVisible = false;
                return;
            }
        } else {
            appearAnimation = UiRender.ease(appearAnimation, 1f, 0.15f);
        }

        boolean showCover = config.showCover;
        int width = WIDTH;
        int height = HEIGHT;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float scale = config.hudScale;

        int scaledWidth = (int) (width * scale);
        int scaledHeight = (int) (height * scale);
        int x = anchorX(config.hudAnchor, screenWidth, scaledWidth) + config.hudOffsetX;
        int y = anchorY(config.hudAnchor, screenHeight, scaledHeight) + config.hudOffsetY;
        // Keep the card fully on screen at any scale or offset.
        x = Math.max(2, Math.min(screenWidth - scaledWidth - 2, x));
        y = Math.max(2, Math.min(screenHeight - scaledHeight - 2, y));

        lastX = x;
        lastY = y;
        lastWidth = scaledWidth;
        lastHeight = scaledHeight;
        lastVisible = true;

        boolean hovered = interactive && isMouseOver(mouseX, mouseY);
        hoverAnimation = UiRender.ease(hoverAnimation, hovered ? 1f : 0f, 0.2f);

        float alpha = config.hudOpacity * Math.min(1f, appearAnimation);

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0f);
        graphics.pose().scale(scale, scale, 1f);
        // Floating card animation from the design system (6s ease-in-out).
        float floatOffset = (float) Math.sin(System.nanoTime() / 1_000_000_000.0 * (Math.PI * 2 / 6.0)) * 1.6f;
        graphics.pose().translate(0f, floatOffset, 0f);

        drawCard(graphics, manager, state, config, width, height, showCover, alpha, hovered);

        graphics.pose().popPose();
    }

    private void drawCard(GuiGraphics graphics, SpotifyManager manager, PlaybackState state, SyncConfig config,
                          int width, int height, boolean showCover, float alpha, boolean hovered) {
        int tint = manager.covers().hasCover()
                ? UiTheme.tintFromCover(manager.covers().averageColor())
                : UiTheme.colors().accent;

        UiRender.glowSphere(graphics, width / 4, height / 2, 46, tint, alpha * (0.6f + 0.4f * hoverAnimation));
        UiRender.glass(graphics, 0, 0, width, height, alpha);
        if (hoverAnimation > 0.01f) {
            UiRender.roundedBorder(graphics, 0, 0, width, height, UiTheme.radiusCard(),
                    UiTheme.accent(0.4f * hoverAnimation * alpha));
        }

        int contentX = PADDING;
        if (showCover) {
            int coverY = (height - COVER) / 2;
            ResourceLocation texture = manager.covers().texture();
            if (texture != null) {
                UiRender.image(graphics, texture, PADDING, coverY, COVER, COVER, alpha);
                UiRender.roundedBorder(graphics, PADDING, coverY, COVER, COVER,
                        UiTheme.radiusControl(), UiTheme.ring(alpha));
            } else {
                UiRender.coverPlaceholder(graphics, PADDING, coverY, COVER, alpha);
            }
            contentX = PADDING + COVER + 10;
        }

        int textWidth = width - contentX - PADDING;

        // Row 1: mono status tag.
        String status = state.hasTrack()
                ? (state.playing() ? "NOW PLAYING" : "PAUSED")
                : manager.connected() ? "IDLE" : "OFFLINE";
        UiRender.statusTag(graphics, status, contentX, PADDING - 2,
                state.playing() ? UiTheme.colors().accent : UiTheme.colors().textMuted, alpha);

        if (state.playing()) {
            UiRender.equalizer(graphics, contentX + textWidth - 14, PADDING - 3, 12, 8,
                    UiTheme.accent(0.85f * alpha), true);
        }

        // Row 2: title in display type.
        String title = state.hasTrack() ? state.title() : "Spotify Sync";
        UiRender.display(graphics, UiRender.ellipsize(title, (int) (textWidth / 0.95f)),
                contentX, PADDING + 11, 0.95f, UiTheme.textPrimary(alpha));

        // Row 3: artist.
        String artist = state.hasTrack() ? state.artistLine() : manager.sourceStatus();
        UiRender.text(graphics, UiRender.ellipsize(artist, textWidth), contentX, PADDING + 23,
                UiTheme.textSecondary(alpha), false);

        // Row 4: progress + timecode.
        int barY = height - PADDING - 9;
        if (config.showProgressBar) {
            UiRender.progressBar(graphics, contentX, barY, textWidth, 3,
                    state.progressFraction(), alpha, false);
        }
        if (config.showTimecode && state.hasTrack()) {
            String elapsed = PlaybackState.formatTime(state.interpolatedProgressMs());
            String total = PlaybackState.formatTime(state.durationMs());
            UiRender.mono(graphics, elapsed, contentX, barY + 5, 0.65f, UiTheme.textMuted(alpha));
            UiRender.monoRight(graphics, total, contentX + textWidth, barY + 5, 0.65f, UiTheme.textMuted(alpha));
        }
    }

    private static int anchorX(HudAnchor anchor, int screenWidth, int width) {
        return switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> 8;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - width - 8;
            default -> (screenWidth - width) / 2;
        };
    }

    private static int anchorY(HudAnchor anchor, int screenHeight, int height) {
        return switch (anchor) {
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - height - 8;
            default -> 8;
        };
    }
}
