package com.voidcanvas.spotifysync.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voidcanvas.spotifysync.client.SpotifyManager;
import com.voidcanvas.spotifysync.config.SyncConfig;
import com.voidcanvas.spotifysync.lyrics.LyricLine;
import com.voidcanvas.spotifysync.lyrics.TrackLyrics;
import com.voidcanvas.spotifysync.spotify.PlaybackState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Immersive 3D lyrics, rebuilt with a custom look instead of plain vanilla
 * nameplates.
 *
 * <p>Each line floats on its own obsidian glass plate that orbits the player,
 * billboarded to the camera. The active line is lifted forward, scaled up, gets
 * a layered lime glow, a karaoke wipe that re-draws the already sung part in
 * the accent colour, mono index brackets on the sides and a bar equaliser
 * below. Neighbouring lines shrink, dim and sink so the ring reads as depth
 * rather than a flat text circle.</p>
 */
public final class Lyrics3DRenderer {

    private Lyrics3DRenderer() {
    }

    public static void render(PoseStack poseStack, Camera camera) {
        SyncConfig config = SyncConfig.get();
        if (!config.lyricsEnabled || !config.lyricsMode.shows3d()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        SpotifyManager manager = SpotifyManager.get();
        PlaybackState state = manager.state();
        if (!state.hasTrack()) {
            return;
        }
        TrackLyrics lyrics = manager.lyrics().lyrics();
        if (lyrics.isEmpty()) {
            return;
        }

        List<LyricLine> lines = lyrics.lines();
        long position = state.interpolatedProgressMs() + config.lyricsOffsetMs + config.lyricsLeadMs;
        int active = lyrics.synced()
                ? lyrics.activeIndex(position)
                : (int) Math.min(lines.size() - 1,
                        (long) (lines.size() * Math.max(0f, Math.min(1f, state.progressFraction()))));
        if (active < 0) {
            active = 0;
        }

        int count = Math.max(1, config.ringLineCount);
        int half = count / 2;
        int start = Math.max(0, active - half);
        int end = Math.min(lines.size(), start + count);
        start = Math.max(0, end - count);

        Font font = minecraft.font;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Font.DisplayMode displayMode = config.ringSeeThrough
                ? Font.DisplayMode.SEE_THROUGH
                : Font.DisplayMode.NORMAL;
        Vec3 cameraPos = camera.getPosition();
        Vec3 anchor = minecraft.player.position();

        double seconds = System.nanoTime() / 1_000_000_000.0;
        float spin = config.ringSpin ? (float) (seconds * 12.0 * config.ringSpinSpeed) : 0f;
        float lineProgress = lyrics.synced()
                ? lyrics.lineProgress(active, position, state.durationMs())
                : 0f;

        for (int index = start; index < end; index++) {
            LyricLine line = lines.get(index);
            String text = line.text();
            if (text == null || text.isBlank()) {
                continue;
            }
            int slot = index - start;
            int slots = Math.max(1, end - start);
            boolean current = index == active;

            float angle = (float) Math.toRadians(spin + slot * (360f / slots));
            double radius = config.ringRadius * (current ? 0.86 : 1.0);
            double x = anchor.x + Math.cos(angle) * radius;
            double z = anchor.z + Math.sin(angle) * radius;
            // ease-in-out float animation of the design system
            double bob = config.ringBob ? Math.sin(seconds * 1.05 + slot * 0.8) * 0.16 : 0.0;
            double y = anchor.y + config.ringHeight + bob
                    + (current ? 0.34 : 0.0)
                    + (slot - slots / 2.0) * 0.05;

            float fade = current ? 1f : 0.42f;
            float alpha = config.ringOpacity * fade;
            int textColor = current ? UiTheme.textPrimary(alpha) : UiTheme.textSecondary(alpha);
            float baseScale = 0.035f * config.ringScale * (current ? 1.3f : 0.95f);

            poseStack.pushPose();
            poseStack.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-camera.getYRot()));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(camera.getXRot()));
            poseStack.scale(-baseScale, -baseScale, baseScale);

