package dev.riftspotify.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.riftspotify.client.ui.SpotifySettingsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public final class ModKeyMappings {
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.riftspotify.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O,
            "key.categories.riftspotify");

    private ModKeyMappings() {}

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(ModKeyMappings.class);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_MENU.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new SpotifySettingsScreen(null));
            }
        }
    }
}
