package dev.vitalstages.client;
import dev.vitalstages.health.LifeStage;
import dev.vitalstages.network.HealthSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
/** Только визуальная копия; не рассчитывает физиологию и не разрешает лечение. */
public final class ClientHealthState {
    private static HealthSyncPayload latest;
    private static float vignette;
    private ClientHealthState() {}
    public static void accept(HealthSyncPayload p) { latest = p; }
    public static void clear() { latest = null; vignette = 0; }
    public static HealthSyncPayload current() {
        Minecraft mc = Minecraft.getInstance();
        if (latest == null || mc.player == null || mc.level == null || !mc.player.isAlive()
                || !latest.enabled() || !mc.player.getUUID().equals(latest.playerId())
                || !mc.level.dimension().location().equals(latest.dimension())) return null;
        return latest;
    }
    public static boolean unconscious() {
        HealthSyncPayload p = current(); return p != null && p.stage() == LifeStage.UNCONSCIOUS.ordinal();
    }
    public static void tick() {
        HealthSyncPayload p = current();
        float target = p == null || !p.delirium() ? 0 : Mth.clamp(
                ((100 - p.consciousness()) * 0.008f + p.pain() * 0.002f) * p.intensity(), 0, 0.95f);
        vignette = Mth.lerp(0.25f, vignette, target);
    }
    public static float vignette() { return vignette; }
}
