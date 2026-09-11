package com.voidcanvas.spotifysync.client.screen.widget;

import com.voidcanvas.spotifysync.client.render.UiRender;
import com.voidcanvas.spotifysync.client.render.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

/** Compact slider: label + value readout above a 2px accent track. */
public class VoidSlider extends AbstractWidget {

    private final double min;
    private final double max;
    private final double step;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final Function<Double, String> formatter;
    private float hoverAnimation;

    public VoidSlider(int x, int y, int width, Component label,
                      double min, double max, double step,
                      DoubleSupplier getter, DoubleConsumer setter,
                      Function<Double, String> formatter) {
        super(x, y, width, 26, label);
        this.min = min;
        this.max = max;
        this.step = step;
        this.getter = getter;
        this.setter = setter;
        this.formatter = formatter;
    }

    private int trackY() {
        return getY() + 18;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoverAnimation += ((isHovered() ? 1f : 0f) - hoverAnimation) * 0.25f;
        double value = getter.getAsDouble();
        float fraction = (float) ((value - min) / (max - min));

        UiRender.text(graphics, getMessage().getString(), getX(), getY() + 2, UiTheme.TEXT_PRIMARY, false);
        String readout = formatter == null ? String.format("%.2f", value) : formatter.apply(value);
        UiRender.textRight(graphics, readout, getX() + width, getY() + 2,
                UiTheme.lerpColor(UiTheme.TEXT_SECONDARY, UiTheme.accent(), hoverAnimation), false);

        int trackHeight = 3;
        UiRender.roundedRect(graphics, getX(), trackY(), width, trackHeight, 1, UiTheme.TRACK);
        int filled = (int) (width * Math.max(0f, Math.min(1f, fraction)));
        if (filled > 0) {
            UiRender.roundedRect(graphics, getX(), trackY(), filled, trackHeight, 1, UiTheme.accent());
        }
        int knobX = getX() + Math.max(0, Math.min(width - 4, filled - 2));
        UiRender.roundedRect(graphics, knobX, trackY() - 3, 4, trackHeight + 6, 2,
                UiTheme.lerpColor(0xFFDEDEDE, 0xFFFFFFFF, hoverAnimation));
    }

    private void applyFromMouse(double mouseX) {
        double fraction = (mouseX - getX()) / (double) width;
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        double value = min + fraction * (max - min);
        if (step > 0.0) {
            value = Math.round(value / step) * step;
        }
        setter.accept(Math.max(min, Math.min(max, value)));
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        applyFromMouse(mouseX);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        applyFromMouse(mouseX);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
