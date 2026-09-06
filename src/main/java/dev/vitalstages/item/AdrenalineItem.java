package dev.vitalstages.item;

import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.PlayerHealthData;

/** Все предметы проходят общую серверную проверку MedicalItem. */
public final class AdrenalineItem extends MedicalItem {
    public AdrenalineItem(Properties p) { super(p); }
    @Override protected boolean treat(PlayerHealthData d) {
        float minBlood = HealthConfig.ENABLE_BLOOD_LOSS.get()
                ? Math.max(HealthConfig.f(HealthConfig.ADRENALINE_MIN_BLOOD), HealthConfig.f(HealthConfig.CRITICAL_BLOOD) + 1) : 0;
        return d.giveAdrenaline(minBlood, Math.max(30, HealthConfig.f(HealthConfig.WAKE_THRESHOLD) + 1),
                HealthConfig.ADRENALINE_SECONDS.get() * 20, HealthConfig.ADRENALINE_COOLDOWN_SECONDS.get() * 20);
    }
    @Override protected int cooldownTicks() { return HealthConfig.MEDICINE_COOLDOWN_TICKS.get(); }
    @Override protected String successKey() { return "vitalstages.adrenaline.success"; }
    @Override protected String failureKey() { return "vitalstages.adrenaline.failed"; }
}