            Matrix4f matrix = poseStack.last().pose();
            float textWidth = font.width(text);
            float left = -textWidth / 2f;

            // Obsidian glass plate: a wide translucent band behind the line.
            int plateColor = UiTheme.argb(UiTheme.colors().shell, (current ? 0.62f : 0.4f) * alpha);
            drawPlate(font, buffers, matrix, displayMode, left - 6f, textWidth + 12f, plateColor);

            if (current) {
                // Layered lime glow behind the active line.
                int glow = UiTheme.accent(0.22f * alpha);
                font.drawInBatch(text, left - 0.6f, -0.6f, glow, false, matrix, buffers, displayMode, 0, 0xF000F0);
                font.drawInBatch(text, left + 0.6f, 0.6f, glow, false, matrix, buffers, displayMode, 0, 0xF000F0);
                // Hairline accent frame
                drawPlate(font, buffers, matrix, displayMode, left - 6f, textWidth + 12f,
                        UiTheme.accent(0.10f * alpha));
            }

            font.drawInBatch(text, left, 0f, textColor, false, matrix, buffers, displayMode, 0, 0xF000F0);

            if (current && lyrics.synced()) {
                // Karaoke wipe: redraw the already sung prefix in lime.
                int chars = (int) Math.floor(text.length() * Math.max(0f, Math.min(1f, lineProgress)));
                if (chars > 0) {
                    String sung = text.substring(0, Math.min(text.length(), chars));
                    font.drawInBatch(sung, left, 0f, UiTheme.accent(alpha), false, matrix, buffers,
                            displayMode, 0, 0xF000F0);
                }
                // Progress rule + bar equaliser below the plate.
                int blocks = Math.max(1, (int) ((textWidth * lineProgress) / 6f));
                font.drawInBatch("\u2588".repeat(blocks), left, 12f, UiTheme.accent(0.85f * alpha), false,
                        matrix, buffers, displayMode, 0, 0xF000F0);
                drawEqualizer(font, buffers, matrix, displayMode, left, 22f, alpha, seconds);

                // Mono index brackets on both sides.
                String tag = String.format("%02d", index + 1);
                font.drawInBatch("[" + tag + "]", left - font.width("[" + tag + "] ") - 4f, 0f,
                        UiTheme.accentSecondary(0.8f * alpha), false, matrix, buffers, displayMode, 0, 0xF000F0);
            }

            poseStack.popPose();
        }

        buffers.endBatch();
    }

    /** Thin full-width band drawn with block glyphs, used as a glass plate. */
    private static void drawPlate(Font font, MultiBufferSource buffers, Matrix4f matrix,
                                  Font.DisplayMode mode, float x, float width, int color) {
        if ((color >>> 24) == 0 || width <= 0f) {
            return;
        }
        int blocks = Math.max(1, (int) (width / 6f));
        String band = "\u2588".repeat(blocks);
        for (float offset = -3f; offset <= 9f; offset += 3f) {
            font.drawInBatch(band, x, offset, color, false, matrix, buffers, mode, 0, 0xF000F0);
        }
    }

    /** Animated bar equaliser under the active line. */
    private static void drawEqualizer(Font font, MultiBufferSource buffers, Matrix4f matrix,
                                      Font.DisplayMode mode, float x, float y, float alpha, double seconds) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            double wave = Math.sin(seconds * 4.0 + i * 0.7) * 0.5 + 0.5;
            builder.append(wave > 0.66 ? '\u2588' : wave > 0.33 ? '\u2584' : '\u2581');
        }
        font.drawInBatch(builder.toString(), x, y, UiTheme.accentSecondary(0.55f * alpha), false,
                matrix, buffers, mode, 0, 0xF000F0);
    }
}
