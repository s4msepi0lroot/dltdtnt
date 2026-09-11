package com.voidcanvas.spotifysync.client.screen;

import com.voidcanvas.spotifysync.client.SpotifyManager;
import com.voidcanvas.spotifysync.client.render.UiRender;
import com.voidcanvas.spotifysync.client.render.UiTheme;
import com.voidcanvas.spotifysync.client.screen.widget.VoidButton;
import com.voidcanvas.spotifysync.client.screen.widget.VoidIconButton;
import com.voidcanvas.spotifysync.client.screen.widget.VoidSlider;
import com.voidcanvas.spotifysync.config.SyncConfig;
import com.voidcanvas.spotifysync.spotify.PlaybackState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The expanded player: a floating obsidian shell with a bento layout.
 *
 * <p>Left column holds the album art card, the right column holds the track
 * metadata, the seek bar, the transport controls and the volume. Every element
 * lives in a fixed row so nothing can overlap, and the whole shell is scaled
 * down automatically on small GUI scales instead of clipping.</p>
 */
public final class ExpandedPlayerScreen extends Screen {

    private static final int SHELL_WIDTH = 470;
    private static final int SHELL_HEIGHT = 320;
    private static final int PADDING = 22;
    private static final int COVER = 160;
    private static final int GAP = 18;

    private float shellScale = 1f;
    private int shellX;
    private int shellY;

    private VoidIconButton playButton;
    private VoidSlider volumeSlider;
    private VoidIconButton shuffleButton;
    private VoidIconButton repeatButton;

    public ExpandedPlayerScreen() {
        super(Component.translatable("spotifysync.screen.player"));
    }

    // --------------------------------------------------------------- geometry

    private void computeShell() {
        float scaleX = (float) (width - 20) / SHELL_WIDTH;
        float scaleY = (float) (height - 20) / SHELL_HEIGHT;
        shellScale = Math.max(0.55f, Math.min(1f, Math.min(scaleX, scaleY)));
        shellX = (int) ((width - SHELL_WIDTH * shellScale) / 2f);
        shellY = (int) ((height - SHELL_HEIGHT * shellScale) / 2f);
    }

    /** Converts a shell-space coordinate to a real screen coordinate. */
    private int sx(int localX) {
        return shellX + (int) (localX * shellScale);
    }

    private int sy(int localY) {
        return shellY + (int) (localY * shellScale);
    }

    private int sw(int localWidth) {
        return Math.max(1, (int) (localWidth * shellScale));
    }

    private double toLocalX(double mouseX) {
        return (mouseX - shellX) / shellScale;
    }

    private double toLocalY(double mouseY) {
        return (mouseY - shellY) / shellScale;
    }

    // ----------------------------------------------------------------- layout

