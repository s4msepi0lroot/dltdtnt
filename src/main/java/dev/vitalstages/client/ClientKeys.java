package dev.vitalstages.client;
import dev.vitalstages.config.HudConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
public final class ClientKeys {
    public static final KeyMapping TOGGLE_HUD = new KeyMapping("key.vitalstages.toggle_hud",
            GLFW.GLFW_KEY_H, "key.categories.vitalstages");
    private ClientKeys() {}
    public static void tick() {
        while (TOGGLE_HUD.consumeClick()) {
            if (Minecraft.getInstance().screen != null || Minecraft.getInstance().level == null) continue;
            HudConfig.SHOW_HUD.set(!HudConfig.SHOW_HUD.get());
            HudConfig.SPEC.save();
        }
    }
}
