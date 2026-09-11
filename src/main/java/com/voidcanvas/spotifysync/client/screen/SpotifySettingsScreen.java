package com.voidcanvas.spotifysync.client.screen;

import com.voidcanvas.spotifysync.client.SpotifyManager;
import com.voidcanvas.spotifysync.client.render.UiRender;
import com.voidcanvas.spotifysync.client.render.UiTheme;
import com.voidcanvas.spotifysync.client.screen.widget.VoidButton;
import com.voidcanvas.spotifysync.client.screen.widget.VoidSlider;
import com.voidcanvas.spotifysync.client.screen.widget.VoidToggle;
import com.voidcanvas.spotifysync.config.HudAnchor;
import com.voidcanvas.spotifysync.config.LyricsMode;
import com.voidcanvas.spotifysync.config.SyncConfig;
import com.voidcanvas.spotifysync.spotify.SpotifyAuth;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;

/**
 * Settings surface.
 *
 * <p>Layout rules of the design system are applied literally here: a single
 * near-black card on a black canvas, 20px card padding, a 20px gutter between
 * the two content columns and a fixed 32px vertical rhythm so no two controls
 * can ever touch. Every tab lays its controls out on that grid, so a tab holds
 * at most two columns of five rows.</p>
 */
public final class SpotifySettingsScreen extends Screen {

    private enum Tab {
        ACCOUNT("spotifysync.tab.account"),
        PLAYER("spotifysync.tab.player"),
        LYRICS("spotifysync.tab.lyrics"),
        IMMERSIVE("spotifysync.tab.immersive");

        private final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    // ---- grid -----------------------------------------------------------
    private static final int CARD_WIDTH = 440;
    private static final int CARD_HEIGHT = 316;
    private static final int PADDING = 20;
    /** Vertical rhythm: the tallest control (slider) is 26px, so 32 leaves air. */
    private static final int ROW = 32;
    private static final int COLUMN_GAP = 20;
    private static final int CONTENT_WIDTH = CARD_WIDTH - PADDING * 2;
    private static final int COLUMN_WIDTH = (CONTENT_WIDTH - COLUMN_GAP) / 2;
    private static final int BUTTON_HEIGHT = 20;
    /** Offset that optically centres a 20px button on a 26px slider row. */
    private static final int BUTTON_ON_ROW = 3;
    private static final int HEADER_HEIGHT = 104;
    private static final int FOOTER_HEIGHT = 56;

    private final Screen parent;
    private Tab tab = Tab.ACCOUNT;
    private EditBox clientIdBox;
    private int cardX;
    private int cardY;
    private float openAnimation;

    public SpotifySettingsScreen(Screen parent) {
        super(Component.translatable("spotifysync.settings.title"));
        this.parent = parent;
    }

    public SpotifySettingsScreen() {
        this(null);
    }

    private int column2(int x) {
        return x + COLUMN_WIDTH + COLUMN_GAP;
    }

    @Override
    protected void init() {
        SyncConfig config = SyncConfig.get();
        cardX = (width - CARD_WIDTH) / 2;
        cardY = Math.max(4, (height - CARD_HEIGHT) / 2);

        // ---- tab strip --------------------------------------------------
        int tabGap = 6;
        int tabWidth = (CONTENT_WIDTH - tabGap * (Tab.values().length - 1)) / Tab.values().length;
        int tabY = cardY + 72;
        int tabX = cardX + PADDING;
        for (Tab value : Tab.values()) {
            boolean selected = value == tab;
            addRenderableWidget(new VoidButton(tabX, tabY, tabWidth, BUTTON_HEIGHT,
                    Component.translatable(value.key),
                    selected ? VoidButton.Style.UTILITY : VoidButton.Style.GHOST,
                    () -> {
                        tab = value;
                        rebuildWidgets();
                    }));
            tabX += tabWidth + tabGap;
        }

        int x = cardX + PADDING;
        int y = cardY + HEADER_HEIGHT;

        switch (tab) {
            case ACCOUNT -> buildAccount(config, x, y);
            case PLAYER -> buildPlayer(config, x, y);
            case LYRICS -> buildLyrics(config, x, y);
            case IMMERSIVE -> buildImmersive(config, x, y);
        }

        // ---- footer -----------------------------------------------------
        int footerY = cardY + CARD_HEIGHT - PADDING - BUTTON_HEIGHT;
        addRenderableWidget(new VoidButton(cardX + PADDING, footerY, 110, BUTTON_HEIGHT,
                Component.translatable("spotifysync.settings.open_player"), VoidButton.Style.SECONDARY,
                () -> minecraft.setScreen(new ExpandedPlayerScreen())));
        addRenderableWidget(new VoidButton(cardX + CARD_WIDTH - PADDING - 90, footerY, 90, BUTTON_HEIGHT,
                Component.translatable("spotifysync.settings.done"), VoidButton.Style.PRIMARY,
                this::onClose));
    }

