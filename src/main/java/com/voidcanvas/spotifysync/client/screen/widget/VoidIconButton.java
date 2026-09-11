package com.voidcanvas.spotifysync.client.screen.widget;

import com.voidcanvas.spotifysync.client.render.UiRender;
import com.voidcanvas.spotifysync.client.render.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Round transport / utility button drawing a vector icon. */
public final class VoidIconButton extends AbstractWidget {

    public enum Icon {
        PLAY, PAUSE, NEXT, PREVIOUS, SHUFFLE, REPEAT, REPEAT_ONE, CLOSE, CHEVRON_DOWN, CHEVRON_UP, SPOTIFY
    }

    private final Runnable action;
    private Icon icon;
    private boolean highlighted;
    private boolean filled;
    private float hover;

    public VoidIconButton(int x, int y, int size, Icon icon, Component narration, Runnable action) {
        super(x, y, size, size, narration);
        this.icon = icon;
        this.action = action;
    }

    public VoidIconButton setIcon(Icon icon) {
        this.icon = icon;
        return this;
    }

    public VoidIconButton setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
        return this;
    }

    /** Solid lime treatment used for the main play / pause control. */
    public VoidIconButton filled() {
        this.filled = true;
        return this;
    }

    @Override
    public void onPress() {
        if (action != null) {
            action.run();
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered() && active;
        hover = UiRender.ease(hover, hovered ? 1f : 0f, 0.25f);
        float alpha = active ? 1f : 0.35f;

        int x = getX();
        int y = getY();
        int size = getWidth();
        int radius = size / 2;

        int iconColor;
        if (filled) {
            if (hover > 0.01f) {
                UiRender.neonGlow(graphics, x, y, size, getHeight(), UiTheme.colors().accent, hover * alpha);
            }
            UiRender.roundedRect(graphics, x, y, size, getHeight(), radius, UiTheme.accent(alpha));
            iconColor = UiTheme.withAlpha(UiTheme.onAccent(), alpha);
        } else {
            UiRender.roundedRect(graphics, x, y, size, getHeight(), radius,
                    UiTheme.glass(alpha * (0.9f + hover)));
            UiRender.roundedBorder(graphics, x, y, size, getHeight(), radius,
                    highlighted ? UiTheme.accent(0.6f * alpha)
                            : hover > 0.5f ? UiTheme.accent(0.4f * alpha) : UiTheme.ring(alpha));
            iconColor = highlighted
                    ? UiTheme.accent(alpha)
                    : hovered ? UiTheme.textPrimary(alpha) : UiTheme.textSecondary(alpha);
        }

        int glyph = Math.max(6, size - 12);
        int gx = x + (size - glyph) / 2;
        int gy = y + (getHeight() - glyph) / 2;
        switch (icon) {
            case PLAY -> UiRender.playIcon(graphics, gx + 1, gy, glyph, iconColor);
            case PAUSE -> UiRender.pauseIcon(graphics, gx, gy, glyph, iconColor);
            case NEXT -> UiRender.nextIcon(graphics, gx, gy, glyph, iconColor);
            case PREVIOUS -> UiRender.previousIcon(graphics, gx, gy, glyph, iconColor);
            case SHUFFLE -> UiRender.shuffleIcon(graphics, gx, gy, glyph, iconColor);
            case REPEAT -> UiRender.repeatIcon(graphics, gx, gy, glyph, iconColor);
            case REPEAT_ONE -> {
                UiRender.repeatIcon(graphics, gx, gy, glyph, iconColor);
                graphics.fill(gx + glyph / 2, gy + glyph / 2 - 1, gx + glyph / 2 + 1, gy + glyph / 2 + 2, iconColor);
            }
            case CLOSE -> UiRender.closeIcon(graphics, gx, gy, glyph, iconColor);
            case CHEVRON_DOWN -> UiRender.chevronIcon(graphics, gx, gy, glyph, iconColor, false);
            case CHEVRON_UP -> UiRender.chevronIcon(graphics, gx, gy, glyph, iconColor, true);
            case SPOTIFY -> {
                UiRender.roundedBorder(graphics, gx, gy, glyph, glyph, glyph / 2, iconColor);
                graphics.fill(gx + 2, gy + glyph / 2 - 2, gx + glyph - 2, gy + glyph / 2 - 1, iconColor);
                graphics.fill(gx + 3, gy + glyph / 2 + 1, gx + glyph - 3, gy + glyph / 2 + 2, iconColor);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
