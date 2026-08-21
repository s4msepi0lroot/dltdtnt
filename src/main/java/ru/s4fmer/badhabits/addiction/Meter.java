package ru.s4fmer.badhabits.addiction;

/**
 * Mutable per-substance state. Plain fields on purpose: it is serialized to JSON with Gson.
 */
public class Meter {
    /** 0..addictionMax */
    public float addiction;
    /** substance currently in the body */
    public float dose;
    /** size of the last taken dose, used for taper detection */
    public float lastDose;
    /** seconds without any dose while addicted */
    public int withdrawalSeconds;
    /** 0 = fine, 1..4 = withdrawal stage */
    public int stage;
    /** world game time of the last use */
    public long lastUseTick;

    public boolean isEmpty() {
        return addiction <= 0.0F && dose <= 0.0F && withdrawalSeconds == 0;
    }

    public void reset() {
        addiction = 0.0F;
        dose = 0.0F;
        lastDose = 0.0F;
        withdrawalSeconds = 0;
        stage = 0;
        lastUseTick = 0L;
    }
}
