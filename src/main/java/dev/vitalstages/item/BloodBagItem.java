package dev.vitalstages.item;

import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.PlayerHealthData;

/** Все предметы проходят общую серверную проверку MedicalItem. */
public final class BloodBagItem extends MedicalItem {
    public BloodBagItem(Properties p) { super(p); }
    @Override protected boolean treat(PlayerHealthData d) {
        return d.transfuseBlood();
    }
    @Override protected int cooldownTicks() { return HealthConfig.MEDICINE_COOLDOWN_TICKS.get(); }
    @Override protected String successKey() { return "vitalstages.blood_bag.success"; }
    @Override protected String failureKey() { return "vitalstages.blood_bag.failed"; }
}
