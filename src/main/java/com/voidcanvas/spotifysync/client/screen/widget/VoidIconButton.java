package com.voidcanvas.spotifysync.client.screen.widget;

import com.voidcanvas.spotifysync.client.render.UiRender;
import com.voidcanvas.spotifysync.client.render.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Circular-ish transport control drawing one of the vector icons. */
public class VoidIconButton extends AbstractWidget {

    public enum Icon {
        PLAY,
        PAUSE,
        NEXT,
        PREVIOUS,
        SHUFFLE,
        REPEAT,
        REPEAT_ONE,
        CLOSE,
        CHEVRON_DOWN
    }

    private final Runnable onPress;
    private Icon icon;
    private boolean highlighted;
    private boolean filled;
    private float hoverAnimation;

    public VoidIconButton(int x, int y, int size, Icon icon, Component narration, Runnable onPress) {
        super(x, y, size, size, narration);
        this.icon = icon;
        this.onPress = onPress;
    }

    public void setIcon(Icon icon) {
        this.icon = icon;
    }

    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
    }

    /** Filled buttons use the near-white hero treatment (play/pause). */
    public VoidIconButton filled() {
        this.filled = true;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered() && active;
        hoverAnimation += ((hovered ? 1f : 0f) - hoverAnimation) * 0.25f;

        int iconSize = Math.max(6, width / 2);
        int iconX = getX() + (width - iconSize) / 2;
        int iconY = getY() + (height - iconSize) / 2;

        int iconColor;
        if (filled) {
            int fill = UiTheme.lerpColor(0xFFDEDEDE, 0xFFFFFFFF, hoverAnimation);
            UiRender.roundedRect(graphics, getX(), getY(), width, height, width / 2, fill);
            iconColor = 0xFF000000;
        } else {
            if (hoverAnimation > 0.02f) {
                UiRender.roundedRect(graphics, getX(), getY(), width, height, width / 2,
                        UiTheme.withAlpha(UiTheme.SURFACE_ALT, hoverAnimation));
            }
            iconColor = highlighted
                    ? UiTheme.accent()
                    : UiTheme.lerpColor(UiTheme.TEXT_SECONDARY, UiTheme.TEXT_PRIMARY, hoverAnimation);
        }
        if (!active) {
            iconColor = UiTheme.withAlpha(iconColor, 0.4f);
        }

        switch (icon) {
            case PLAY -> UiRender.playIcon(graphics, iconX, iconY, iconSize, iconColor);
            case PAUSE -> UiRender.pauseIcon(graphics, iconX, iconY, iconSize, iconColor);
            case NEXT -> UiRender.nextIcon(graphics, iconX, iconY, iconSize, iconColor);
            case PREVIOUS -> UiRender.previousIcon(graphics, iconX, iconY, iconSize, iconColor);
            case SHUFFLE -> UiRender.shuffleIcon(graphics, iconX, iconY, iconSize, iconColor);
            case REPEAT -> UiRender.repeatIcon(graphics, iconX, iconY, iconSize, iconColor);
            case REPEAT_ONE -> {
                UiRender.repeatIcon(graphics, iconX, iconY, iconSize, iconColor);
                UiRender.textCentered(graphics, "1", getX() + width / 2, getY() + height / 2 - 3,
                        iconColor, false);
            }
            case CLOSE -> {
                for (int i = 0; i < iconSize; i++) {
                    graphics.fill(iconX + i, iconY + i, iconX + i + 1, iconY + i + 1, iconColor);
                    graphics.fill(iconX + iconSize - i - 1, iconY + i, iconX + iconSize - i, iconY + i + 1, iconColor);
                }
            }
            case CHEVRON_DOWN -> UiRender.chevronIcon(graphics, iconX, iconY, iconSize, iconColor, false);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (onPress != null) {
            onPress.run();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