    @Override
    protected void init() {
        computeShell();
        SpotifyManager manager = SpotifyManager.get();

        int rightX = PADDING + COVER + GAP;
        int rightWidth = SHELL_WIDTH - rightX - PADDING;

        // Transport row.
        int transportY = 196;
        int big = 40;
        int small = 30;
        int centerX = rightX + rightWidth / 2;

        addRenderableWidget(new VoidIconButton(sx(centerX - big / 2 - GAP - small), sy(transportY + (big - small) / 2),
                sw(small), VoidIconButton.Icon.PREVIOUS,
                Component.translatable("spotifysync.action.previous"), manager::previous));

        playButton = new VoidIconButton(sx(centerX - big / 2), sy(transportY), sw(big),
                manager.state().playing() ? VoidIconButton.Icon.PAUSE : VoidIconButton.Icon.PLAY,
                Component.translatable("spotifysync.action.play_pause"), manager::togglePlayPause);
        playButton.filled();
        addRenderableWidget(playButton);

        addRenderableWidget(new VoidIconButton(sx(centerX + big / 2 + GAP), sy(transportY + (big - small) / 2),
                sw(small), VoidIconButton.Icon.NEXT,
                Component.translatable("spotifysync.action.next"), manager::next));

        shuffleButton = new VoidIconButton(sx(rightX), sy(transportY + (big - small) / 2), sw(small),
                VoidIconButton.Icon.SHUFFLE, Component.translatable("spotifysync.action.shuffle"),
                manager::toggleShuffle);
        addRenderableWidget(shuffleButton);

        repeatButton = new VoidIconButton(sx(rightX + rightWidth - small), sy(transportY + (big - small) / 2),
                sw(small), VoidIconButton.Icon.REPEAT, Component.translatable("spotifysync.action.repeat"),
                manager::cycleRepeat);
        addRenderableWidget(repeatButton);

        // Volume row.
        volumeSlider = new VoidSlider(sx(rightX), sy(246), sw(rightWidth),
                Component.translatable("spotifysync.action.volume"), 0, 100, 1,
                () -> {
                    int percent = SpotifyManager.get().state().volumePercent();
                    return percent < 0 ? 100 : percent;
                },
                value -> SpotifyManager.get().setVolume((int) Math.round(value)),
                value -> Math.round(value) + "%");
        addRenderableWidget(volumeSlider);

        // Footer actions.
        int footerY = SHELL_HEIGHT - PADDING - 26;
        int buttonWidth = (SHELL_WIDTH - PADDING * 2 - GAP) / 2;
        addRenderableWidget(new VoidButton(sx(PADDING), sy(footerY), sw(buttonWidth), sw(26),
                Component.translatable("spotifysync.action.open_spotify"),
                VoidButton.Style.PRIMARY, manager::openInSpotify));
        addRenderableWidget(new VoidButton(sx(PADDING + buttonWidth + GAP), sy(footerY), sw(buttonWidth), sw(26),
                Component.translatable("spotifysync.action.settings"),
                VoidButton.Style.SECONDARY,
                () -> minecraft.setScreen(new SpotifySettingsScreen())));
    }

    @Override
    public void tick() {
        SpotifyManager manager = SpotifyManager.get();
        PlaybackState state = manager.state();
        if (playButton != null) {
            playButton.setIcon(state.playing() ? VoidIconButton.Icon.PAUSE : VoidIconButton.Icon.PLAY);
        }
        if (shuffleButton != null) {
            shuffleButton.setHighlighted(state.shuffle());
            shuffleButton.active = manager.supportsExtendedControls();
        }
        if (repeatButton != null) {
            String repeat = state.repeatState();
            repeatButton.setIcon("track".equals(repeat)
                    ? VoidIconButton.Icon.REPEAT_ONE : VoidIconButton.Icon.REPEAT);
            repeatButton.setHighlighted(!"off".equals(repeat));
            repeatButton.active = manager.supportsExtendedControls();
        }
    }

    // ----------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        computeShell();
        SyncConfig config = SyncConfig.get();
        SpotifyManager manager = SpotifyManager.get();
        PlaybackState state = manager.state();

        // Black viewport behind the floating shell.
        graphics.fill(0, 0, width, height, UiTheme.scrim(0.72f));

        graphics.pose().pushPose();
        graphics.pose().translate(shellX, shellY, 0f);
        graphics.pose().scale(shellScale, shellScale, 1f);

        int tint = manager.covers().hasCover()
                ? UiTheme.tintFromCover(manager.covers().averageColor())
                : UiTheme.colors().accent;

        UiRender.shell(graphics, 0, 0, SHELL_WIDTH, SHELL_HEIGHT, 1f);
        UiRender.glowSphere(graphics, SHELL_WIDTH - 60, 40, 150, tint, 0.5f);
        UiRender.glowSphere(graphics, 40, SHELL_HEIGHT - 40, 130, UiTheme.colors().accentSecondary, 0.28f);

        int rightX = PADDING + COVER + GAP;
        int rightWidth = SHELL_WIDTH - rightX - PADDING;

        // Header row: mono status tag + close hint.
        UiRender.statusTag(graphics, manager.sourceStatus(), PADDING, PADDING - 6,
                manager.connected() ? UiTheme.colors().accent : UiTheme.colors().danger, 1f);
        UiRender.monoRight(graphics, "ESC", SHELL_WIDTH - PADDING, PADDING - 6, 0.8f, UiTheme.textMuted(1f));

