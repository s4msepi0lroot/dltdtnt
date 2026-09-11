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
 * <p>Opened by clicking the mini player (or with the dedicated key). Layout
 * follows the design system: one large near-black card on a black scrim, a
 * full-bleed cover on the left, display-scale title, an accent hairline, a
 * scrubbable progress bar, transport controls, a volume slider, a row of blue
 * glass metadata tiles and a live lyrics column.</p>
 */
public final class ExpandedPlayerScreen extends Screen {

    private static final int CARD_WIDTH = 340;
    private static final int CARD_HEIGHT = 196;
    private static final int COVER = 108;
    private static final int PADDING = 14;

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
        cardY = (height - CARD_HEIGHT) / 2;

        int contentX = cardX + PADDING + COVER + PADDING;
        int contentRight = cardX + CARD_WIDTH - PADDING;
        progressX = contentX;
        progressY = cardY + 96;
        progressWidth = contentRight - contentX;

        int controlsY = cardY + 112;
        int buttonSize = 22;

        addRenderableWidget(new VoidIconButton(contentX, controlsY, buttonSize,
                VoidIconButton.Icon.PREVIOUS, Component.translatable("spotifysync.control.previous"),
                manager::previous));

        playPause = addRenderableWidget(new VoidIconButton(contentX + buttonSize + 6, controlsY - 2, buttonSize + 4,
                manager.state().playing() ? VoidIconButton.Icon.PAUSE : VoidIconButton.Icon.PLAY,
                Component.translatable("spotifysync.control.play_pause"),
                manager::togglePlayPause).filled());

        addRenderableWidget(new VoidIconButton(contentX + (buttonSize + 6) * 2 + 4, controlsY, buttonSize,
                VoidIconButton.Icon.NEXT, Component.translatable("spotifysync.control.next"),
                manager::next));

        shuffle = addRenderableWidget(new VoidIconButton(contentX + (buttonSize + 6) * 3 + 10, controlsY, buttonSize,
                VoidIconButton.Icon.SHUFFLE, Component.translatable("spotifysync.control.shuffle"),
                manager::toggleShuffle));

        repeat = addRenderableWidget(new VoidIconButton(contentX + (buttonSize + 6) * 4 + 10, controlsY, buttonSize,
                VoidIconButton.Icon.REPEAT, Component.translatable("spotifysync.control.repeat"),
                manager::cycleRepeat));

        int volumeWidth = 96;
        addRenderableWidget(new VoidSlider(contentRight - volumeWidth, cardY + 136, volumeWidth,
                Component.translatable("spotifysync.player.volume"), 0, 100, 1,
                () -> {
                    int volume = SpotifyManager.get().state().volumePercent();
                    return volume < 0 ? 100 : volume;
                },
                value -> SpotifyManager.get().setVolume((int) Math.round(value)),
                value -> String.valueOf(Math.round(value)) + "%"));

        int footerY = cardY + CARD_HEIGHT - PADDING - 18;
        addRenderableWidget(new VoidButton(cardX + PADDING, footerY, 78, 18,
                Component.translatable("spotifysync.player.settings"), VoidButton.Style.UTILITY,
                () -> minecraft.setScreen(new SpotifySettingsScreen(this))));

        addRenderableWidget(new VoidButton(cardX + PADDING + 84, footerY, 96, 18,
                Component.translatable("spotifysync.player.open_spotify"), VoidButton.Style.SECONDARY,
                () -> {
                    String url = SpotifyManager.get().state().trackUrl();
                    if (url != null && !url.isEmpty()) {
                        Util.getPlatform().openUri(URI.create(url));
                    }
                }));

        addRenderableWidget(new VoidButton(contentRight - 70, footerY, 70, 18,
                Component.translatable("spotifysync.player.close"), VoidButton.Style.GHOST,
                this::onClose));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        openAnimation = Math.min(1f, openAnimation + 0.12f);
        SpotifyManager manager = SpotifyManager.get();
        PlaybackState state = manager.state();

