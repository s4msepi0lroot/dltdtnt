package com.voidcanvas.spotifysync.client.screen;

import com.voidcanvas.spotifysync.client.render.UiRender;
import com.voidcanvas.spotifysync.client.render.UiTheme;
import com.voidcanvas.spotifysync.client.screen.widget.VoidButton;
import com.voidcanvas.spotifysync.client.screen.widget.VoidSlider;
import com.voidcanvas.spotifysync.config.SyncConfig;
import com.voidcanvas.spotifysync.config.ThemeColors;
import com.voidcanvas.spotifysync.config.ThemePreset;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Colour-scheme editor: presets plus per-token R/G/B sliders, opacities and
 * corner radius, with a live preview card on the right.
 *
 * <p>Any manual change flips the config to the {@code CUSTOM} preset so the
 * player's own palette is never overwritten by a preset name.</p>
 */
public final class ThemeEditorScreen extends Screen {

    private static final int SHELL_WIDTH = 470;
    private static final int SHELL_HEIGHT = 340;
    private static final int PADDING = 20;
    private static final int HEADER = 76;
    private static final int FOOTER = 44;
    private static final int ROW_GAP = 6;
    private static final int PREVIEW_WIDTH = 150;

    /** Colour tokens the player can edit. */
    private enum Token {
        ACCENT("spotifysync.token.accent"),
        ACCENT_SECONDARY("spotifysync.token.accent_secondary"),
        SHELL("spotifysync.token.shell"),
        SURFACE("spotifysync.token.surface"),
        TEXT_PRIMARY("spotifysync.token.text_primary"),
        TEXT_SECONDARY("spotifysync.token.text_secondary");

        final String key;

        Token(String key) {
            this.key = key;
        }
    }

    private static Token activeToken = Token.ACCENT;

    private final Screen parent;
    private final List<AbstractWidget> rows = new ArrayList<>();
    private final Map<AbstractWidget, Integer> rowY = new HashMap<>();

    private float shellScale = 1f;
    private int shellX;
    private int shellY;
    private int scroll;
    private int contentHeight;

