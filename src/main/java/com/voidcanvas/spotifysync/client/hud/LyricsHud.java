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
 * Flat 2D lyrics band: the active line in white, context lines faded to
 * {@code #666666}, plus a hairline karaoke rule that fills with the accent as
 * the line plays.
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

        long position = state.interpolatedProgressMs() + config.lyricsOffsetMs;
        List<LyricLine> lines = lyrics.lines();

        if (!lyrics.synced()) {
            // Unsynced lyrics: scroll softly through the whole text.
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
        lineAnimation = Math.min(1f, lineAnimation + 0.08f);
        float lineProgress = lyrics.lineProgress(active, position, state.durationMs());
        drawLines(graphics, centerX, baseY, lines, active, lineProgress, true);

        graphics.pose().popPose();
    }

    private void drawLines(GuiGraphics graphics, int centerX, int baseY,
                           List<LyricLine> lines, int active, float lineProgress, boolean synced) {
        SyncConfig config = SyncConfig.get();
        int context = config.lyricsContextLines;

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
            float alpha = current ? 1f : Math.max(0.18f, 0.55f - distance * 0.16f);
            float textScale = current ? 1.1f : 0.9f;
            int color = current
                    ? UiTheme.withAlpha(UiTheme.TEXT_PRIMARY, alpha)
                    : UiTheme.withAlpha(UiTheme.TEXT_MUTED, alpha);
            int y = baseY + offset * 13;
            if (current) {
                float pop = 1f + 0.04f * (1f - lineAnimation);
                textScale *= pop;
            }
            UiRender.textScaledCentered(graphics, text, centerX, y, textScale, color, true);
        }

        if (synced && active >= 0) {
            String text = lines.get(active).text();
            int width = Math.max(24, (int) (UiRender.font().width(text) * 1.1f));
            int ruleY = baseY + 14;
            UiRender.hairline(graphics, centerX - width / 2, ruleY, width, 0.35f);
            int filled = (int) (width * lineProgress);
            if (filled > 0) {
                UiRender.accentRule(graphics, centerX - width / 2, ruleY, filled, 0.9f);
            }
        }
    }

    private void drawStatus(GuiGraphics graphics, int centerX, int baseY, String message) {
        UiRender.textScaledCentered(graphics, message, centerX, baseY, 0.8f,
                UiTheme.withAlpha(UiTheme.TEXT_MUTED, 0.8f), true);
    }

    private static String dots() {
        int count = (int) ((System.nanoTime() / 400_000_000L) % 4L);
        return ".".repeat(count);
    }
}
