package dev.vitalstages.item;

import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.PlayerHealthData;

/** Все предметы проходят общую серверную проверку MedicalItem. */
public final class AntisepticItem extends MedicalItem {
    public AntisepticItem(Properties p) { super(p); }
    @Override protected boolean treat(PlayerHealthData d) {
        return d.disinfectWorstWound(HealthConfig.ANTISEPTIC_SECONDS.get() * 20);
    }
    @Override protected int cooldownTicks() { return HealthConfig.MEDICINE_COOLDOWN_TICKS.get(); }
    @Override protected String successKey() { return "vitalstages.antiseptic.success"; }
    @Override protected String failureKey() { return "vitalstages.antiseptic.failed"; }
}