    // ------------------------------------------------------------------ tabs

    private void buildAccount(SyncConfig config, int x, int y) {
        SpotifyManager manager = SpotifyManager.get();
        int right = column2(x);

        // Row 1 - client id + connect.
        clientIdBox = new EditBox(font, x, y + BUTTON_ON_ROW, COLUMN_WIDTH, BUTTON_HEIGHT,
                Component.translatable("spotifysync.settings.client_id"));
        clientIdBox.setMaxLength(64);
        clientIdBox.setValue(config.clientId);
        clientIdBox.setHint(Component.literal("client id"));
        clientIdBox.setResponder(value -> {
            config.clientId = value.trim();
            config.save();
        });
        addRenderableWidget(clientIdBox);

        addRenderableWidget(new VoidButton(right, y + BUTTON_ON_ROW, COLUMN_WIDTH, BUTTON_HEIGHT,
                Component.translatable(manager.connected()
                        ? "spotifysync.settings.reconnect" : "spotifysync.settings.connect"),
                VoidButton.Style.PRIMARY,
                manager::beginLogin));

        // Row 2 - playback source (no Premium needed for the local one).
        addRenderableWidget(new VoidButton(x, y + ROW + BUTTON_ON_ROW, COLUMN_WIDTH, BUTTON_HEIGHT,
                Component.translatable("spotifysync.settings.source")
                        .append(Component.literal(": "))
                        .append(Component.translatable(config.playbackSource.translationKey())),
                VoidButton.Style.SECONDARY,
                () -> {
                    config.playbackSource = config.playbackSource.next();
                    config.save();
                    SpotifyManager.get().poll();
                    rebuildWidgets();
                }));

        addRenderableWidget(new VoidButton(right, y + ROW + BUTTON_ON_ROW, COLUMN_WIDTH, BUTTON_HEIGHT,
                Component.translatable("spotifysync.settings.developer_portal"), VoidButton.Style.GHOST,
                () -> Util.getPlatform().openUri(URI.create("https://developer.spotify.com/dashboard"))));

        // Row 3 - timing.
        addRenderableWidget(new VoidSlider(x, y + ROW * 2, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.poll_interval"), 700, 10000, 100,
                () -> config.pollIntervalMs,
                value -> {
                    config.pollIntervalMs = (int) Math.round(value);
                    config.save();
                },
                value -> Math.round(value) + " ms"));

        addRenderableWidget(new VoidSlider(right, y + ROW * 2, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.callback_port"), 1024, 65535, 1,
                () -> config.callbackPort,
                value -> {
                    config.callbackPort = (int) Math.round(value);
                    config.save();
                },
                value -> String.valueOf(Math.round(value))));

        // Row 4 - auto connect.
        addRenderableWidget(new VoidToggle(x, y + ROW * 3, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.auto_connect"),
                "poll automatically in game",
                () -> config.autoConnect,
                value -> {
                    config.autoConnect = value;
                    config.save();
                    if (value) {
                        SpotifyManager.get().poll();
                    }
                }));

        addRenderableWidget(new VoidButton(right, y + ROW * 3 + BUTTON_ON_ROW, COLUMN_WIDTH, BUTTON_HEIGHT,
                Component.translatable("spotifysync.settings.logout"), VoidButton.Style.DANGER,
                () -> {
                    SpotifyManager.get().logout();
                    rebuildWidgets();
                }));
    }

