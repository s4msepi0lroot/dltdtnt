package dev.vitalstages.item;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.PlayerHealthData;
public final class SplintItem extends MedicalItem {
    public SplintItem(Properties properties) { super(properties); }
    @Override protected boolean treat(PlayerHealthData d) { return d.splintWorstFracture(); }
    @Override protected int cooldownTicks() { return HealthConfig.SPLINT_COOLDOWN_TICKS.get(); }
    @Override protected String successKey() { return "vitalstages.splinted"; }
    @Override protected String failureKey() { return "vitalstages.no_fracture"; }
}
