package com.voidcanvas.spotifysync.client.screen.widget;

import com.voidcanvas.spotifysync.client.render.UiRender;
import com.voidcanvas.spotifysync.client.render.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Glass row with a mono label on the left and a lime pill switch on the right.
 * The row is the click target, which makes it comfortable at any GUI scale.
 */
public final class VoidToggle extends AbstractWidget {

    public static final int HEIGHT = 26;

    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private final String description;
    private float knob;
    private float hover;

    public VoidToggle(int x, int y, int width, Component label, String description,
                      BooleanSupplier getter, Consumer<Boolean> setter) {
        super(x, y, width, HEIGHT, label);
        this.getter = getter;
        this.setter = setter;
        this.description = description;
        this.knob = getter.getAsBoolean() ? 1f : 0f;
    }

    @Override
    public void onPress() {
        boolean next = !getter.getAsBoolean();
        setter.accept(next);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean value = getter.getAsBoolean();
        boolean hovered = isHovered() && active;
        hover = UiRender.ease(hover, hovered ? 1f : 0f, 0.25f);
        knob = UiRender.ease(knob, value ? 1f : 0f, 0.3f);
        float alpha = active ? 1f : 0.45f;

        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();

        UiRender.roundedRect(graphics, x, y, width, height, UiTheme.radiusControl(),
                UiTheme.glass(alpha * (0.8f + hover * 0.9f)));
        UiRender.roundedBorder(graphics, x, y, width, height, UiTheme.radiusControl(),
                hover > 0.5f ? UiTheme.accent(0.4f * alpha) : UiTheme.ring(alpha));

        int switchWidth = 26;
        int switchHeight = 12;
        int switchX = x + width - switchWidth - 8;
        int switchY = y + (height - switchHeight) / 2;

        int labelWidth = switchX - x - 16;
        String label = getMessage().getString();
        UiRender.mono(graphics, fit(label, labelWidth, 0.8f), x + 8, y + (height - 7) / 2f, 0.8f,
                value ? UiTheme.textPrimary(alpha) : UiTheme.textSecondary(alpha));

        UiRender.pill(graphics, switchX, switchY, switchWidth, switchHeight,
                value ? UiTheme.accent(alpha * 0.9f) : UiTheme.withAlpha(UiTheme.track(), alpha));
        UiRender.pillBorder(graphics, switchX, switchY, switchWidth, switchHeight,
                value ? UiTheme.accent(alpha) : UiTheme.ring(alpha));

        int knobSize = switchHeight - 4;
        int knobX = (int) (switchX + 2 + knob * (switchWidth - knobSize - 4));
        UiRender.roundedRect(graphics, knobX, switchY + 2, knobSize, knobSize, knobSize / 2,
                value ? UiTheme.withAlpha(UiTheme.onAccent(), alpha) : UiTheme.textSecondary(alpha));
    }

    private static String fit(String value, int available, float scale) {
        if (available <= 0) {
            return "";
        }
        String text = value;
        while (text.length() > 1 && UiRender.monoWidth(text, scale) > available) {
            text = text.substring(0, text.length() - 1);
        }
        return text.equals(value) ? value : text;
    }

    /** Optional helper copy shown by the settings screen under the row. */
    public String description() {
        return description;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