    private void buildPlayer(SyncConfig config, int x, int y) {
        int right = column2(x);

        addRenderableWidget(new VoidToggle(x, y, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.hud_enabled"),
                "show the mini player",
                () -> config.hudEnabled,
                value -> {
                    config.hudEnabled = value;
                    config.save();
                }));

        addRenderableWidget(new VoidToggle(right, y, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.show_on_screens"),
                "clickable over inventory and chat",
                () -> config.showOnScreens,
                value -> {
                    config.showOnScreens = value;
                    config.save();
                }));

        addRenderableWidget(new VoidToggle(x, y + ROW, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.show_cover"),
                "album artwork thumbnail",
                () -> config.showCover,
                value -> {
                    config.showCover = value;
                    config.save();
                }));

        addRenderableWidget(new VoidToggle(right, y + ROW, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.grain"),
                "animated film grain",
                () -> config.grain,
                value -> {
                    config.grain = value;
                    config.save();
                }));

        addRenderableWidget(new VoidButton(x, y + ROW * 2 + BUTTON_ON_ROW, COLUMN_WIDTH, BUTTON_HEIGHT,
                Component.translatable("spotifysync.settings.anchor")
                        .append(Component.literal(": "))
                        .append(Component.translatable(config.hudAnchor.translationKey())),
                VoidButton.Style.SECONDARY,
                () -> {
                    config.hudAnchor = config.hudAnchor.next();
                    config.save();
                    rebuildWidgets();
                }));

        addRenderableWidget(new VoidSlider(right, y + ROW * 2, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.hud_scale"), 0.5, 2.5, 0.05,
                () -> config.hudScale,
                value -> {
                    config.hudScale = (float) value;
                    config.save();
                },
                value -> String.format("%.2fx", value)));

        addRenderableWidget(new VoidSlider(x, y + ROW * 3, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.offset_x"), -200, 200, 1,
                () -> config.hudOffsetX,
                value -> {
                    config.hudOffsetX = (int) Math.round(value);
                    config.save();
                },
                value -> String.valueOf(Math.round(value))));

        addRenderableWidget(new VoidSlider(right, y + ROW * 3, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.offset_y"), -200, 200, 1,
                () -> config.hudOffsetY,
                value -> {
                    config.hudOffsetY = (int) Math.round(value);
                    config.save();
                },
                value -> String.valueOf(Math.round(value))));

        addRenderableWidget(new VoidSlider(x, y + ROW * 4, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.hud_opacity"), 0.15, 1.0, 0.05,
                () -> config.hudOpacity,
                value -> {
                    config.hudOpacity = (float) value;
                    config.save();
                },
                value -> Math.round(value * 100) + "%"));

        addRenderableWidget(new VoidToggle(right, y + ROW * 4, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.hide_when_idle"),
                "hide while nothing plays",
                () -> config.hideWhenIdle,
                value -> {
                    config.hideWhenIdle = value;
                    config.save();
                }));
    }

