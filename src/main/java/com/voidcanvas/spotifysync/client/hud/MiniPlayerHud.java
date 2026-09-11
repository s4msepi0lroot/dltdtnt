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
 * The always-on mini player.
 *
 * <p>Anatomy (void-canvas card): square album art on the left, title in white,
 * artist in {@code #999999}, a hairline accent rule, a thin progress bar and a
 * mono timecode. Rendered both as a HUD layer and, when a screen with a cursor
 * is open, on top of that screen where it becomes clickable.</p>
 */
public final class MiniPlayerHud implements LayeredDraw.Layer {

    public static final MiniPlayerHud INSTANCE = new MiniPlayerHud();

    /** Layout constants (GUI pixels). */
    private static final int WIDTH = 168;
    private static final int HEIGHT = 46;
    private static final int PADDING = 6;

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
        boolean connected = manager.connected();

        if (!connected || (config.hideWhenIdle && !hasTrack)) {
            appearAnimation += (0f - appearAnimation) * 0.2f;
            if (appearAnimation < 0.02f) {
                lastVisible = false;
                return;
            }
        } else {
            appearAnimation += (1f - appearAnimation) * 0.2f;
        }

        float scale = config.hudScale;
        int screenWidth = (int) (graphics.guiWidth() / scale);
        int screenHeight = (int) (graphics.guiHeight() / scale);
        int x = config.hudAnchor.resolveX(screenWidth, WIDTH, config.hudOffsetX);
        int y = config.hudAnchor.resolveY(screenHeight, HEIGHT, config.hudOffsetY);

        lastX = (int) (x * scale);
        lastY = (int) (y * scale);
        lastWidth = (int) (WIDTH * scale);
        lastHeight = (int) (HEIGHT * scale);
        lastVisible = true;

        boolean hovered = interactive && isMouseOver(mouseX, mouseY);
        hoverAnimation += ((hovered ? 1f : 0f) - hoverAnimation) * 0.22f;

        float alpha = config.hudOpacity * easeOut(appearAnimation);

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1f);

        drawCard(graphics, x, y, state, connected, alpha, hovered);

        graphics.pose().popPose();
    }

    private void drawCard(GuiGraphics graphics, int x, int y, PlaybackState state,
                          boolean connected, float alpha, boolean hovered) {
        SyncConfig config = SyncConfig.get();
        SpotifyManager manager = SpotifyManager.get();

        // Base surface: near-black card, hairline border, accent on hover.
        UiRender.roundedRect(graphics, x, y, WIDTH, HEIGHT, UiTheme.RADIUS_CARD,
                UiTheme.withAlpha(UiTheme.SURFACE, alpha * 0.94f));
        UiRender.accentWash(graphics, x, y, WIDTH / 2, HEIGHT, alpha * hoverAnimation);
        UiRender.roundedBorder(graphics, x, y, WIDTH, HEIGHT, UiTheme.RADIUS_CARD,
                UiTheme.lerpColor(UiTheme.withAlpha(UiTheme.BORDER, alpha),
                        UiTheme.accent(alpha * 0.75f), hoverAnimation));
        if (config.grain) {
            UiRender.grain(graphics, x + 1, y + 1, WIDTH - 2, HEIGHT - 2, alpha * 0.5f);
        }

        int contentX = x + PADDING;
        int coverSize = HEIGHT - PADDING * 2;

        // ---- album art -----------------------------------------------------
        if (config.showCover) {
            ResourceLocation cover = manager.covers().texture();
            if (cover != null && state.hasTrack()) {
                UiRender.image(graphics, cover, contentX, y + PADDING, coverSize, coverSize, alpha);
                UiRender.roundedBorder(graphics, contentX, y + PADDING, coverSize, coverSize,
                        UiTheme.RADIUS_CONTROL, UiTheme.withAlpha(UiTheme.BORDER, alpha * 0.8f));
            } else {
                UiRender.coverPlaceholder(graphics, contentX, y + PADDING, coverSize, alpha);
            }
            contentX += coverSize + PADDING;
        }

        int textWidth = x + WIDTH - PADDING - contentX;

        if (!connected) {
            UiRender.text(graphics, "SPOTIFY SYNC", contentX, y + PADDING + 2,
                    UiTheme.withAlpha(UiTheme.TEXT_PRIMARY, alpha), false);
            UiRender.accentRule(graphics, contentX, y + PADDING + 13, 24, alpha);
            UiRender.textScaled(graphics, UiRender.ellipsize("not connected", (int) (textWidth / 0.75f)),
                    contentX, y + PADDING + 18, 0.75f, UiTheme.withAlpha(UiTheme.TEXT_MUTED, alpha), false);
            return;
        }

        if (!state.hasTrack()) {
            UiRender.text(graphics, "Nothing playing", contentX, y + PADDING + 2,
                    UiTheme.withAlpha(UiTheme.TEXT_PRIMARY, alpha), false);
            UiRender.accentRule(graphics, contentX, y + PADDING + 13, 24, alpha);
            UiRender.textScaled(graphics, manager.everSynced() ? "start playback on any device" : "waiting for spotify\u2026",
                    contentX, y + PADDING + 18, 0.75f, UiTheme.withAlpha(UiTheme.TEXT_MUTED, alpha), false);
            return;
        }

        // ---- title (marquee when too long) ---------------------------------
        String title = state.title();
        int titleOffset = UiRender.marqueeOffset(title, textWidth, 18);
        graphics.enableScissor(contentX, y + PADDING, contentX + textWidth, y + PADDING + 12);
        UiRender.text(graphics, title, contentX - titleOffset, y + PADDING + 1,
                UiTheme.withAlpha(UiTheme.TEXT_PRIMARY, alpha), false);
        graphics.disableScissor();

        // ---- artist --------------------------------------------------------
        String artist = state.artistLine();
        UiRender.textScaled(graphics, UiRender.ellipsize(artist, (int) (textWidth / 0.85f)),
                contentX, y + PADDING + 13, 0.85f,
                UiTheme.withAlpha(UiTheme.TEXT_SECONDARY, alpha), false);

        // ---- equaliser + timecode -----------------------------------------
        int rowY = y + HEIGHT - PADDING - 10;
        UiRender.equalizer(graphics, contentX, rowY, 12, 7, UiTheme.accent(alpha), state.playing());

        long progress = state.interpolatedProgressMs();
        if (config.showTimecode) {
            String timecode = PlaybackState.formatTime(progress) + " / " + PlaybackState.formatTime(state.durationMs());
            UiRender.textScaled(graphics, timecode,
                    x + WIDTH - PADDING - UiRender.font().width(timecode) * 0.75f, rowY,
                    0.75f, UiTheme.withAlpha(UiTheme.TEXT_MUTED, alpha), false);
        }

        // ---- progress bar --------------------------------------------------
        if (config.showProgressBar) {
            int barY = y + HEIGHT - 4;
            UiRender.progressBar(graphics, x + PADDING, barY, WIDTH - PADDING * 2, 2,
                    state.progressFraction(), alpha, false);
        }

        // ---- hover affordance ---------------------------------------------
        if (hoverAnimation > 0.05f) {
            String hint = "open player";
            UiRender.textScaled(graphics, hint,
                    x + WIDTH - PADDING - UiRender.font().width(hint) * 0.7f, y + PADDING,
                    0.7f, UiTheme.accent(alpha * hoverAnimation), false);
        }

        if (manager.stale()) {
            UiRender.roundedRect(graphics, x + WIDTH - 9, y + 4, 4, 4, 2,
                    UiTheme.withAlpha(0xFFFF5A5A, alpha));
        }
    }

    private static float easeOut(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - clamped) * (1f - clamped);
    }
}