    public ThemeEditorScreen(Screen parent) {
        super(Component.translatable("spotifysync.screen.theme"));
        this.parent = parent;
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

    private int columnWidth() {
        return SHELL_WIDTH - PADDING * 3 - PREVIEW_WIDTH;
    }

    private int viewportHeight() {
        return SHELL_HEIGHT - HEADER - FOOTER;
    }

    // ----------------------------------------------------------------- layout

    @Override
    protected void init() {
        computeShell();
        rows.clear();
        rowY.clear();

        SyncConfig config = SyncConfig.get();
        ThemeColors theme = config.theme;
        int column = columnWidth();
        int y = HEADER + 4;

        // Channel sliders for the selected token.
        y = addChannel(y, column, "R", 16, () -> channel(theme, 16), value -> setChannel(theme, 16, value));
        y = addChannel(y, column, "G", 8, () -> channel(theme, 8), value -> setChannel(theme, 8, value));
        y = addChannel(y, column, "B", 0, () -> channel(theme, 0), value -> setChannel(theme, 0, value));

        y = addRow(y, column, new VoidSlider(0, 0, column,
                Component.translatable("spotifysync.token.corner_radius"), 0, 16, 1,
                () -> theme.cornerRadius, value -> {
            theme.cornerRadius = (int) Math.round(value);
            theme.markCustom();
            config.save();
        }, value -> Math.round(value) + " px"));

        y = addRow(y, column, new VoidSlider(0, 0, column,
                Component.translatable("spotifysync.token.glass_opacity"), 0.0, 0.25, 0.01,
                () -> theme.glassOpacity, value -> {
            theme.glassOpacity = (float) value;
            theme.markCustom();
            config.save();
        }, value -> Math.round(value * 100) + "%"));

        y = addRow(y, column, new VoidSlider(0, 0, column,
                Component.translatable("spotifysync.token.border_opacity"), 0.0, 0.5, 0.01,
                () -> theme.borderOpacity, value -> {
            theme.borderOpacity = (float) value;
            theme.markCustom();
            config.save();
        }, value -> Math.round(value * 100) + "%"));

        y = addRow(y, column, new VoidSlider(0, 0, column,
                Component.translatable("spotifysync.token.grid_opacity"), 0.0, 0.6, 0.01,
                () -> theme.gridOpacity, value -> {
            theme.gridOpacity = (float) value;
            theme.markCustom();
            config.save();
        }, value -> Math.round(value * 100) + "%"));

        y = addRow(y, column, new VoidSlider(0, 0, column,
                Component.translatable("spotifysync.token.noise_opacity"), 0.0, 0.5, 0.01,
                () -> theme.noiseOpacity, value -> {
            theme.noiseOpacity = (float) value;
            theme.markCustom();
            config.save();
        }, value -> Math.round(value * 100) + "%"));

        y = addRow(y, column, new VoidSlider(0, 0, column,
                Component.translatable("spotifysync.token.glow_opacity"), 0.0, 1.0, 0.02,
                () -> theme.glowOpacity, value -> {
            theme.glowOpacity = (float) value;
            theme.markCustom();
            config.save();
        }, value -> Math.round(value * 100) + "%"));

        contentHeight = y - HEADER;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewportHeight() + 10)));

        // Token selector pills.
        Token[] tokens = Token.values();
        int pillWidth = (SHELL_WIDTH - PADDING * 2 - (tokens.length - 1) * 4) / tokens.length;
        for (int i = 0; i < tokens.length; i++) {
            Token token = tokens[i];
            addRenderableWidget(new VoidButton(sx(PADDING + i * (pillWidth + 4)), sy(48),
                    sw(pillWidth), sw(22), Component.translatable(token.key),
                    token == activeToken ? VoidButton.Style.PRIMARY : VoidButton.Style.GHOST, () -> {
                activeToken = token;
                rebuildWidgets();
            }));
        }

        // Preset cycle + reset + done.
        int footerY = SHELL_HEIGHT - PADDING - 24;
        int third = (SHELL_WIDTH - PADDING * 2 - 16) / 3;
        addRenderableWidget(new VoidButton(sx(PADDING), sy(footerY), sw(third), sw(24),
                Component.translatable(config.theme.presetOrCustom().translationKey()),
                VoidButton.Style.UTILITY, () -> {
            ThemePreset next = config.theme.presetOrCustom().next();
            if (next == ThemePreset.CUSTOM) {
                next = next.next();
            }
            next.applyTo(config.theme);
            config.save();
            rebuildWidgets();
        }).body());
        addRenderableWidget(new VoidButton(sx(PADDING + third + 8), sy(footerY), sw(third), sw(24),
                Component.translatable("spotifysync.action.reset_theme"),
                VoidButton.Style.DANGER, () -> {
            ThemePreset.OBSIDIAN_LIME.applyTo(config.theme);
            config.save();
            rebuildWidgets();
        }).body());
        addRenderableWidget(new VoidButton(sx(PADDING + (third + 8) * 2), sy(footerY), sw(third), sw(24),
                Component.translatable("spotifysync.action.done"),
                VoidButton.Style.PRIMARY, this::onClose).body());

        layoutRows();
    }

    private int addChannel(int y, int column, String label, int shift,
                           IntSupplier getter, IntConsumer setter) {
        VoidSlider slider = new VoidSlider(0, 0, column, Component.literal(label), 0, 255, 1,
                getter::getAsInt, value -> setter.accept((int) Math.round(value)),
                value -> String.valueOf(Math.round(value)));
        return addRow(y, column, slider);
    }

    private int addRow(int y, int column, AbstractWidget widget) {
        rows.add(widget);
        rowY.put(widget, y);
        addRenderableWidget(widget);
        return y + widget.getHeight() + ROW_GAP;
    }

    private void layoutRows() {
        int column = columnWidth();
        for (AbstractWidget widget : rows) {
            Integer local = rowY.get(widget);
            if (local == null) {
                continue;
            }
            int localY = local - scroll;
            widget.setX(sx(PADDING));
            widget.setY(sy(localY));
            widget.setWidth(sw(column));
            boolean visible = localY + widget.getHeight() > HEADER && localY < HEADER + viewportHeight();
            widget.visible = visible;
            widget.active = visible;
        }
    }

    // ------------------------------------------------------------- token access

    private int tokenValue(ThemeColors theme) {
        return switch (activeToken) {
            case ACCENT -> theme.accent;
            case ACCENT_SECONDARY -> theme.accentSecondary;
            case SHELL -> theme.shell;
            case SURFACE -> theme.surface;
            case TEXT_PRIMARY -> theme.textPrimary;
            case TEXT_SECONDARY -> theme.textSecondary;
        };
    }

    private void setTokenValue(ThemeColors theme, int rgb) {
        int value = rgb & 0xFFFFFF;
        switch (activeToken) {
            case ACCENT -> theme.accent = value;
            case ACCENT_SECONDARY -> theme.accentSecondary = value;
            case SHELL -> theme.shell = value;
            case SURFACE -> theme.surface = value;
            case TEXT_PRIMARY -> theme.textPrimary = value;
            case TEXT_SECONDARY -> theme.textSecondary = value;
        }
        theme.markCustom();
        SyncConfig.get().save();
    }

    private int channel(ThemeColors theme, int shift) {
        return (tokenValue(theme) >> shift) & 0xFF;
    }

    private void setChannel(ThemeColors theme, int shift, int value) {
        int clamped = Math.max(0, Math.min(255, value));
        int current = tokenValue(theme);
        int next = (current & ~(0xFF << shift)) | (clamped << shift);
        setTokenValue(theme, next);
    }

    // ----------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        computeShell();
        layoutRows();
        ThemeColors theme = SyncConfig.get().theme;

        graphics.fill(0, 0, width, height, UiTheme.scrim(0.72f));

        graphics.pose().pushPose();
        graphics.pose().translate(shellX, shellY, 0f);
        graphics.pose().scale(shellScale, shellScale, 1f);

        UiRender.shell(graphics, 0, 0, SHELL_WIDTH, SHELL_HEIGHT, 1f);
        UiRender.glowSphere(graphics, SHELL_WIDTH - 60, 40, 140, theme.accent, 0.4f);

        UiRender.accentCard(graphics, PADDING, PADDING - 4, 24, 24, 1f);
        UiRender.display(graphics, "C", PADDING + 8, PADDING + 2, 1.2f, UiTheme.onAccent());
        UiRender.display(graphics, Component.translatable("spotifysync.screen.theme").getString(),
                PADDING + 34, PADDING - 1, 1.3f, UiTheme.textPrimary(1f));
        UiRender.mono(graphics, hex(tokenValue(theme)), PADDING + 34, PADDING + 20, 0.8f,
                UiTheme.textMuted(1f));
        UiRender.monoRight(graphics, "THEME EDITOR", SHELL_WIDTH - PADDING, PADDING - 1, 0.8f,
                UiTheme.accent(1f));

        UiRender.hairline(graphics, PADDING, HEADER - 6, SHELL_WIDTH - PADDING * 2, 0.14f);
        UiRender.hairline(graphics, PADDING, SHELL_HEIGHT - FOOTER + 4, SHELL_WIDTH - PADDING * 2, 0.14f);

        // Live preview column.
        int previewX = SHELL_WIDTH - PADDING - PREVIEW_WIDTH;
        int previewY = HEADER + 4;
        int previewH = viewportHeight() - 8;
        UiRender.card(graphics, previewX, previewY, PREVIEW_WIDTH, previewH, 1f);
        UiRender.mono(graphics, "PREVIEW", previewX + 12, previewY + 10, 0.8f, UiTheme.textMuted(1f));
        UiRender.display(graphics, "Aa", previewX + 12, previewY + 24, 1.8f, UiTheme.textPrimary(1f));
        UiRender.text(graphics, "Secondary text", previewX + 12, previewY + 52,
                UiTheme.textSecondary(1f), false);
        UiRender.statusTag(graphics, "LIVE", previewX + 12, previewY + 68, theme.accent, 1f);
        UiRender.progressBar(graphics, previewX + 12, previewY + 88, PREVIEW_WIDTH - 24, 6, 0.62f, 1f, true);
        UiRender.glassTile(graphics, previewX + 12, previewY + 102, PREVIEW_WIDTH - 24, 34, 1f);
        UiRender.mono(graphics, "GLASS", previewX + 22, previewY + 116, 0.75f, UiTheme.textSecondary(1f));
        UiRender.accentCard(graphics, previewX + 12, previewY + 144, PREVIEW_WIDTH - 24, 30, 1f);
        UiRender.display(graphics, "ACCENT", previewX + 24, previewY + 152, 1.1f, UiTheme.onAccent());
        UiRender.equalizer(graphics, previewX + 12, previewY + 182, PREVIEW_WIDTH - 24, 18,
                UiTheme.accent(0.85f), true);

        // Swatch strip of every token.
        int swatchY = previewY + previewH - 26;
        int[] swatches = {theme.accent, theme.accentSecondary, theme.shell, theme.surface,
                theme.textPrimary, theme.textSecondary};
        int swatchWidth = (PREVIEW_WIDTH - 24) / swatches.length;
        for (int i = 0; i < swatches.length; i++) {
            UiRender.roundedRect(graphics, previewX + 12 + i * swatchWidth, swatchY,
                    swatchWidth - 2, 14, 3, UiTheme.opaque(swatches[i]));
        }

        // Scrollbar.
        if (contentHeight > viewportHeight()) {
            int trackX = PADDING + columnWidth() + 4;
            UiRender.pill(graphics, trackX, HEADER, 3, viewportHeight(), UiTheme.track());
            int thumb = Math.max(18, (int) (viewportHeight() * (viewportHeight() / (float) contentHeight)));
            int maxScroll = contentHeight - viewportHeight() + 10;
            int thumbY = HEADER + (int) ((viewportHeight() - thumb) * (scroll / (float) Math.max(1, maxScroll)));
            UiRender.pill(graphics, trackX, thumbY, 3, thumb, UiTheme.accent(0.9f));
        }

        graphics.pose().popPose();

        graphics.enableScissor(sx(0), sy(HEADER), sx(SHELL_WIDTH), sy(HEADER + viewportHeight()));
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

    private static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    // ------------------------------------------------------------- interaction

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (contentHeight > viewportHeight()) {
            int maxScroll = contentHeight - viewportHeight() + 10;
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (scrollY * 16)));
            layoutRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        SyncConfig.get().save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
