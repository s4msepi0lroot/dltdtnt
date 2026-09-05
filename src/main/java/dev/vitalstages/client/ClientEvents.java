package dev.vitalstages.client;
import dev.vitalstages.VitalStages;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = VitalStages.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean cameraLocked;
    private static float lockedYaw, lockedPitch;
    private ClientEvents() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) { ClientHealthState.tick(); ClientKeys.tick(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { ClientHealthState.clear(); cameraLocked = false; }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn e) { ClientHealthState.clear(); cameraLocked = false; }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent e) {
        if (!ClientHealthState.unconscious()) return;
        var i = e.getInput();
        i.leftImpulse = 0; i.forwardImpulse = 0; i.up = false; i.down = false; i.left = false; i.right = false;
        i.jumping = false; i.shiftKeyDown = false; e.getEntity().setSprinting(false);
    }
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        if (ClientHealthState.unconscious()) { e.setCanceled(true); e.setSwingHand(false); }
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (!ClientHealthState.unconscious()) { cameraLocked = false; return; }
        if (!cameraLocked) { lockedYaw = e.getYaw(); lockedPitch = e.getPitch(); cameraLocked = true; }
        e.setYaw(lockedYaw); e.setPitch(lockedPitch); e.setRoll(0);
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post e) {
        // Рисуем вне условного health-layer: F1 не является способом выйти из blackout.
        VitalsOverlay.render(e.getGuiGraphics());
    }
}
