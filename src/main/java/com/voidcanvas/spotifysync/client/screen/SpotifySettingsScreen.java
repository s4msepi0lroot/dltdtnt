package com.voidcanvas.spotifysync.client.screen;

import com.voidcanvas.spotifysync.client.SpotifyManager;
import com.voidcanvas.spotifysync.client.render.UiRender;
import com.voidcanvas.spotifysync.client.render.UiTheme;
import com.voidcanvas.spotifysync.client.screen.widget.VoidButton;
import com.voidcanvas.spotifysync.client.screen.widget.VoidSlider;
import com.voidcanvas.spotifysync.client.screen.widget.VoidToggle;
import com.voidcanvas.spotifysync.config.HudAnchor;
import com.voidcanvas.spotifysync.config.LyricsMode;
import com.voidcanvas.spotifysync.config.PlaybackSource;
import com.voidcanvas.spotifysync.config.SyncConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings, laid out as a floating obsidian shell with a pill tab bar and a
 * scrollable single column of rows.
 *
 * <p>Every row gets a full-width slot and the list scrolls, so no control can
 * ever be squeezed or clipped, whatever the GUI scale is.</p>
 */
public final class SpotifySettingsScreen extends Screen {

    private static final int SHELL_WIDTH = 460;
    private static final int SHELL_HEIGHT = 340;
    private static final int PADDING = 22;
    private static final int ROW_GAP = 8;
    private static final int HEADER = 88;
    private static final int FOOTER = 46;

    /** Tabs of the settings shell. */
    private enum Tab {
        SOURCE("spotifysync.tab.source"),
        HUD("spotifysync.tab.hud"),
        LYRICS("spotifysync.tab.lyrics"),
        RING("spotifysync.tab.ring"),
        THEME("spotifysync.tab.theme");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private static Tab activeTab = Tab.SOURCE;

    private final List<AbstractWidget> rows = new ArrayList<>();

    private float shellScale = 1f;
    private int shellX;
    private int shellY;
    private int scroll;
    private int contentHeight;

    public SpotifySettingsScreen() {
        super(Component.translatable("spotifysync.screen.settings"));
    }

    // --------------------------------------------------------------- geometry

    private void computeShell() {
        float scaleX = (float) (width - 16) / SHELL_WIDTH;
        float scaleY = (float) (height - 16) / SHELL_HEIGHT;
        shellScale = Math.max(0.55f, Math.min(1f, Math.min(scaleX, scaleY)));
        shellX = (int) ((width - SHELL_WIDTH * shellScale) / 2f);
        shellY = (int) ((height - SHELL_HEIGHT * shellScale) / 2f);
    }

    private int sx(int localX) {
        return shellX + (int) (localX * shellScale);
    }

    private int sy(int localY) {
        return shellY + (int) (localY * shellScale);
    }

    private int sw(int localWidth) {
        return Math.max(1, (int) (localWidth * shellScale));
    }

    private int viewportTop() {
        return HEADER;
    }

    private int viewportHeight() {
        return SHELL_HEIGHT - HEADER - FOOTER;
    }

    // ----------------------------------------------------------------- layout