        super.render(graphics, mouseX, mouseY, partialTick);

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
        // Widgets are drawn by super.render() first, so re-render them on top
        // of the card surface.
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
            UiRender.fadeToBlack(graphics, coverX, coverY + COVER - 18, COVER, 18, 0.75f);
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
            UiRender.accentRule(graphics, contentX, cardY + PADDING + 20, 40, 1f);
            UiRender.textScaled(graphics, "open settings and paste your client id",
                    contentX, cardY + PADDING + 28, 0.85f, UiTheme.TEXT_SECONDARY, false);
            return;
        }

        // ---- label / eyebrow ------------------------------------------------
        String eyebrow = state.episode() ? "NOW PLAYING \u00b7 PODCAST" : "NOW PLAYING";
        UiRender.textScaled(graphics, eyebrow, contentX, cardY + PADDING, 0.75f,
                UiTheme.TEXT_MUTED, false);

        // ---- display title --------------------------------------------------
        String title = state.hasTrack() ? state.title() : "Nothing playing";
        float titleScale = 1.55f;
        String fittedTitle = UiRender.ellipsize(title, (int) (contentWidth / titleScale));
        UiRender.textScaled(graphics, fittedTitle, contentX, cardY + PADDING + 10, titleScale,
                UiTheme.TEXT_PRIMARY, false);

        UiRender.accentRule(graphics, contentX, cardY + PADDING + 30, 34, 0.9f);

        // ---- artist + album -------------------------------------------------
        UiRender.text(graphics, UiRender.ellipsize(state.artistLine(), contentWidth),
                contentX, cardY + PADDING + 38, UiTheme.TEXT_SECONDARY, false);
        UiRender.textScaled(graphics, UiRender.ellipsize(state.album(), (int) (contentWidth / 0.85f)),
                contentX, cardY + PADDING + 50, 0.85f, UiTheme.TEXT_MUTED, false);

        // ---- progress -------------------------------------------------------
        long progress = state.interpolatedProgressMs();
        boolean hoverProgress = isOverProgress(mouseX, mouseY);
        UiRender.progressBar(graphics, progressX, progressY, progressWidth, hoverProgress ? 4 : 3,
                state.progressFraction(), 1f, true);
        UiRender.textScaled(graphics, PlaybackState.formatTime(progress), progressX, progressY + 7, 0.8f,
                UiTheme.TEXT_SECONDARY, false);
        String duration = PlaybackState.formatTime(state.durationMs());
        UiRender.textScaled(graphics, duration,
                contentRight - UiRender.font().width(duration) * 0.8f, progressY + 7, 0.8f,
                UiTheme.TEXT_MUTED, false);
        if (hoverProgress && state.durationMs() > 0) {
            float fraction = (float) (mouseX - progressX) / (float) progressWidth;
            String preview = PlaybackState.formatTime((long) (fraction * state.durationMs()));
            UiRender.textScaledCentered(graphics, preview, mouseX, progressY - 12, 0.8f,
                    UiTheme.accent(), true);
        }

        // ---- glass metadata tiles ------------------------------------------
        int tileY = cardY + 158;
        int tileHeight = 16;
        int tileGap = 5;
        int tileWidth = (contentWidth - tileGap * 2) / 3;
        drawTile(graphics, contentX, tileY, tileWidth, tileHeight, "DEVICE",
                state.deviceName().isEmpty() ? "\u2014" : state.deviceName());
        drawTile(graphics, contentX + tileWidth + tileGap, tileY, tileWidth, tileHeight, "REPEAT",
                state.repeatState().toUpperCase());
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
        drawLyricsPreview(graphics, coverX, cardY + PADDING + COVER + 8, COVER, state, lyrics);

        // ---- status dot ------------------------------------------------------
        int dotColor = manager.stale() ? 0xFFFF5A5A : UiTheme.ACCENT_SECONDARY;
        UiRender.roundedRect(graphics, contentRight - 6, cardY + PADDING + 1, 4, 4, 2, dotColor);
    }

    private void drawTile(GuiGraphics graphics, int x, int y, int width, int height, String label, String value) {
        UiRender.glassTile(graphics, x, y, width, height, 1f);
        UiRender.textScaled(graphics, label, x + 4, y + 2, 0.6f, UiTheme.TEXT_SECONDARY, false);
        UiRender.textScaled(graphics, UiRender.ellipsize(value, (int) ((width - 8) / 0.75f)),
                x + 4, y + 8, 0.75f, UiTheme.TEXT_PRIMARY, false);
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
        for (int offset = 0; offset < 3; offset++) {
            int index = active + offset;
            if (index >= lines.size()) {
                break;
            }
            String text = lines.get(index).text();
            if (text == null || text.isBlank()) {
                continue;
            }
            int color = offset == 0 ? UiTheme.TEXT_PRIMARY
                    : UiTheme.withAlpha(UiTheme.TEXT_MUTED, 0.8f - offset * 0.2f);
            UiRender.textScaled(graphics, UiRender.ellipsize(text, (int) (width / 0.7f)),
                    x, y + offset * 8, 0.7f, color, false);
        }
    }

    private boolean isOverProgress(int mouseX, int mouseY) {
        return mouseX >= progressX && mouseX <= progressX + progressWidth
                && mouseY >= progressY - 4 && mouseY <= progressY + 7;
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
