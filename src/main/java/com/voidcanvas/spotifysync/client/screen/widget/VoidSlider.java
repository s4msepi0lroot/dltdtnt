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

/**
 * Slider with a mono label, a value readout and a lime track.
 *
 * <p>The widget keeps its own value while the knob is dragged and for a short
 * settle window afterwards. That matters for remote values such as the Spotify
 * volume: the player only reports the new level a few hundred milliseconds
 * after the command lands, and re-reading the stale value every frame used to
 * snap the knob back so the slider looked broken.</p>
 */
public final class VoidSlider extends AbstractWidget {

    public static final int HEIGHT = 30;
    private static final long SETTLE_NANOS = 900_000_000L;

    private final double min;
    private final double max;
    private final double step;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final Function<Double, String> formatter;

    private double value;
    private boolean dragging;
    private long settleUntilNano;
    private float hover;

    public VoidSlider(int x, int y, int width, Component label,
                      double min, double max, double step,
                      DoubleSupplier getter, DoubleConsumer setter,
                      Function<Double, String> formatter) {
        super(x, y, width, HEIGHT, label);
        this.min = min;
        this.max = max;
        this.step = step;
        this.getter = getter;
        this.setter = setter;
        this.formatter = formatter;
        this.value = clamp(getter.getAsDouble());
    }

    private double clamp(double raw) {
        return Math.max(min, Math.min(max, raw));
    }

    private double snap(double raw) {
        if (step <= 0.0) {
            return clamp(raw);
        }
        double snapped = min + Math.round((raw - min) / step) * step;
        return clamp(Math.round(snapped * 1_000_000.0) / 1_000_000.0);
    }

    /** Current value, taking the drag latch into account. */
    public double value() {
        if (dragging || System.nanoTime() < settleUntilNano) {
            return value;
        }
        value = clamp(getter.getAsDouble());
        return value;
    }

    private int trackY() {
        return getY() + 20;
    }

    private void applyFromMouse(double mouseX) {
        double fraction = (mouseX - (getX() + 2)) / Math.max(1, getWidth() - 4);
        double next = snap(min + fraction * (max - min));
        if (next != value) {
            value = next;
            setter.accept(next);
        }
        settleUntilNano = System.nanoTime() + SETTLE_NANOS;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        dragging = true;
        applyFromMouse(mouseX);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        dragging = true;
        applyFromMouse(mouseX);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        if (dragging) {
            dragging = false;
            settleUntilNano = System.nanoTime() + SETTLE_NANOS;
            setter.accept(value);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isHovered() || !active) {
            return false;
        }
        double delta = (step > 0.0 ? step : (max - min) / 20.0) * Math.signum(scrollY);
        double next = snap(value() + delta);
        if (next != value) {
            value = next;
            setter.accept(next);
        }
        settleUntilNano = System.nanoTime() + SETTLE_NANOS;
        return true;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        double current = value();
        boolean hovered = isHovered() && active;
        hover = UiRender.ease(hover, hovered || dragging ? 1f : 0f, 0.25f);
        float alpha = active ? 1f : 0.4f;

        int x = getX();
        int y = getY();
        int width = getWidth();

        String label = getMessage().getString();
        UiRender.mono(graphics, label, x, y, 0.8f, UiTheme.textSecondary(alpha));

        String readout = formatter != null ? formatter.apply(current) : String.valueOf(current);
        UiRender.monoRight(graphics, readout, x + width, y, 0.8f,
                hover > 0.3f ? UiTheme.accent(alpha) : UiTheme.textPrimary(alpha));

        int trackHeight = 4;
        int ty = trackY();
        UiRender.pill(graphics, x, ty, width, trackHeight, UiTheme.withAlpha(UiTheme.track(), alpha));

        float fraction = (float) ((current - min) / Math.max(1.0e-6, max - min));
        int filled = Math.max(0, Math.min(width, (int) (width * fraction)));
        if (filled > 0) {
            UiRender.pill(graphics, x, ty, filled, trackHeight, UiTheme.accent(alpha));
        }

        int knobSize = 10;
        int knobX = Math.max(x, Math.min(x + width - knobSize, x + filled - knobSize / 2));
        int knobY = ty + trackHeight / 2 - knobSize / 2;
        if (hover > 0.01f) {
            UiRender.neonGlow(graphics, knobX, knobY, knobSize, knobSize, UiTheme.colors().accent, hover * alpha);
        }
        UiRender.roundedRect(graphics, knobX, knobY, knobSize, knobSize, knobSize / 2,
                UiTheme.withAlpha(UiTheme.opaque(0xFFFFFF), alpha));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
