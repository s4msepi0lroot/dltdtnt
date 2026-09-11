package com.voidcanvas.spotifysync.client.hud;

import com.voidcanvas.spotifysync.client.SpotifyManager;
import com.voidcanvas.spotifysync.client.render.UiRender;
import com.voidcanvas.spotifysync.client.render.UiTheme;
import com.voidcanvas.spotifysync.config.SyncConfig;
import com.voidcanvas.spotifysync.lyrics.LyricLine;
import com.voidcanvas.spotifysync.lyrics.TrackLyrics;
import com.voidcanvas.spotifysync.spotify.PlaybackState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

import java.util.List;

/**
 * Flat 2D lyrics band of the Obsidian and Lime system.
 *
 * <p>The active line sits on a glass plate with a lime karaoke wipe underneath,
 * context lines fade out above and below. Timing uses the configured lead so
 * the highlighted line no longer trails behind the music.</p>
 */
public final class LyricsHud implements LayeredDraw.Layer {

    public static final LyricsHud INSTANCE = new LyricsHud();

    private int renderedIndex = -1;
    private float lineAnimation;

    private LyricsHud() {
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        renderBand(graphics);
    }

    public void renderBand(GuiGraphics graphics) {
        SyncConfig config = SyncConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (!config.lyricsEnabled || !config.lyricsMode.showsHud()
                || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        SpotifyManager manager = SpotifyManager.get();
        PlaybackState state = manager.state();
        if (!state.hasTrack()) {
            return;
        }
        TrackLyrics lyrics = manager.lyrics().lyrics();

        float scale = config.lyricsScale;
        int screenWidth = (int) (graphics.guiWidth() / scale);
        int screenHeight = (int) (graphics.guiHeight() / scale);
        int centerX = screenWidth / 2;
        int baseY = (int) (screenHeight * config.lyricsHudY);

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1f);

        if (lyrics.isEmpty()) {
            if (manager.lyrics().loading()) {
                drawStatus(graphics, centerX, baseY, "searching lyrics" + dots());
            } else if (manager.lyrics().failedForCurrentTrack(state.trackId())) {
                drawStatus(graphics, centerX, baseY, "no lyrics found");
            }
            graphics.pose().popPose();
            return;
        }

        long position = state.interpolatedProgressMs() + config.lyricsOffsetMs + config.lyricsLeadMs;
        List<LyricLine> lines = lyrics.lines();

        if (!lyrics.synced()) {
            int index = (int) Math.min(lines.size() - 1,
                    (long) (lines.size() * Math.max(0f, Math.min(1f, state.progressFraction()))));
            drawLines(graphics, centerX, baseY, lines, index, 0f, false);
            graphics.pose().popPose();
            return;
        }

        int active = lyrics.activeIndex(position);
        if (active != renderedIndex) {
            renderedIndex = active;
            lineAnimation = 0f;
        }
        lineAnimation = Math.min(1f, lineAnimation + 0.12f);
        float lineProgress = lyrics.lineProgress(active, position, state.durationMs());
        drawLines(graphics, centerX, baseY, lines, active, lineProgress, true);

        graphics.pose().popPose();
    }

    private void drawLines(GuiGraphics graphics, int centerX, int baseY,
                           List<LyricLine> lines, int active, float lineProgress, boolean synced) {
        SyncConfig config = SyncConfig.get();
        int context = config.lyricsContextLines;

        if (active >= 0 && active < lines.size()) {
            String text = lines.get(active).text();
            if (text != null && !text.isBlank()) {
                int plateWidth = (int) (UiRender.displayWidth(text, 1.15f)) + 28;
                int plateHeight = 24;
                UiRender.glass(graphics, centerX - plateWidth / 2, baseY - 7, plateWidth, plateHeight,
                        plateHeight / 2, 0.9f);
            }
        }

        for (int offset = -context; offset <= context; offset++) {
            int index = active + offset;
            if (index < 0 || index >= lines.size()) {
                continue;
            }
            String text = lines.get(index).text();
            if (text == null || text.isBlank()) {
                continue;
            }
            boolean current = offset == 0 && active >= 0;
            float distance = Math.abs(offset);
            float alpha = current ? 1f : Math.max(0.2f, 0.5f - distance * 0.15f);
            float textScale = current ? 1.15f : 0.9f;
            int color = current ? UiTheme.textPrimary(alpha) : UiTheme.textMuted(alpha);
            int y = baseY + offset * 16;
            if (current) {
                textScale *= 1f + 0.05f * (1f - lineAnimation);
                UiRender.display(graphics, text,
                        centerX - UiRender.displayWidth(text, textScale) / 2f, y, textScale, color);
            } else {
                UiRender.textScaledCentered(graphics, text, centerX, y, textScale, color, true);
            }
        }

        if (synced && active >= 0) {
            String text = lines.get(active).text();
            int width = Math.max(24, (int) UiRender.displayWidth(text, 1.15f));
            int ruleY = baseY + 12;
            UiRender.hairline(graphics, centerX - width / 2, ruleY, width, 0.3f);
            int filled = (int) (width * lineProgress);
            if (filled > 0) {
                UiRender.accentRule(graphics, centerX - width / 2, ruleY, filled, 0.95f);
            }
        }
    }

    private void drawStatus(GuiGraphics graphics, int centerX, int baseY, String message) {
        UiRender.monoCentered(graphics, message, centerX, baseY, 0.8f, UiTheme.textMuted(0.85f));
    }

    private static String dots() {
        int phase = (int) ((System.nanoTime() / 400_000_000L) % 4L);
        return ".".repeat(phase);
    }
}
