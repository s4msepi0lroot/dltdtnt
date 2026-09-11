package com.voidcanvas.spotifysync.client.screen.widget;

import com.voidcanvas.spotifysync.client.render.UiRender;
import com.voidcanvas.spotifysync.client.render.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Button in the void-canvas language.
 *
 * <ul>
 *   <li>{@code PRIMARY} - near-white solid fill, black label (hero control)</li>
 *   <li>{@code SECONDARY} - pure black fill, white label, hairline border</li>
 *   <li>{@code UTILITY} - white fill, black label, sharper corners</li>
 *   <li>{@code GHOST} - transparent, secondary text, accent on hover</li>
 * </ul>
 */
public class VoidButton extends AbstractWidget {

    public enum Style {
        PRIMARY,
        SECONDARY,
        UTILITY,
        GHOST,
        DANGER
    }

    private final Runnable onPress;
    private final Style style;
    private float hoverAnimation;

    public VoidButton(int x, int y, int width, int height, Component message, Style style, Runnable onPress) {
        super(x, y, width, height, message);
        this.style = style;
        this.onPress = onPress;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered() && active;
        hoverAnimation += ((hovered ? 1f : 0f) - hoverAnimation) * 0.25f;
        float alpha = active ? 1f : 0.4f;
        // The design system fades controls to ~0.64 opacity on hover.
        float fillAlpha = alpha * (1f - 0.36f * hoverAnimation);

        int radius = style == Style.UTILITY ? UiTheme.RADIUS_CONTROL : UiTheme.RADIUS_CARD;
        int background;
        int labelColor;
        switch (style) {
            case PRIMARY -> {
                background = UiTheme.withAlpha(0xFFDEDEDE, fillAlpha);
                labelColor = UiTheme.withAlpha(0xFF000000, alpha);
            }
            case UTILITY -> {
                background = UiTheme.withAlpha(0xFFFFFFFF, fillAlpha);
                labelColor = UiTheme.withAlpha(0xFF000000, alpha);
            }
            case DANGER -> {
                background = UiTheme.withAlpha(0xFF1E1E1E, fillAlpha);
                labelColor = UiTheme.withAlpha(0xFFFF5A5A, alpha);
            }
            case GHOST -> {
                background = 0;
                labelColor = UiTheme.lerpColor(UiTheme.TEXT_SECONDARY, UiTheme.accent(), hoverAnimation);
            }
            default -> {
                background = UiTheme.withAlpha(UiTheme.BACKGROUND, fillAlpha);
                labelColor = UiTheme.withAlpha(UiTheme.TEXT_PRIMARY, alpha);
            }
        }

        if (background != 0) {
            UiRender.roundedRect(graphics, getX(), getY(), width, height, radius, background);
        }
        if (style == Style.SECONDARY || style == Style.GHOST || style == Style.DANGER) {
            int borderColor = hovered
                    ? UiTheme.accent(0.65f * alpha)
                    : UiTheme.withAlpha(UiTheme.BORDER, alpha);
            UiRender.roundedBorder(graphics, getX(), getY(), width, height, radius, borderColor);
        }

        String label = UiRender.ellipsize(getMessage().getString(), width - 10);
        UiRender.textCentered(graphics, label, getX() + width / 2,
                getY() + (height - 8) / 2 + 1, labelColor, false);
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