    private void buildLyrics(SyncConfig config, int x, int y) {
        int right = column2(x);

        addRenderableWidget(new VoidToggle(x, y, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.lyrics_enabled"),
                "fetch synced lyrics",
                () -> config.lyricsEnabled,
                value -> {
                    config.lyricsEnabled = value;
                    config.save();
                }));

        addRenderableWidget(new VoidToggle(right, y, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.lyrics_on_screens"),
                "keep lyrics over open screens",
                () -> config.lyricsShowOnScreens,
                value -> {
                    config.lyricsShowOnScreens = value;
                    config.save();
                }));

        addRenderableWidget(new VoidButton(x, y + ROW + BUTTON_ON_ROW, COLUMN_WIDTH, BUTTON_HEIGHT,
                Component.translatable("spotifysync.settings.lyrics_mode")
                        .append(Component.literal(": "))
                        .append(Component.translatable(config.lyricsMode.translationKey())),
                VoidButton.Style.SECONDARY,
                () -> {
                    config.lyricsMode = config.lyricsMode.next();
                    config.save();
                    rebuildWidgets();
                }));

        addRenderableWidget(new VoidButton(right, y + ROW + BUTTON_ON_ROW, COLUMN_WIDTH, BUTTON_HEIGHT,
                Component.translatable("spotifysync.settings.retry_lyrics"), VoidButton.Style.UTILITY,
                () -> SpotifyManager.get().lyrics().retry(SpotifyManager.get().state())));

        addRenderableWidget(new VoidSlider(x, y + ROW * 2, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.lyrics_position"), 0.05, 0.95, 0.01,
                () -> config.lyricsHudY,
                value -> {
                    config.lyricsHudY = (float) value;
                    config.save();
                },
                value -> Math.round(value * 100) + "%"));

        addRenderableWidget(new VoidSlider(right, y + ROW * 2, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.lyrics_scale"), 0.5, 2.5, 0.05,
                () -> config.lyricsScale,
                value -> {
                    config.lyricsScale = (float) value;
                    config.save();
                },
                value -> String.format("%.2fx", value)));

        addRenderableWidget(new VoidSlider(x, y + ROW * 3, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.lyrics_context"), 0, 4, 1,
                () -> config.lyricsContextLines,
                value -> {
                    config.lyricsContextLines = (int) Math.round(value);
                    config.save();
                },
                value -> String.valueOf(Math.round(value))));

        addRenderableWidget(new VoidSlider(right, y + ROW * 3, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.lyrics_offset"), -5000, 5000, 50,
                () -> config.lyricsOffsetMs,
                value -> {
                    config.lyricsOffsetMs = (int) Math.round(value);
                    config.save();
                },
                value -> Math.round(value) + " ms"));
    }

    private void buildImmersive(SyncConfig config, int x, int y) {
        int right = column2(x);

        addRenderableWidget(new VoidSlider(x, y, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.ring_radius"), 1.5, 12.0, 0.1,
                () -> config.ringRadius,
                value -> {
                    config.ringRadius = (float) value;
                    config.save();
                },
                value -> String.format("%.1f", value)));

        addRenderableWidget(new VoidSlider(right, y, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.ring_height"), -1.0, 6.0, 0.1,
                () -> config.ringHeight,
                value -> {
                    config.ringHeight = (float) value;
                    config.save();
                },
                value -> String.format("%.1f", value)));

        addRenderableWidget(new VoidSlider(x, y + ROW, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.ring_scale"), 0.3, 3.0, 0.05,
                () -> config.ringScale,
                value -> {
                    config.ringScale = (float) value;
                    config.save();
                },
                value -> String.format("%.2fx", value)));

        addRenderableWidget(new VoidSlider(right, y + ROW, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.ring_lines"), 1, 12, 1,
                () -> config.ringLineCount,
                value -> {
                    config.ringLineCount = (int) Math.round(value);
                    config.save();
                },
                value -> String.valueOf(Math.round(value))));

        addRenderableWidget(new VoidSlider(x, y + ROW * 2, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.ring_speed"), 0.0, 4.0, 0.05,
                () -> config.ringSpinSpeed,
                value -> {
                    config.ringSpinSpeed = (float) value;
                    config.save();
                },
                value -> String.format("%.2fx", value)));

        addRenderableWidget(new VoidSlider(right, y + ROW * 2, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.ring_opacity"), 0.1, 1.0, 0.05,
                () -> config.ringOpacity,
                value -> {
                    config.ringOpacity = (float) value;
                    config.save();
                },
                value -> Math.round(value * 100) + "%"));

        addRenderableWidget(new VoidToggle(x, y + ROW * 3, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.ring_spin"),
                "lines orbit the player",
                () -> config.ringSpin,
                value -> {
                    config.ringSpin = value;
                    config.save();
                }));

        addRenderableWidget(new VoidToggle(right, y + ROW * 3, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.ring_bob"),
                "soft vertical float",
                () -> config.ringBob,
                value -> {
                    config.ringBob = value;
                    config.save();
                }));

        addRenderableWidget(new VoidToggle(x, y + ROW * 4, COLUMN_WIDTH,
                Component.translatable("spotifysync.settings.ring_see_through"),
                "render through blocks",
                () -> config.ringSeeThrough,
                value -> {
                    config.ringSeeThrough = value;
                    config.save();
                }));
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        openAnimation = Math.min(1f, openAnimation + 0.12f);
        renderBackground(graphics, mouseX, mouseY, partialTick);
        drawChrome(graphics);
        for (var widget : renderables) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, UiTheme.withAlpha(UiTheme.SCRIM, openAnimation));
    }

