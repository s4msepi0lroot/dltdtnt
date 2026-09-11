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
 * Settings surface. Four tabs keep every page short and scroll-free, matching
 * the dense tool-panel character of the design system: black canvas, one
 * near-black card, hairline separators and a single rationed accent.
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

    private static final int CARD_WIDTH = 320;
    private static final int CARD_HEIGHT = 218;
    private static final int PADDING = 14;

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

    @Override
    protected void init() {
        SyncConfig config = SyncConfig.get();
        cardX = (width - CARD_WIDTH) / 2;
        cardY = (height - CARD_HEIGHT) / 2;

        int tabY = cardY + 36;
        int tabWidth = 72;
        int tabX = cardX + PADDING;
        for (Tab value : Tab.values()) {
            boolean selected = value == tab;
            addRenderableWidget(new VoidButton(tabX, tabY, tabWidth, 18,
                    Component.translatable(value.key),
                    selected ? VoidButton.Style.UTILITY : VoidButton.Style.GHOST,
                    () -> {
                        tab = value;
                        rebuildWidgets();
                    }));
            tabX += tabWidth + 5;
        }

        int x = cardX + PADDING;
        int y = cardY + 64;
        int contentWidth = CARD_WIDTH - PADDING * 2;

        switch (tab) {
            case ACCOUNT -> buildAccount(config, x, y, contentWidth);
            case PLAYER -> buildPlayer(config, x, y, contentWidth);
            case LYRICS -> buildLyrics(config, x, y, contentWidth);
            case IMMERSIVE -> buildImmersive(config, x, y, contentWidth);
        }

        int footerY = cardY + CARD_HEIGHT - PADDING - 18;
        addRenderableWidget(new VoidButton(cardX + CARD_WIDTH - PADDING - 74, footerY, 74, 18,
                Component.translatable("spotifysync.settings.done"), VoidButton.Style.PRIMARY,
                this::onClose));
        addRenderableWidget(new VoidButton(cardX + PADDING, footerY, 86, 18,
                Component.translatable("spotifysync.settings.open_player"), VoidButton.Style.SECONDARY,
                () -> minecraft.setScreen(new ExpandedPlayerScreen())));
    }

    // ------------------------------------------------------------------ tabs

    private void buildAccount(SyncConfig config, int x, int y, int contentWidth) {
        clientIdBox = new EditBox(font, x, y + 14, contentWidth - 96, 18,
                Component.translatable("spotifysync.settings.client_id"));
        clientIdBox.setMaxLength(64);
        clientIdBox.setValue(config.clientId);
        clientIdBox.setHint(Component.literal("32-character client id"));
        clientIdBox.setResponder(value -> {
            config.clientId = value.trim();
            config.save();
        });
        addRenderableWidget(clientIdBox);

        SpotifyManager manager = SpotifyManager.get();
        addRenderableWidget(new VoidButton(x + contentWidth - 92, y + 14, 92, 18,
                Component.translatable(manager.connected()
                        ? "spotifysync.settings.reconnect" : "spotifysync.settings.connect"),
                VoidButton.Style.PRIMARY,
                manager::beginLogin));

        addRenderableWidget(new VoidSlider(x, y + 44, contentWidth - 110,
                Component.translatable("spotifysync.settings.poll_interval"), 700, 10000, 100,
                () -> config.pollIntervalMs,
                value -> {
                    config.pollIntervalMs = (int) Math.round(value);
                    config.save();
                },
                value -> Math.round(value) + " ms"));

        addRenderableWidget(new VoidSlider(x, y + 76, contentWidth - 110,
                Component.translatable("spotifysync.settings.callback_port"), 1024, 65535, 1,
                () -> config.callbackPort,
                value -> {
                    config.callbackPort = (int) Math.round(value);
                    config.save();
                },
                value -> String.valueOf(Math.round(value))));

        addRenderableWidget(new VoidToggle(x, y + 104, contentWidth,
                Component.translatable("spotifysync.settings.auto_connect"),
                "poll spotify automatically in game",
                () -> config.autoConnect,
                value -> {
                    config.autoConnect = value;
                    config.save();
                    if (value) {
                        SpotifyManager.get().poll();
                    }
                }));

        addRenderableWidget(new VoidButton(x, y + 132, 120, 18,
                Component.translatable("spotifysync.settings.developer_portal"), VoidButton.Style.GHOST,
                () -> Util.getPlatform().openUri(URI.create("https://developer.spotify.com/dashboard"))));

        addRenderableWidget(new VoidButton(x + contentWidth - 74, y + 132, 74, 18,
                Component.translatable("spotifysync.settings.logout"), VoidButton.Style.DANGER,
                () -> {
                    SpotifyManager.get().logout();
                    rebuildWidgets();
                }));
    }

    private void buildPlayer(SyncConfig config, int x, int y, int contentWidth) {
        addRenderableWidget(new VoidToggle(x, y, contentWidth,
                Component.translatable("spotifysync.settings.hud_enabled"),
                "show the mini player on the hud",
                () -> config.hudEnabled,
                value -> {
                    config.hudEnabled = value;
                    config.save();
                }));

        addRenderableWidget(new VoidToggle(x, y + 26, contentWidth,
                Component.translatable("spotifysync.settings.show_on_screens"),
                "keep it clickable over inventory and chat",
                () -> config.showOnScreens,
                value -> {
                    config.showOnScreens = value;
                    config.save();
                }));

        addRenderableWidget(new VoidToggle(x, y + 52, contentWidth,
                Component.translatable("spotifysync.settings.show_cover"),
                "album artwork thumbnail",
                () -> config.showCover,
                value -> {
                    config.showCover = value;
                    config.save();
                }));

        addRenderableWidget(new VoidToggle(x, y + 78, contentWidth,
                Component.translatable("spotifysync.settings.grain"),
                "animated film grain over dark panels",
                () -> config.grain,
                value -> {
                    config.grain = value;
                    config.save();
                }));

        addRenderableWidget(new VoidButton(x, y + 106, 132, 18,
                Component.translatable("spotifysync.settings.anchor")
                        .append(Component.literal(": "))
                        .append(Component.translatable(config.hudAnchor.translationKey())),
                VoidButton.Style.SECONDARY,
                () -> {
                    config.hudAnchor = config.hudAnchor.next();
                    config.save();
                    rebuildWidgets();
                }));

        addRenderableWidget(new VoidSlider(x + 142, y + 100, contentWidth - 142,
                Component.translatable("spotifysync.settings.hud_scale"), 0.5, 2.5, 0.05,
                () -> config.hudScale,
                value -> {
                    config.hudScale = (float) value;
                    config.save();
                },
                value -> String.format("%.2fx", value)));

        addRenderableWidget(new VoidSlider(x, y + 130, (contentWidth - 10) / 2,
                Component.translatable("spotifysync.settings.offset_x"), -200, 200, 1,
                () -> config.hudOffsetX,
                value -> {
                    config.hudOffsetX = (int) Math.round(value);
                    config.save();
                },
                value -> String.valueOf(Math.round(value))));

        addRenderableWidget(new VoidSlider(x + (contentWidth + 10) / 2, y + 130, (contentWidth - 10) / 2,
                Component.translatable("spotifysync.settings.offset_y"), -200, 200, 1,
                () -> config.hudOffsetY,
                value -> {
                    config.hudOffsetY = (int) Math.round(value);
                    config.save();
                },
                value -> String.valueOf(Math.round(value))));

        addRenderableWidget(new VoidSlider(x, y + 160, contentWidth,
                Component.translatable("spotifysync.settings.hud_opacity"), 0.15, 1.0, 0.05,
                () -> config.hudOpacity,
                value -> {
                    config.hudOpacity = (float) value;
                    config.save();
                },
                value -> Math.round(value * 100) + "%"));
    }

    private void buildLyrics(SyncConfig config, int x, int y, int contentWidth) {
        addRenderableWidget(new VoidToggle(x, y, contentWidth,
                Component.translatable("spotifysync.settings.lyrics_enabled"),
                "fetch synced lyrics for the current track",
                () -> config.lyricsEnabled,
                value -> {
                    config.lyricsEnabled = value;
                    config.save();
                }));

        addRenderableWidget(new VoidButton(x, y + 28, 168, 18,
                Component.translatable("spotifysync.settings.lyrics_mode")
                        .append(Component.literal(": "))
                        .append(Component.translatable(config.lyricsMode.translationKey())),
                VoidButton.Style.SECONDARY,
                () -> {
                    config.lyricsMode = config.lyricsMode.next();
                    config.save();
                    rebuildWidgets();
                }));

        addRenderableWidget(new VoidButton(x + 176, y + 28, contentWidth - 176, 18,
                Component.translatable("spotifysync.settings.retry_lyrics"), VoidButton.Style.UTILITY,
                () -> SpotifyManager.get().lyrics().retry(SpotifyManager.get().state())));

        addRenderableWidget(new VoidSlider(x, y + 54, contentWidth,
                Component.translatable("spotifysync.settings.lyrics_position"), 0.05, 0.95, 0.01,
                () -> config.lyricsHudY,
                value -> {
                    config.lyricsHudY = (float) value;
                    config.save();
                },
                value -> Math.round(value * 100) + "%"));

        addRenderableWidget(new VoidSlider(x, y + 86, (contentWidth - 10) / 2,
                Component.translatable("spotifysync.settings.lyrics_scale"), 0.5, 2.5, 0.05,
                () -> config.lyricsScale,
                value -> {
                    config.lyricsScale = (float) value;
                    config.save();
                },
                value -> String.format("%.2fx", value)));

        addRenderableWidget(new VoidSlider(x + (contentWidth + 10) / 2, y + 86, (contentWidth - 10) / 2,
                Component.translatable("spotifysync.settings.lyrics_context"), 0, 4, 1,
                () -> config.lyricsContextLines,
                value -> {
                    config.lyricsContextLines = (int) Math.round(value);
                    config.save();
                },
                value -> String.valueOf(Math.round(value))));

        addRenderableWidget(new VoidSlider(x, y + 118, contentWidth,
                Component.translatable("spotifysync.settings.lyrics_offset"), -5000, 5000, 50,
                () -> config.lyricsOffsetMs,
                value -> {
                    config.lyricsOffsetMs = (int) Math.round(value);
                    config.save();
                },
                value -> Math.round(value) + " ms"));
    }

    private void buildImmersive(SyncConfig config, int x, int y, int contentWidth) {
        addRenderableWidget(new VoidSlider(x, y, (contentWidth - 10) / 2,
                Component.translatable("spotifysync.settings.ring_radius"), 1.5, 12.0, 0.1,
                () -> config.ringRadius,
                value -> {
                    config.ringRadius = (float) value;
                    config.save();
                },
                value -> String.format("%.1f", value)));

        addRenderableWidget(new VoidSlider(x + (contentWidth + 10) / 2, y, (contentWidth - 10) / 2,
                Component.translatable("spotifysync.settings.ring_height"), -1.0, 6.0, 0.1,
                () -> config.ringHeight,
                value -> {
                    config.ringHeight = (float) value;
                    config.save();
                },
                value -> String.format("%.1f", value)));

        addRenderableWidget(new VoidSlider(x, y + 32, (contentWidth - 10) / 2,
                Component.translatable("spotifysync.settings.ring_scale"), 0.3, 3.0, 0.05,
                () -> config.ringScale,
                value -> {
                    config.ringScale = (float) value;
                    config.save();
                },
                value -> String.format("%.2fx", value)));

        addRenderableWidget(new VoidSlider(x + (contentWidth + 10) / 2, y + 32, (contentWidth - 10) / 2,
                Component.translatable("spotifysync.settings.ring_lines"), 1, 12, 1,
                () -> config.ringLineCount,
                value -> {
                    config.ringLineCount = (int) Math.round(value);
                    config.save();
                },
                value -> String.valueOf(Math.round(value))));

        addRenderableWidget(new VoidSlider(x, y + 64, (contentWidth - 10) / 2,
                Component.translatable("spotifysync.settings.ring_speed"), 0.0, 4.0, 0.05,
                () -> config.ringSpinSpeed,
                value -> {
                    config.ringSpinSpeed = (float) value;
                    config.save();
                },
                value -> String.format("%.2fx", value)));

        addRenderableWidget(new VoidSlider(x + (contentWidth + 10) / 2, y + 64, (contentWidth - 10) / 2,
                Component.translatable("spotifysync.settings.ring_opacity"), 0.1, 1.0, 0.05,
                () -> config.ringOpacity,
                value -> {
                    config.ringOpacity = (float) value;
                    config.save();
                },
                value -> Math.round(value * 100) + "%"));

        addRenderableWidget(new VoidToggle(x, y + 96, contentWidth,
                Component.translatable("spotifysync.settings.ring_spin"),
                "lines orbit slowly around the player",
                () -> config.ringSpin,
                value -> {
                    config.ringSpin = value;
                    config.save();
                }));

        addRenderableWidget(new VoidToggle(x, y + 122, contentWidth,
                Component.translatable("spotifysync.settings.ring_bob"),
                "soft vertical float",
                () -> config.ringBob,
                value -> {
                    config.ringBob = value;
                    config.save();
                }));

        addRenderableWidget(new VoidToggle(x, y + 148, contentWidth,
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

        UiRender.accentRule(graphics, cardX + PADDING, cardY + PADDING, 28, 1f);
        UiRender.textScaled(graphics, "SPOTIFY SYNC", cardX + PADDING, cardY + PADDING + 6, 1.35f,
                UiTheme.TEXT_PRIMARY, false);

        // Status line, mono register.
        SpotifyAuth.Status status = manager.auth().status();
        String statusLabel = switch (status) {
            case CONNECTED -> "connected";
            case WAITING_FOR_BROWSER -> "waiting for browser";
            case ERROR -> "error";
            default -> manager.connected() ? "session saved" : "disconnected";
        };
        int statusColor = switch (status) {
            case CONNECTED -> UiTheme.ACCENT_SECONDARY;
            case ERROR -> 0xFFFF5A5A;
            case WAITING_FOR_BROWSER -> UiTheme.accent();
            default -> UiTheme.TEXT_MUTED;
        };
        String statusText = statusLabel + (manager.auth().message().isEmpty()
                ? "" : " \u00b7 " + manager.auth().message());
        UiRender.textScaled(graphics,
                UiRender.ellipsize(statusText, (int) ((CARD_WIDTH - PADDING * 2) / 0.75f)),
                cardX + PADDING, cardY + PADDING + 22, 0.75f, statusColor, false);

        UiRender.hairline(graphics, cardX + PADDING, cardY + 58, CARD_WIDTH - PADDING * 2, 0.5f);

        if (tab == Tab.ACCOUNT) {
            UiRender.textScaled(graphics, "SPOTIFY CLIENT ID", cardX + PADDING, cardY + 66, 0.7f,
                    UiTheme.TEXT_SECONDARY, false);
        }

        UiRender.hairline(graphics, cardX + PADDING, cardY + CARD_HEIGHT - 40,
                CARD_WIDTH - PADDING * 2, 0.5f);
        UiRender.textScaled(graphics, "keys: K settings \u00b7 J player \u00b7 L lyrics mode",
                cardX + PADDING, cardY + CARD_HEIGHT - 34, 0.7f, UiTheme.TEXT_MUTED, false);
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
