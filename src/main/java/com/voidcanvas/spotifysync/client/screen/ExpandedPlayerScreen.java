package com.voidcanvas.spotifysync.client.screen;

import com.voidcanvas.spotifysync.client.SpotifyManager;
import com.voidcanvas.spotifysync.client.render.UiRender;
import com.voidcanvas.spotifysync.client.render.UiTheme;
import com.voidcanvas.spotifysync.client.screen.widget.VoidButton;
import com.voidcanvas.spotifysync.client.screen.widget.VoidIconButton;
import com.voidcanvas.spotifysync.client.screen.widget.VoidSlider;
import com.voidcanvas.spotifysync.config.SyncConfig;
import com.voidcanvas.spotifysync.lyrics.LyricLine;
import com.voidcanvas.spotifysync.lyrics.TrackLyrics;
import com.voidcanvas.spotifysync.spotify.PlaybackState;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.net.URI;
import java.util.List;

/**
 * The expanded player.
 *
 * <p>Opened by clicking the mini player (or with the dedicated key). The layout
 * uses a strict grid so nothing collides: a 132px cover column on the left, a
 * text/controls column on the right, and fixed bands for the progress bar,
 * transport row, volume, metadata tiles and footer.</p>
 */
public final class ExpandedPlayerScreen extends Screen {

    private static final int CARD_WIDTH = 432;
    private static final int CARD_HEIGHT = 268;
    private static final int COVER = 132;
    private static final int PADDING = 18;
    private static final int BUTTON_HEIGHT = 20;

    /** Vertical bands, relative to the card top. */
    private static final int BAND_PROGRESS = 108;
    private static final int BAND_CONTROLS = 132;
    private static final int BAND_VOLUME = 166;
    private static final int BAND_TILES = 198;

    private int cardX;
    private int cardY;
    private int progressX;
    private int progressY;
    private int progressWidth;

    private VoidIconButton playPause;
    private VoidIconButton shuffle;
    private VoidIconButton repeat;
    private boolean scrubbing;
    private float openAnimation;

    public ExpandedPlayerScreen() {
        super(Component.translatable("spotifysync.player.title"));
    }