    private void drawChrome(GuiGraphics graphics) {
        SyncConfig config = SyncConfig.get();
        SpotifyManager manager = SpotifyManager.get();

        UiRender.roundedRect(graphics, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, UiTheme.RADIUS_CARD_LG,
                UiTheme.withAlpha(UiTheme.SURFACE, openAnimation));
        UiRender.roundedBorder(graphics, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, UiTheme.RADIUS_CARD_LG,
                UiTheme.accent(0.4f * openAnimation));
        if (config.grain) {
            UiRender.grain(graphics, cardX + 1, cardY + 1, CARD_WIDTH - 2, CARD_HEIGHT - 2, 0.4f);
        }

        // ---- header ------------------------------------------------------
        int left = cardX + PADDING;
        UiRender.accentRule(graphics, left, cardY + PADDING, 28, 1f);
        UiRender.textScaled(graphics, "SPOTIFY SYNC", left, cardY + PADDING + 8, 1.4f,
                UiTheme.TEXT_PRIMARY, false);

        SpotifyAuth.Status status = manager.auth().status();
        String statusLabel = switch (status) {
            case CONNECTED -> "connected";
            case WAITING_FOR_BROWSER -> "waiting for browser";
            case ERROR -> "error";
            default -> manager.connected() ? "ready" : "disconnected";
        };
        int statusColor = switch (status) {
            case CONNECTED -> UiTheme.ACCENT_SECONDARY;
            case ERROR -> 0xFFFF5A5A;
            case WAITING_FOR_BROWSER -> UiTheme.accent();
            default -> manager.connected() ? UiTheme.ACCENT_SECONDARY : UiTheme.TEXT_MUTED;
        };
        String detail = manager.usingLocalSource() ? manager.sourceStatus() : manager.auth().message();
        String statusText = statusLabel + (detail == null || detail.isEmpty() ? "" : " \u00b7 " + detail);
        UiRender.textScaled(graphics, UiRender.ellipsize(statusText, (int) (CONTENT_WIDTH / 0.8f)),
                left, cardY + PADDING + 30, 0.8f, statusColor, false);

        UiRender.hairline(graphics, left, cardY + 62, CONTENT_WIDTH, 0.5f);

        // Section caption above the first content row.
        String caption = switch (tab) {
            case ACCOUNT -> "CONNECTION";
            case PLAYER -> "MINI PLAYER";
            case LYRICS -> "LYRICS";
            case IMMERSIVE -> "3D LYRICS RING";
        };
        UiRender.textScaled(graphics, caption, left, cardY + HEADER_HEIGHT - 14, 0.7f,
                UiTheme.TEXT_SECONDARY, false);

        // ---- footer ------------------------------------------------------
        int footerLine = cardY + CARD_HEIGHT - FOOTER_HEIGHT;
        UiRender.hairline(graphics, left, footerLine, CONTENT_WIDTH, 0.5f);
        UiRender.textScaled(graphics, "keys: K settings \u00b7 J player \u00b7 L lyrics mode",
                left, footerLine + 8, 0.7f, UiTheme.TEXT_MUTED, false);
    }

    @Override
    public void onClose() {
        SyncConfig.get().save();
        if (parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Convenience accessor used by the key binding handler. */
    public static SpotifySettingsScreen create() {
        return new SpotifySettingsScreen();
    }

    /** Keeps the unused enum helper referenced for clarity. */
    public static LyricsMode cycle(LyricsMode mode) {
        return mode.next();
    }

    public static HudAnchor cycle(HudAnchor anchor) {
        return anchor.next();
    }
}
