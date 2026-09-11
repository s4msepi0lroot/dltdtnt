package com.voidcanvas.spotifysync.config;

/** Screen anchor used to position the mini player. */
public enum HudAnchor {
    TOP_LEFT("top_left"),
    TOP_CENTER("top_center"),
    TOP_RIGHT("top_right"),
    BOTTOM_LEFT("bottom_left"),
    BOTTOM_RIGHT("bottom_right");

    private final String key;

    HudAnchor(String key) {
        this.key = key;
    }

    public String translationKey() {
        return "spotifysync.anchor." + key;
    }

    public HudAnchor next() {
        HudAnchor[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Resolves the top-left corner of a widget of the given size. */
    public int resolveX(int screenWidth, int widgetWidth, int offsetX) {
        return switch (this) {
            case TOP_LEFT, BOTTOM_LEFT -> offsetX;
            case TOP_CENTER -> (screenWidth - widgetWidth) / 2 + offsetX;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - widgetWidth - offsetX;
        };
    }

    public int resolveY(int screenHeight, int widgetHeight, int offsetY) {
        return switch (this) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> offsetY;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - widgetHeight - offsetY;
        };
    }
}
