package dev.vitalstages.health;

import java.util.Objects;
import java.util.UUID;

/** Чистая immutable-модель. Антисептик не перевязывает рану и не стирает прогресс заживления. */
public record Wound(UUID id, BodyPart bodyPart, Type type, float severity,
                    boolean isBandaged, boolean isSplinted, int infectionTimer,
                    float infectionRisk, int healingTicks, int antisepticTicks) {
    public enum Type { CUT, BRUISE, FRACTURE, BURN }
    public static final float MAX_SEVERITY = 10;
    public Wound {
        Objects.requireNonNull(id); Objects.requireNonNull(bodyPart); Objects.requireNonNull(type);
        severity = Physiology.clamp(severity, 0.05f, MAX_SEVERITY, 0.05f);
        infectionTimer = bound(infectionTimer); infectionRisk = Physiology.clamp(infectionRisk, 0, 1, 0);
        healingTicks = bound(healingTicks); antisepticTicks = infectable(type) ? bound(antisepticTicks) : 0;
        isSplinted = isSplinted && type == Type.FRACTURE && bodyPart.isLimb();
    }
    public Wound(UUID id, BodyPart bodyPart, Type type, float severity, boolean bandaged,
            boolean splinted, int infectionTimer, float infectionRisk, int healingTicks) {
        this(id, bodyPart, type, severity, bandaged, splinted, infectionTimer, infectionRisk, healingTicks, 0);
    }
    private static int bound(int t) { return Math.max(0, Math.min(Physiology.MAX_TIMER, t)); }
    private static boolean infectable(Type t) { return t == Type.CUT || t == Type.BURN; }
    public static Wound fresh(BodyPart part, Type type, float severity) {
        return new Wound(UUID.randomUUID(), part, type, severity, false, false, 0, 0, 0, 0);
    }
    public boolean isOpenCut() { return type == Type.CUT && !isBandaged; }
    public boolean needsSplint() { return type == Type.FRACTURE && bodyPart.isLimb() && !isSplinted; }
    public boolean needsAntiseptic() { return infectable(type) && antisepticTicks == 0; }
    public float bleedWeight() { return isOpenCut() ? severity * bodyPart.bleedFactor : 0; }
    public Wound bandage() {
        if (!isOpenCut()) return this;
        return new Wound(id, bodyPart, type, severity, true, isSplinted, infectionTimer, infectionRisk, healingTicks, antisepticTicks);
    }
    public Wound splint() {
        if (!needsSplint()) return this;
        return new Wound(id, bodyPart, type, severity, isBandaged, true, infectionTimer, infectionRisk, healingTicks, antisepticTicks);
    }
    public Wound disinfect(int duration) {
        if (!needsAntiseptic() || duration <= 0) return this;
        return new Wound(id, bodyPart, type, severity, isBandaged, isSplinted, 0, 0, healingTicks, duration);
    }
    public Wound withHealingTicks(int ticks) {
        return new Wound(id, bodyPart, type, severity, isBandaged, isSplinted, infectionTimer, infectionRisk, ticks, antisepticTicks);
    }
    public boolean treatmentAllowsHealing() {
        return switch (type) {
            case CUT -> isBandaged;
            case FRACTURE -> isSplinted || !bodyPart.isLimb();
            default -> true;
        };
    }
    public Wound mergeImpact(Wound incoming) {
        if (bodyPart != incoming.bodyPart || type != incoming.type) throw new IllegalArgumentException("Несовместимые раны");
        // Повторная травма заново загрязняет рану, нарушает фиксацию/перевязку и сбрасывает заживление.
        return new Wound(id, bodyPart, type, severity + incoming.severity, false, false,
                isBandaged ? 0 : infectionTimer, Math.max(infectionRisk, incoming.infectionRisk), 0, 0);
    }
    public Wound tickInfection(boolean enabled, int delayTicks, float hazardPerSecond) {
        int remaining = Math.max(0, antisepticTicks - 1);
        if (!enabled || isBandaged || !infectable(type) || antisepticTicks > 0) {
            return remaining == antisepticTicks ? this : new Wound(id, bodyPart, type, severity,
                    isBandaged, isSplinted, infectionTimer, infectionRisk, healingTicks, remaining);
        }
        int age = Math.min(Physiology.MAX_TIMER, infectionTimer + 1);
        float hazard = Physiology.clamp(hazardPerSecond, 0, 1, 0);
        float risk = age > delayTicks ? (float) (1 - (1 - infectionRisk) * Math.exp(-hazard / 20.0)) : infectionRisk;
        return new Wound(id, bodyPart, type, severity, isBandaged, isSplinted, age, risk, healingTicks, remaining);
    }
}
