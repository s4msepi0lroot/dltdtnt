package com.voidcanvas.spotifysync.client.screen.widget;

import com.voidcanvas.spotifysync.client.render.UiRender;
import com.voidcanvas.spotifysync.client.render.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Settings row: label on the left, small pill switch on the right. */
public class VoidToggle extends AbstractWidget {

    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private final String description;
    private float knobAnimation;
    private float hoverAnimation;

    public VoidToggle(int x, int y, int width, Component label, String description,
                      BooleanSupplier getter, Consumer<Boolean> setter) {
        super(x, y, width, 24, label);
        this.getter = getter;
        this.setter = setter;
        this.description = description;
        this.knobAnimation = getter.getAsBoolean() ? 1f : 0f;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean value = getter.getAsBoolean();
        knobAnimation += ((value ? 1f : 0f) - knobAnimation) * 0.28f;
        hoverAnimation += ((isHovered() ? 1f : 0f) - hoverAnimation) * 0.25f;

        if (hoverAnimation > 0.02f) {
            UiRender.roundedRect(graphics, getX() - 4, getY(), width + 8, height, UiTheme.RADIUS_CONTROL,
                    UiTheme.withAlpha(UiTheme.SURFACE, hoverAnimation * 0.85f));
        }

        int labelColor = UiTheme.lerpColor(UiTheme.TEXT_PRIMARY, UiTheme.TEXT_PRIMARY, hoverAnimation);
        UiRender.text(graphics, UiRender.ellipsize(getMessage().getString(), width - 70),
                getX(), getY() + 4, labelColor, false);
        if (description != null && !description.isEmpty()) {
            UiRender.textScaled(graphics, UiRender.ellipsize(description, (int) ((width - 70) / 0.75f)),
                    getX(), getY() + 14, 0.75f, UiTheme.TEXT_MUTED, false);
        }

        int switchWidth = 26;
        int switchHeight = 12;
        int switchX = getX() + width - switchWidth;
        int switchY = getY() + (height - switchHeight) / 2;
        int trackColor = UiTheme.lerpColor(UiTheme.TRACK, UiTheme.accent(0.85f), knobAnimation);
        UiRender.roundedRect(graphics, switchX, switchY, switchWidth, switchHeight, switchHeight / 2, trackColor);
        UiRender.roundedBorder(graphics, switchX, switchY, switchWidth, switchHeight, switchHeight / 2,
                UiTheme.withAlpha(UiTheme.BORDER, 0.8f));
        int knobSize = switchHeight - 4;
        int knobX = (int) (switchX + 2 + knobAnimation * (switchWidth - knobSize - 4));
        UiRender.roundedRect(graphics, knobX, switchY + 2, knobSize, knobSize, knobSize / 2, 0xFFFFFFFF);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        setter.accept(!getter.getAsBoolean());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