        // Album art card.
        int coverY = PADDING + 16;
        UiRender.card(graphics, PADDING - 6, coverY - 6, COVER + 12, COVER + 12, 1f);
        ResourceLocation texture = manager.covers().texture();
        if (texture != null) {
            UiRender.cover(graphics, texture, PADDING, coverY, COVER, 1f);
        } else {
            UiRender.coverPlaceholder(graphics, PADDING, coverY, COVER, 1f);
        }

        // Track metadata rows.
        String title = state.hasTrack() ? state.title() : I18nFallback.noTrack();
        UiRender.display(graphics, UiRender.ellipsize(title, (int) (rightWidth / 1.5f)),
                rightX, PADDING + 18, 1.5f, UiTheme.textPrimary(1f));
        UiRender.text(graphics, UiRender.ellipsize(state.hasTrack() ? state.artistLine() : "", rightWidth),
                rightX, PADDING + 44, UiTheme.textSecondary(1f), false);
        UiRender.mono(graphics, UiRender.ellipsize(state.album(), (int) (rightWidth / 0.8f)),
                rightX, PADDING + 60, 0.8f, UiTheme.textMuted(1f));

        // Glass info tiles (bento row).
        int tileY = PADDING + 82;
        int tileWidth = (rightWidth - GAP) / 2;
        drawTile(graphics, rightX, tileY, tileWidth, 40, "DEVICE",
                UiRender.ellipsize(orDash(state.deviceName()), (int) (tileWidth / 0.9f)));
        drawTile(graphics, rightX + tileWidth + GAP, tileY, tileWidth, 40, "SOURCE",
                manager.usingLocalSource() ? "LOCAL SESSION" : "WEB API");

        // Seek bar row.
        int barY = 158;
        UiRender.progressBar(graphics, rightX, barY, rightWidth, 6, state.progressFraction(), 1f, true);
        UiRender.mono(graphics, PlaybackState.formatTime(state.interpolatedProgressMs()),
                rightX, barY + 10, 0.8f, UiTheme.textSecondary(1f));
        UiRender.monoRight(graphics, PlaybackState.formatTime(state.durationMs()),
                rightX + rightWidth, barY + 10, 0.8f, UiTheme.textSecondary(1f));

        UiRender.hairline(graphics, PADDING, SHELL_HEIGHT - PADDING - 40, SHELL_WIDTH - PADDING * 2, 0.14f);

        if (state.playing()) {
            UiRender.equalizer(graphics, PADDING, coverY + COVER + 10, 60, 12,
                    UiTheme.accent(0.8f), true);
        }

        graphics.pose().popPose();

        // Widgets are positioned in screen space already.
        for (var renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Custom background is drawn in render().
    }

    /** Glass metadata tile: mono caption above a value line. */
    private static void drawTile(GuiGraphics graphics, int x, int y, int width, int height,
                                 String caption, String value) {
        UiRender.glassTile(graphics, x, y, width, height, 1f);
        UiRender.mono(graphics, caption, x + 10, y + 9, 0.75f, UiTheme.textMuted(1f));
        UiRender.text(graphics, UiRender.ellipsize(value, width - 20), x + 10, y + 22,
                UiTheme.textPrimary(1f), false);
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "\u2014" : value;
    }

    // ------------------------------------------------------------- interaction

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double localX = toLocalX(mouseX);
            double localY = toLocalY(mouseY);
            int rightX = PADDING + COVER + GAP;
            int rightWidth = SHELL_WIDTH - rightX - PADDING;
            int barY = 158;
            if (localY >= barY - 6 && localY <= barY + 12 && localX >= rightX && localX <= rightX + rightWidth) {
                float fraction = (float) ((localX - rightX) / rightWidth);
                SpotifyManager.get().seekFraction(fraction);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Tiny helper so the empty state still reads nicely. */
    private static final class I18nFallback {
        private I18nFallback() {
        }

        static String noTrack() {
            return Component.translatable("spotifysync.state.no_track").getString();
        }
    }
}
