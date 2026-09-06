package dev.vitalstages.health;

/** Игровые правила, не реальные медицинские дозировки. Одна доза крови всегда равна 20. */
public final class MedicalRules {
    public static final float BLOOD_UNIT = 20;
    private MedicalRules() {}
    public static boolean canDonate(Physiology.State s, float bleed, float healthFraction, int cooldown, float minBlood) {
        return !s.unconscious() && !s.criticalTrauma() && s.consciousness() >= 80
                && Float.isFinite(healthFraction) && healthFraction >= 0.75f
                && Float.isFinite(bleed) && bleed <= 0.0001f && cooldown == 0
                && s.blood() >= Math.max(BLOOD_UNIT, minBlood);
    }
    public static boolean canAdrenaline(Physiology.State s, float bleed, int cooldown, float minBlood) {
        return s.unconscious() && !s.criticalTrauma() && Float.isFinite(bleed) && bleed <= 0.0001f
                && cooldown == 0 && s.blood() >= minBlood; // minBlood=0 только при выключенной системе крови.
    }
    public static float transfusedBlood(float blood) {
        return Math.min(100, Physiology.clamp(blood, 0, 100, 100) + BLOOD_UNIT);
    }
}