    @Override
    protected void init() {
        SpotifyManager manager = SpotifyManager.get();
        cardX = (width - CARD_WIDTH) / 2;
        cardY = Math.max(4, (height - CARD_HEIGHT) / 2);

        int contentX = cardX + PADDING + COVER + PADDING;
        int contentRight = cardX + CARD_WIDTH - PADDING;
        progressX = contentX;
        progressY = cardY + BAND_PROGRESS;
        progressWidth = contentRight - contentX;

        boolean extended = manager.supportsExtendedControls();

        // ---- transport row ------------------------------------------------
        int size = 26;
        int gap = 8;
        int controlsY = cardY + BAND_CONTROLS;
        int cursor = contentX;

        addRenderableWidget(new VoidIconButton(cursor, controlsY + 2, size - 4,
                VoidIconButton.Icon.PREVIOUS, Component.translatable("spotifysync.control.previous"),
                manager::previous));
        cursor += size - 4 + gap;

        playPause = addRenderableWidget(new VoidIconButton(cursor, controlsY, size,
                manager.state().playing() ? VoidIconButton.Icon.PAUSE : VoidIconButton.Icon.PLAY,
                Component.translatable("spotifysync.control.play_pause"),
                manager::togglePlayPause).filled());
        cursor += size + gap;

        addRenderableWidget(new VoidIconButton(cursor, controlsY + 2, size - 4,
                VoidIconButton.Icon.NEXT, Component.translatable("spotifysync.control.next"),
                manager::next));
        cursor += size - 4 + gap * 2;

        shuffle = addRenderableWidget(new VoidIconButton(cursor, controlsY + 2, size - 4,
                VoidIconButton.Icon.SHUFFLE, Component.translatable("spotifysync.control.shuffle"),
                manager::toggleShuffle));
        cursor += size - 4 + gap;

        repeat = addRenderableWidget(new VoidIconButton(cursor, controlsY + 2, size - 4,
                VoidIconButton.Icon.REPEAT, Component.translatable("spotifysync.control.repeat"),
                manager::cycleRepeat));

        // Shuffle / repeat / volume only exist on the Web API source.
        shuffle.active = extended;
        repeat.active = extended;

        // ---- volume -------------------------------------------------------
        int volumeWidth = 140;
        VoidSlider volume = new VoidSlider(contentRight - volumeWidth, cardY + BAND_VOLUME, volumeWidth,
                Component.translatable("spotifysync.player.volume"), 0, 100, 1,
                () -> {
                    int level = SpotifyManager.get().state().volumePercent();
                    return level < 0 ? 100 : level;
                },
                value -> SpotifyManager.get().setVolume((int) Math.round(value)),
                value -> Math.round(value) + "%");
        volume.active = extended;
        addRenderableWidget(volume);

        // ---- footer -------------------------------------------------------
        int footerY = cardY + CARD_HEIGHT - PADDING - BUTTON_HEIGHT;
        addRenderableWidget(new VoidButton(cardX + PADDING, footerY, 96, BUTTON_HEIGHT,
                Component.translatable("spotifysync.player.settings"), VoidButton.Style.UTILITY,
                () -> minecraft.setScreen(new SpotifySettingsScreen(this))));

        addRenderableWidget(new VoidButton(cardX + PADDING + 96 + 10, footerY, 118, BUTTON_HEIGHT,
                Component.translatable("spotifysync.player.open_spotify"), VoidButton.Style.SECONDARY,
                () -> {
                    String url = SpotifyManager.get().state().trackUrl();
                    if (url != null && !url.isEmpty()) {
                        Util.getPlatform().openUri(URI.create(url));
                    }
                }));

        addRenderableWidget(new VoidButton(contentRight - 80, footerY, 80, BUTTON_HEIGHT,
                Component.translatable("spotifysync.player.close"), VoidButton.Style.GHOST,
                this::onClose));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        openAnimation = Math.min(1f, openAnimation + 0.12f);
        SpotifyManager manager = SpotifyManager.get();
        PlaybackState state = manager.state();

        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Keep the transport icons in sync with the live state.
        if (playPause != null) {
            playPause.setIcon(state.playing() ? VoidIconButton.Icon.PAUSE : VoidIconButton.Icon.PLAY);
        }
        if (shuffle != null) {
            shuffle.setHighlighted(state.shuffle());
        }
        if (repeat != null) {
            repeat.setHighlighted(!"off".equals(state.repeatState()));
            repeat.setIcon("track".equals(state.repeatState())
                    ? VoidIconButton.Icon.REPEAT_ONE : VoidIconButton.Icon.REPEAT);
        }

        drawCard(graphics, state, mouseX, mouseY);
        // The card is painted first, the widgets sit on top of it.
        for (var widget : renderables) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, UiTheme.withAlpha(UiTheme.SCRIM, openAnimation));
        // Ambient wash tinted by the album art, rationed to one corner.
        int tint = UiTheme.tintFromCover(SpotifyManager.get().covers().averageColor());
        graphics.fillGradient(0, 0, width, height / 2,
                UiTheme.withAlpha(tint, 0.10f * openAnimation), 0x00000000);
    }

    private void drawCard(GuiGraphics graphics, PlaybackState state, int mouseX, int mouseY) {
        SyncConfig config = SyncConfig.get();
        SpotifyManager manager = SpotifyManager.get();

        UiRender.roundedRect(graphics, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, UiTheme.RADIUS_CARD_LG,
                UiTheme.withAlpha(UiTheme.SURFACE, openAnimation));
        UiRender.accentWash(graphics, cardX, cardY, CARD_WIDTH / 3, CARD_HEIGHT, openAnimation);
        UiRender.roundedBorder(graphics, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, UiTheme.RADIUS_CARD_LG,
                UiTheme.accent(0.45f * openAnimation));
        if (config.grain) {
            UiRender.grain(graphics, cardX + 1, cardY + 1, CARD_WIDTH - 2, CARD_HEIGHT - 2, 0.45f);
        }

        int coverX = cardX + PADDING;
        int coverY = cardY + PADDING;
        ResourceLocation cover = manager.covers().texture();
        if (cover != null && state.hasTrack()) {
            UiRender.image(graphics, cover, coverX, coverY, COVER, COVER, openAnimation);
            UiRender.fadeToBlack(graphics, coverX, coverY + COVER - 20, COVER, 20, 0.75f);
            UiRender.roundedBorder(graphics, coverX, coverY, COVER, COVER, UiTheme.RADIUS_CONTROL,
                    UiTheme.withAlpha(UiTheme.BORDER, 0.9f));
        } else {
            UiRender.coverPlaceholder(graphics, coverX, coverY, COVER, openAnimation);
        }

        int contentX = coverX + COVER + PADDING;
        int contentRight = cardX + CARD_WIDTH - PADDING;
        int contentWidth = contentRight - contentX;

        if (!manager.connected()) {
            UiRender.textScaled(graphics, "Not connected", contentX, cardY + PADDING, 1.6f,
                    UiTheme.TEXT_PRIMARY, false);
            UiRender.accentRule(graphics, contentX, cardY + PADDING + 24, 40, 1f);
            UiRender.textScaled(graphics, "open settings and pick a playback source",
                    contentX, cardY + PADDING + 34, 0.85f, UiTheme.TEXT_SECONDARY, false);
            return;
        }

        // ---- eyebrow --------------------------------------------------------
        String eyebrow = state.episode() ? "NOW PLAYING \u00b7 PODCAST" : "NOW PLAYING";
        UiRender.textScaled(graphics, eyebrow, contentX, cardY + PADDING, 0.75f,
                UiTheme.TEXT_MUTED, false);

        // ---- display title --------------------------------------------------
        String title = state.hasTrack() ? state.title() : "Nothing playing";
        float titleScale = 1.6f;
        String fittedTitle = UiRender.ellipsize(title, (int) (contentWidth / titleScale));
        UiRender.textScaled(graphics, fittedTitle, contentX, cardY + PADDING + 14, titleScale,
                UiTheme.TEXT_PRIMARY, false);

        UiRender.accentRule(graphics, contentX, cardY + PADDING + 38, 34, 0.9f);

        // ---- artist + album -------------------------------------------------
        UiRender.text(graphics, UiRender.ellipsize(state.artistLine(), contentWidth),
                contentX, cardY + PADDING + 48, UiTheme.TEXT_SECONDARY, false);
        UiRender.textScaled(graphics, UiRender.ellipsize(state.album(), (int) (contentWidth / 0.85f)),
                contentX, cardY + PADDING + 64, 0.85f, UiTheme.TEXT_MUTED, false);

        // ---- progress -------------------------------------------------------
        long progress = state.interpolatedProgressMs();
        boolean hoverProgress = isOverProgress(mouseX, mouseY);
        UiRender.progressBar(graphics, progressX, progressY, progressWidth, hoverProgress ? 4 : 3,
                state.progressFraction(), 1f, true);
        UiRender.textScaled(graphics, PlaybackState.formatTime(progress), progressX, progressY + 9, 0.8f,
                UiTheme.TEXT_SECONDARY, false);
        String duration = PlaybackState.formatTime(state.durationMs());
        UiRender.textScaled(graphics, duration,
                contentRight - UiRender.font().width(duration) * 0.8f, progressY + 9, 0.8f,
                UiTheme.TEXT_MUTED, false);
        if (hoverProgress && state.durationMs() > 0) {
            float fraction = (float) (mouseX - progressX) / (float) progressWidth;
            String preview = PlaybackState.formatTime((long) (fraction * state.durationMs()));
            UiRender.textScaledCentered(graphics, preview, mouseX, progressY - 14, 0.8f,
                    UiTheme.accent(), true);
        }

        // ---- glass metadata tiles ------------------------------------------
        int tileY = cardY + BAND_TILES;
        int tileHeight = 22;
        int tileGap = 8;
        int tileWidth = (contentWidth - tileGap * 2) / 3;
        drawTile(graphics, contentX, tileY, tileWidth, tileHeight, "SOURCE",
                manager.usingLocalSource() ? "WINDOWS" : "WEB API");
        drawTile(graphics, contentX + tileWidth + tileGap, tileY, tileWidth, tileHeight, "DEVICE",
                state.deviceName().isEmpty() ? "\u2014" : state.deviceName());
        String lyricsLabel;
        TrackLyrics lyrics = manager.lyrics().lyrics();
        if (manager.lyrics().loading()) {
            lyricsLabel = "SEARCHING";
        } else if (lyrics.isEmpty()) {
            lyricsLabel = "NONE";
        } else {
            lyricsLabel = lyrics.synced() ? "SYNCED" : "PLAIN";
        }
        drawTile(graphics, contentX + (tileWidth + tileGap) * 2, tileY, tileWidth, tileHeight,
                "LYRICS", lyricsLabel);

        // ---- lyrics preview under the cover ---------------------------------
        drawLyricsPreview(graphics, coverX, coverY + COVER + 12, COVER, state, lyrics);

        // ---- status dot ------------------------------------------------------
        int dotColor = manager.stale() ? 0xFFFF5A5A : UiTheme.ACCENT_SECONDARY;
        UiRender.roundedRect(graphics, contentRight - 5, cardY + PADDING + 1, 4, 4, 2, dotColor);
    }

    private void drawTile(GuiGraphics graphics, int x, int y, int width, int height, String label, String value) {
        UiRender.glassTile(graphics, x, y, width, height, 1f);
        UiRender.textScaled(graphics, label, x + 6, y + 4, 0.6f, UiTheme.TEXT_SECONDARY, false);
        UiRender.textScaled(graphics, UiRender.ellipsize(value, (int) ((width - 12) / 0.75f)),
                x + 6, y + 12, 0.75f, UiTheme.TEXT_PRIMARY, false);
    }

    private void drawLyricsPreview(GuiGraphics graphics, int x, int y, int width,
                                   PlaybackState state, TrackLyrics lyrics) {
        if (lyrics.isEmpty() || !state.hasTrack()) {
            return;
        }
        long position = state.interpolatedProgressMs() + SyncConfig.get().lyricsOffsetMs;
        List<LyricLine> lines = lyrics.lines();
        int active = lyrics.synced()
                ? lyrics.activeIndex(position)
                : (int) Math.min(lines.size() - 1, (long) (lines.size() * state.progressFraction()));
        if (active < 0) {
            active = 0;
        }
        for (int offset = 0; offset < 4; offset++) {
            int index = active + offset;
            if (index >= lines.size()) {
                break;
            }
            String text = lines.get(index).text();
            if (text == null || text.isBlank()) {
                continue;
            }
            int color = offset == 0 ? UiTheme.TEXT_PRIMARY
                    : UiTheme.withAlpha(UiTheme.TEXT_MUTED, 0.85f - offset * 0.18f);
            UiRender.textScaled(graphics, UiRender.ellipsize(text, (int) (width / 0.7f)),
                    x, y + offset * 10, 0.7f, color, false);
        }
    }

    private boolean isOverProgress(int mouseX, int mouseY) {
        return mouseX >= progressX && mouseX <= progressX + progressWidth
                && mouseY >= progressY - 5 && mouseY <= progressY + 8;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverProgress((int) mouseX, (int) mouseY)) {
            scrubbing = true;
            SpotifyManager.get().seekFraction((float) ((mouseX - progressX) / progressWidth));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrubbing && button == 0) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrubbing && button == 0) {
            scrubbing = false;
            SpotifyManager.get().seekFraction((float) ((mouseX - progressX) / progressWidth));
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            SpotifyManager.get().seekRelative(scrollY > 0 ? 5000L : -5000L);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
