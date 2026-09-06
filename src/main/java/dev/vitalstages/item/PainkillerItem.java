package dev.vitalstages.item;

import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.PlayerHealthData;

/** Все предметы проходят общую серверную проверку MedicalItem. */
public final class PainkillerItem extends MedicalItem {
    public PainkillerItem(Properties p) { super(p); }
    @Override protected boolean treat(PlayerHealthData d) {
        return d.takePainkiller(HealthConfig.PAINKILLER_SECONDS.get() * 20);
    }
    @Override protected int cooldownTicks() { return HealthConfig.MEDICINE_COOLDOWN_TICKS.get(); }
    @Override protected String successKey() { return "vitalstages.painkiller.success"; }
    @Override protected String failureKey() { return "vitalstages.painkiller.failed"; }
}
