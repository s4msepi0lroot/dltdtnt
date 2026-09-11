package com.voidcanvas.spotifysync.client.screen.widget;

import com.voidcanvas.spotifysync.client.render.UiRender;
import com.voidcanvas.spotifysync.client.render.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Button of the Obsidian and Lime system.
 *
 * <p>{@link Style#PRIMARY} is the "neon pulse" button from the design spec:
 * a lime pill with black bold text and a soft glowing shadow that grows on
 * hover. The other styles are the white pill, the glass utility pill, a plain
 * ghost label and the destructive variant.</p>
 */
public final class VoidButton extends AbstractButton {

    public enum Style {
        /** Lime pill, black text, neon glow. */
        PRIMARY,
        /** White pill, black text. */
        SECONDARY,
        /** Glass pill with a hairline ring. */
        UTILITY,
        /** Text only, no container. */
        GHOST,
        /** Destructive action. */
        DANGER
    }

    private final Style style;
    private final Runnable action;
    private float hover;
    private boolean mono = true;

    public VoidButton(int x, int y, int width, int height, Component label, Style style, Runnable action) {
        super(x, y, width, height, label);
        this.style = style;
        this.action = action;
    }

    /** Use sentence-case body text instead of a tracked-out mono label. */
    public VoidButton body() {
        this.mono = false;
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
        float alpha = active ? 1f : 0.4f;

        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        boolean pillShape = UiTheme.pills();
        int radius = pillShape ? height / 2 : UiTheme.radiusControl();

        int textColor;
        switch (style) {
            case PRIMARY -> {
                if (hover > 0.01f) {
                    UiRender.neonGlow(graphics, x, y, width, height, UiTheme.colors().accent, hover * alpha);
                }
                UiRender.roundedRect(graphics, x, y, width, height, radius, UiTheme.accent(alpha));
                UiRender.noise(graphics, x + 1, y + 1, width - 2, height - 2, alpha * 0.6f);
                textColor = UiTheme.withAlpha(UiTheme.onAccent(), alpha);
            }
            case SECONDARY -> {
                int base = UiTheme.argb(0xFFFFFF, (0.88f + 0.12f * hover) * alpha);
                UiRender.roundedRect(graphics, x, y, width, height, radius, base);
                textColor = UiTheme.withAlpha(UiTheme.opaque(0x000000), alpha);
            }
            case DANGER -> {
                UiRender.roundedRect(graphics, x, y, width, height, radius,
                        UiTheme.argb(UiTheme.colors().danger, (0.16f + 0.22f * hover) * alpha));
                UiRender.roundedBorder(graphics, x, y, width, height, radius,
                        UiTheme.argb(UiTheme.colors().danger, (0.55f + 0.45f * hover) * alpha));
                textColor = UiTheme.argb(UiTheme.colors().danger, alpha);
            }
            case GHOST -> textColor = hovered
                    ? UiTheme.accent(alpha)
                    : UiTheme.textSecondary(alpha);
            default -> {
                UiRender.roundedRect(graphics, x, y, width, height, radius, UiTheme.scrim(alpha * 0.8f));
                UiRender.roundedRect(graphics, x, y, width, height, radius,
                        UiTheme.glass(alpha * (1f + hover)));
                UiRender.roundedBorder(graphics, x, y, width, height, radius,
                        hover > 0.5f ? UiTheme.accent(0.45f * alpha) : UiTheme.ring(alpha));
                textColor = hovered ? UiTheme.textPrimary(alpha) : UiTheme.textSecondary(alpha);
            }
        }

        String label = getMessage().getString();
        float centerX = x + width / 2f;
        if (mono) {
            float scale = 0.85f;
            float available = width - 12f;
            while (scale > 0.55f && UiRender.monoWidth(label, scale) > available) {
                scale -= 0.05f;
            }
            float textY = y + (height - 8f * scale) / 2f;
            UiRender.monoCentered(graphics, label, centerX, textY, scale, textColor);
        } else {
            String fitted = UiRender.ellipsize(label, width - 12);
            UiRender.textCentered(graphics, fitted, (int) centerX, y + (height - 8) / 2, textColor, false);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
