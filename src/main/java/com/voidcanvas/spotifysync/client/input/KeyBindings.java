package com.voidcanvas.spotifysync.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Key bindings, all listed under a dedicated "Spotify Sync" category. */
public final class KeyBindings {

    public static final String CATEGORY = "key.categories.spotifysync";

    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.spotifysync.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    public static final KeyMapping OPEN_PLAYER = new KeyMapping(
            "key.spotifysync.open_player", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.spotifysync.toggle_hud", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

    public static final KeyMapping CYCLE_LYRICS = new KeyMapping(
            "key.spotifysync.cycle_lyrics", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY);

    public static final KeyMapping PLAY_PAUSE = new KeyMapping(
            "key.spotifysync.play_pause", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

    public static final KeyMapping NEXT_TRACK = new KeyMapping(
            "key.spotifysync.next_track", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

    public static final KeyMapping PREVIOUS_TRACK = new KeyMapping(
            "key.spotifysync.previous_track", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

    private KeyBindings() {
    }
}
