package dev.vitalstages.item;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.PlayerHealthData;
public final class BandageItem extends MedicalItem {
    public BandageItem(Properties properties) { super(properties); }
    @Override protected boolean treat(PlayerHealthData d) { return d.bandageWorstOpenCut(); }
    @Override protected int cooldownTicks() { return HealthConfig.BANDAGE_COOLDOWN_TICKS.get(); }
    @Override protected String successKey() { return "vitalstages.bandaged"; }
    @Override protected String failureKey() { return "vitalstages.no_open_cut"; }
}