    @Override
    protected void init() {
        computeShell();
        rows.clear();
        SyncConfig config = SyncConfig.get();
        SpotifyManager manager = SpotifyManager.get();

        int rowWidth = SHELL_WIDTH - PADDING * 2;
        Cursor cursor = new Cursor(viewportTop() + 6);

        switch (activeTab) {
            case SOURCE -> {
                add(cursor, rowWidth, new VoidButton(0, 0, rowWidth, 28,
                        Component.translatable("spotifysync.option.playback_source")
                                .append(Component.literal(": "))
                                .append(Component.translatable(config.playbackSource.translationKey())),
                        VoidButton.Style.SECONDARY, () -> {
                    config.playbackSource = next(config.playbackSource);
                    config.save();
                    rebuildWidgets();
                }).body());
                add(cursor, rowWidth, new VoidToggle(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.auto_connect"),
                        "AUTO", () -> config.autoConnect, value -> {
                    config.autoConnect = value;
                    config.save();
                }));
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.local_poll"), 80, 1000, 10,
                        () -> config.localPollMs, value -> {
                    config.localPollMs = (int) Math.round(value);
                    config.save();
                    manager.localSource().stopWatcher();
                }, value -> Math.round(value) + " ms"));
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.poll_interval"), 700, 6000, 100,
                        () -> config.pollIntervalMs, value -> {
                    config.pollIntervalMs = (int) Math.round(value);
                    config.save();
                }, value -> Math.round(value) + " ms"));
                add(cursor, rowWidth, new VoidButton(0, 0, rowWidth, 28,
                        Component.translatable(manager.auth().isAuthorized()
                                ? "spotifysync.action.logout" : "spotifysync.action.login"),
                        VoidButton.Style.UTILITY, () -> {
                    if (manager.auth().isAuthorized()) {
                        manager.logout();
                    } else {
                        manager.beginLogin();
                    }
                    rebuildWidgets();
                }).body());
            }
            case HUD -> {
                add(cursor, rowWidth, new VoidToggle(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.hud_enabled"),
                        "HUD", () -> config.hudEnabled, value -> {
                    config.hudEnabled = value;
                    config.save();
                }));
                add(cursor, rowWidth, new VoidButton(0, 0, rowWidth, 28,
                        Component.translatable("spotifysync.option.hud_anchor")
                                .append(Component.literal(": "))
                                .append(Component.translatable(config.hudAnchor.translationKey())),
                        VoidButton.Style.SECONDARY, () -> {
                    config.hudAnchor = next(config.hudAnchor);
                    config.save();
                    rebuildWidgets();
                }).body());
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.hud_scale"), 0.6, 1.8, 0.05,
                        () -> config.hudScale, value -> {
                    config.hudScale = (float) value;
                    config.save();
                }, value -> String.format("%.2fx", value)));
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.hud_opacity"), 0.2, 1.0, 0.05,
                        () -> config.hudOpacity, value -> {
                    config.hudOpacity = (float) value;
                    config.save();
                }, value -> Math.round(value * 100) + "%"));
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.hud_offset_x"), -200, 200, 2,
                        () -> config.hudOffsetX, value -> {
                    config.hudOffsetX = (int) Math.round(value);
                    config.save();
                }, value -> String.valueOf(Math.round(value))));
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.hud_offset_y"), -200, 200, 2,
                        () -> config.hudOffsetY, value -> {
                    config.hudOffsetY = (int) Math.round(value);
                    config.save();
                }, value -> String.valueOf(Math.round(value))));
                add(cursor, rowWidth, new VoidToggle(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.show_cover"),
                        "ART", () -> config.showCover, value -> {
                    config.showCover = value;
                    config.save();
                }));
                add(cursor, rowWidth, new VoidToggle(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.show_on_screens"),
                        "GUI", () -> config.showOnScreens, value -> {
                    config.showOnScreens = value;
                    config.save();
                }));
                add(cursor, rowWidth, new VoidToggle(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.hide_when_idle"),
                        "IDLE", () -> config.hideWhenIdle, value -> {
                    config.hideWhenIdle = value;
                    config.save();
                }));
            }
            case LYRICS -> {
                add(cursor, rowWidth, new VoidToggle(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.lyrics_enabled"),
                        "SYNC", () -> config.lyricsEnabled, value -> {
                    config.lyricsEnabled = value;
                    config.save();
                }));
                add(cursor, rowWidth, new VoidButton(0, 0, rowWidth, 28,
                        Component.translatable("spotifysync.option.lyrics_mode")
                                .append(Component.literal(": "))
                                .append(Component.translatable(config.lyricsMode.translationKey())),
                        VoidButton.Style.SECONDARY, () -> {
                    config.lyricsMode = config.lyricsMode.next();
                    config.save();
                    rebuildWidgets();
                }).body());
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.lyrics_lead"), -1000, 1000, 20,
                        () -> config.lyricsLeadMs, value -> {
                    config.lyricsLeadMs = (int) Math.round(value);
                    config.save();
                }, value -> Math.round(value) + " ms"));
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.lyrics_offset"), -3000, 3000, 50,
                        () -> config.lyricsOffsetMs, value -> {
                    config.lyricsOffsetMs = (int) Math.round(value);
                    config.save();
                }, value -> Math.round(value) + " ms"));
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.lyrics_scale"), 0.6, 2.0, 0.05,
                        () -> config.lyricsScale, value -> {
                    config.lyricsScale = (float) value;
                    config.save();
                }, value -> String.format("%.2fx", value)));
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.lyrics_y"), 0.2, 0.95, 0.01,
                        () -> config.lyricsHudY, value -> {
                    config.lyricsHudY = (float) value;
                    config.save();
                }, value -> Math.round(value * 100) + "%"));
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.lyrics_context"), 0, 3, 1,
                        () -> config.lyricsContextLines, value -> {
                    config.lyricsContextLines = (int) Math.round(value);
                    config.save();
                }, value -> String.valueOf(Math.round(value))));
                add(cursor, rowWidth, new VoidToggle(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.lyrics_on_screens"),
                        "GUI", () -> config.lyricsShowOnScreens, value -> {
                    config.lyricsShowOnScreens = value;
                    config.save();
                }));
            }
            case RING -> {
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.ring_radius"), 1.5, 8.0, 0.1,
                        () -> config.ringRadius, value -> {
                    config.ringRadius = (float) value;
                    config.save();
                }, value -> String.format("%.1f", value)));
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.ring_height"), 0.5, 4.0, 0.1,
                        () -> config.ringHeight, value -> {
                    config.ringHeight = (float) value;
                    config.save();
                }, value -> String.format("%.1f", value)));
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.ring_scale"), 0.5, 2.5, 0.05,
                        () -> config.ringScale, value -> {
                    config.ringScale = (float) value;
                    config.save();
                }, value -> String.format("%.2fx", value)));
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.ring_lines"), 1, 9, 1,
                        () -> config.ringLineCount, value -> {
                    config.ringLineCount = (int) Math.round(value);
                    config.save();
                }, value -> String.valueOf(Math.round(value))));
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.ring_opacity"), 0.2, 1.0, 0.05,
                        () -> config.ringOpacity, value -> {
                    config.ringOpacity = (float) value;
                    config.save();
                }, value -> Math.round(value * 100) + "%"));
                add(cursor, rowWidth, new VoidToggle(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.ring_spin"),
                        "SPIN", () -> config.ringSpin, value -> {
                    config.ringSpin = value;
                    config.save();
                }));
                add(cursor, rowWidth, new VoidToggle(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.ring_bob"),
                        "FLOAT", () -> config.ringBob, value -> {
                    config.ringBob = value;
                    config.save();
                }));
                add(cursor, rowWidth, new VoidToggle(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.ring_see_through"),
                        "XRAY", () -> config.ringSeeThrough, value -> {
                    config.ringSeeThrough = value;
                    config.save();
                }));
            }
            case THEME -> {
                add(cursor, rowWidth, new VoidButton(0, 0, rowWidth, 28,
                        Component.translatable("spotifysync.option.theme_preset")
                                .append(Component.literal(": "))
                                .append(Component.translatable(config.theme.presetOrCustom().translationKey())),
                        VoidButton.Style.SECONDARY, () -> {
                    config.theme.presetOrCustom().next().applyTo(config.theme);
                    config.save();
                    rebuildWidgets();
                }).body());
                add(cursor, rowWidth, new VoidButton(0, 0, rowWidth, 28,
                        Component.translatable("spotifysync.action.open_theme_editor"),
                        VoidButton.Style.PRIMARY,
                        () -> minecraft.setScreen(new ThemeEditorScreen(this))).body());
                add(cursor, rowWidth, new VoidSlider(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.ui_scale"), 0.0, 1.6, 0.05,
                        () -> config.uiScale, value -> {
                    config.uiScale = (float) value;
                    config.save();
                }, value -> value < 0.05 ? "AUTO" : String.format("%.2fx", value)));
                add(cursor, rowWidth, new VoidToggle(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.grid_pattern"),
                        "GRID", () -> config.theme.gridPattern, value -> {
                    config.theme.gridPattern = value;
                    config.theme.markCustom();
                    config.save();
                }));
                add(cursor, rowWidth, new VoidToggle(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.noise_overlay"),
                        "NOISE", () -> config.theme.noiseOverlay, value -> {
                    config.theme.noiseOverlay = value;
                    config.theme.markCustom();
                    config.save();
                }));
                add(cursor, rowWidth, new VoidToggle(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.glow_spheres"),
                        "GLOW", () -> config.theme.glowSpheres, value -> {
                    config.theme.glowSpheres = value;
                    config.theme.markCustom();
                    config.save();
                }));
                add(cursor, rowWidth, new VoidToggle(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.pill_buttons"),
                        "PILL", () -> config.theme.pillButtons, value -> {
                    config.theme.pillButtons = value;
                    config.theme.markCustom();
                    config.save();
                }));
                add(cursor, rowWidth, new VoidToggle(0, 0, rowWidth,
                        Component.translatable("spotifysync.option.grain"),
                        "FILM", () -> config.grain, value -> {
                    config.grain = value;
                    config.save();
                }));
            }
        }

        contentHeight = cursor.y - viewportTop();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewportHeight() + 12)));

        // Tab bar.
        Tab[] tabs = Tab.values();
        int tabWidth = (SHELL_WIDTH - PADDING * 2 - (tabs.length - 1) * 6) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            VoidButton button = new VoidButton(sx(PADDING + i * (tabWidth + 6)), sy(54), sw(tabWidth), sw(24),
                    Component.translatable(tab.key),
                    tab == activeTab ? VoidButton.Style.PRIMARY : VoidButton.Style.GHOST, () -> {
                activeTab = tab;
                scroll = 0;
                rebuildWidgets();
            });
            addRenderableWidget(button);
        }

        // Footer.
        int footerY = SHELL_HEIGHT - PADDING - 26;
        int half = (SHELL_WIDTH - PADDING * 2 - 10) / 2;
        addRenderableWidget(new VoidButton(sx(PADDING), sy(footerY), sw(half), sw(26),
                Component.translatable("spotifysync.action.open_player"),
                VoidButton.Style.SECONDARY,
                () -> minecraft.setScreen(new ExpandedPlayerScreen())).body());
        addRenderableWidget(new VoidButton(sx(PADDING + half + 10), sy(footerY), sw(half), sw(26),
                Component.translatable("spotifysync.action.done"),
                VoidButton.Style.PRIMARY, this::onClose).body());

        layoutRows();
    }

    /** Applies the current scroll offset to the row widgets. */
    private void layoutRows() {
        for (AbstractWidget widget : rows) {
            RowSlot slot = slots.get(widget);
            if (slot == null) {
                continue;
            }
            int localY = slot.y - scroll;
            widget.setX(sx(PADDING));
            widget.setY(sy(localY));
            widget.setWidth(sw(slot.width));
            boolean visible = localY + slot.height > viewportTop() && localY < viewportTop() + viewportHeight();
            widget.visible = visible;
            widget.active = visible;
        }
    }

    private final java.util.Map<AbstractWidget, RowSlot> slots = new java.util.HashMap<>();

    private record RowSlot(int y, int width, int height) {
    }

    private static final class Cursor {
        int y;

        Cursor(int y) {
            this.y = y;
        }
    }

    private void add(Cursor cursor, int rowWidth, AbstractWidget widget) {
        int rowHeight = widget.getHeight() > 0 ? widget.getHeight() : 28;
        slots.put(widget, new RowSlot(cursor.y, rowWidth, rowHeight));
        rows.add(widget);
        addRenderableWidget(widget);
        cursor.y += rowHeight + ROW_GAP;
    }

    private static <T extends Enum<T>> T next(T value) {
        T[] values = value.getDeclaringClass().getEnumConstants();
        return values[(value.ordinal() + 1) % values.length];
    }

    // ----------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        computeShell();
        layoutRows();
        SpotifyManager manager = SpotifyManager.get();

        graphics.fill(0, 0, width, height, UiTheme.scrim(0.72f));

        graphics.pose().pushPose();
        graphics.pose().translate(shellX, shellY, 0f);
        graphics.pose().scale(shellScale, shellScale, 1f);

        UiRender.shell(graphics, 0, 0, SHELL_WIDTH, SHELL_HEIGHT, 1f);
        UiRender.glowSphere(graphics, SHELL_WIDTH - 50, 30, 140, UiTheme.colors().accent, 0.38f);

        // Header: lime logo box, display title, mono status tag.
        UiRender.accentCard(graphics, PADDING, PADDING - 4, 26, 26, 1f);
        UiRender.display(graphics, "S", PADDING + 9, PADDING + 3, 1.3f, UiTheme.onAccent());
        UiRender.display(graphics, "Spotify Sync", PADDING + 36, PADDING + 1, 1.35f, UiTheme.textPrimary(1f));
        UiRender.statusTag(graphics, manager.sourceStatus(), PADDING + 36, PADDING + 22,
                manager.connected() ? UiTheme.colors().accent : UiTheme.colors().danger, 1f);

        UiRender.hairline(graphics, PADDING, HEADER - 8, SHELL_WIDTH - PADDING * 2, 0.14f);
        UiRender.hairline(graphics, PADDING, SHELL_HEIGHT - FOOTER + 6, SHELL_WIDTH - PADDING * 2, 0.14f);

        // Scrollbar.
        int viewport = viewportHeight();
        if (contentHeight > viewport) {
            int trackX = SHELL_WIDTH - PADDING + 8;
            UiRender.pill(graphics, trackX, viewportTop(), 3, viewport, UiTheme.track());
            int thumb = Math.max(20, (int) (viewport * (viewport / (float) contentHeight)));
            int maxScroll = contentHeight - viewport + 12;
            int thumbY = viewportTop() + (int) ((viewport - thumb) * (scroll / (float) Math.max(1, maxScroll)));
            UiRender.pill(graphics, trackX, thumbY, 3, thumb, UiTheme.accent(0.9f));
        }

        graphics.pose().popPose();

        // Clip the rows to the viewport so scrolled content never bleeds out.
        int clipTop = sy(viewportTop());
        int clipBottom = sy(viewportTop() + viewport);
        graphics.enableScissor(sx(0), clipTop, sx(SHELL_WIDTH), clipBottom);
        for (AbstractWidget widget : rows) {
            if (widget.visible) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        graphics.disableScissor();

        for (var renderable : renderables) {
            if (renderable instanceof AbstractWidget widget && rows.contains(widget)) {
                continue;
            }
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Custom background is drawn in render().
    }

    // ------------------------------------------------------------- interaction

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int viewport = viewportHeight();
        if (contentHeight > viewport) {
            double localY = (mouseY - shellY) / shellScale;
            if (localY >= viewportTop() && localY <= viewportTop() + viewport) {
                int maxScroll = contentHeight - viewport + 12;
                scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (scrollY * 18)));
                layoutRows();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        SyncConfig.get().save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
