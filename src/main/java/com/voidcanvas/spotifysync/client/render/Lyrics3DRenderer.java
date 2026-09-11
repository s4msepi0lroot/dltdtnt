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
 * Immersive 3D lyrics: the lines of the currently playing track orbit the
 * player in world space, billboarded towards the camera, slowly rotating and
 * bobbing. The active line is white and slightly larger; neighbouring lines
 * fade towards {@code #666666}.
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
        long position = state.interpolatedProgressMs() + config.lyricsOffsetMs;
        int active = lyrics.synced()
                ? lyrics.activeIndex(position)
                : (int) Math.min(lines.size() - 1,
                        (long) (lines.size() * Math.max(0f, Math.min(1f, state.progressFraction()))));
        if (active < 0) {
            active = 0;
        }

        int count = Math.max(1, config.ringLineCount);
        // Window of lines kept around the player, centred on the active one.
        int half = count / 2;
        int start = Math.max(0, active - half);
        int end = Math.min(lines.size(), start + count);
        start = Math.max(0, end - count);

        Font font = minecraft.font;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Vec3 cameraPos = camera.getPosition();
        Vec3 anchor = minecraft.player.position();

        double seconds = System.nanoTime() / 1_000_000_000.0;
        float spin = config.ringSpin ? (float) (seconds * 12.0 * config.ringSpinSpeed) : 0f;

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
            double x = anchor.x + Math.cos(angle) * config.ringRadius;
            double z = anchor.z + Math.sin(angle) * config.ringRadius;
            double bob = config.ringBob ? Math.sin(seconds * 1.4 + slot) * 0.12 : 0.0;
            double y = anchor.y + config.ringHeight + bob
                    + (current ? 0.25 : 0.0)
                    + (slot - slots / 2.0) * 0.06;

            float distanceFade = current ? 1f : 0.45f;
            float alpha = config.ringOpacity * distanceFade;
            int color = current
                    ? UiTheme.withAlpha(UiTheme.TEXT_PRIMARY, alpha)
                    : UiTheme.withAlpha(UiTheme.TEXT_SECONDARY, alpha);
            int backgroundColor = current
                    ? UiTheme.withAlpha(UiTheme.BACKGROUND, 0.45f * alpha)
                    : UiTheme.withAlpha(UiTheme.BACKGROUND, 0.25f * alpha);

            float baseScale = 0.035f * config.ringScale * (current ? 1.25f : 1f);

            poseStack.pushPose();
            poseStack.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
            // Billboard towards the camera.
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-camera.getYRot()));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(camera.getXRot()));
            poseStack.scale(-baseScale, -baseScale, baseScale);

            Matrix4f matrix = poseStack.last().pose();
            float textWidth = font.width(text);
            font.drawInBatch(text, -textWidth / 2f, 0f, color, false, matrix, buffers,
                    config.ringSeeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                    backgroundColor, 0xF000F0);

            if (current && lyrics.synced()) {
                // Accent karaoke rule under the active line.
                float progress = lyrics.lineProgress(active, position, state.durationMs());
                int ruleColor = UiTheme.accent(alpha);
                float ruleWidth = textWidth * progress;
                if (ruleWidth > 0.5f) {
                    font.drawInBatch("\u2588".repeat(Math.max(1, (int) (ruleWidth / 6f))),
                            -textWidth / 2f, 11f, ruleColor, false, matrix, buffers,
                            config.ringSeeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                            0, 0xF000F0);
                }
            }

            poseStack.popPose();
        }

        buffers.endBatch();
    }
}
