package dev.vitalstages.health;

import java.util.Objects;
import java.util.UUID;

/** Неизменяемая чистая модель. Minecraft/NBT вынесен в WoundNbtCodec для настоящих unit-проверок. */
public record Wound(UUID id, BodyPart bodyPart, Type type, float severity,
                    boolean isBandaged, boolean isSplinted, int infectionTimer,
                    float infectionRisk, int healingTicks) {
    public enum Type { CUT, BRUISE, FRACTURE, BURN }
    public static final float MAX_SEVERITY = 10;
    public Wound {
        Objects.requireNonNull(id); Objects.requireNonNull(bodyPart); Objects.requireNonNull(type);
        severity = Physiology.clamp(severity, 0.05f, MAX_SEVERITY, 0.05f);
        infectionTimer = Math.max(0, Math.min(Physiology.MAX_TIMER, infectionTimer));
        infectionRisk = Physiology.clamp(infectionRisk, 0, 1, 0);
        healingTicks = Math.max(0, Math.min(Physiology.MAX_TIMER, healingTicks));
        isSplinted = isSplinted && type == Type.FRACTURE && bodyPart.isLimb();
    }
    public static Wound fresh(BodyPart part, Type type, float severity) {
        return new Wound(UUID.randomUUID(), part, type, severity, false, false, 0, 0, 0);
    }
    public boolean isOpenCut() { return type == Type.CUT && !isBandaged; }
    public boolean needsSplint() { return type == Type.FRACTURE && bodyPart.isLimb() && !isSplinted; }
    public float bleedWeight() { return isOpenCut() ? severity * bodyPart.bleedFactor : 0; }
    public Wound bandage() {
        if (!isOpenCut()) return this;
        return new Wound(id, bodyPart, type, severity, true, isSplinted, infectionTimer, infectionRisk, healingTicks);
    }
    public Wound splint() {
        if (!needsSplint()) return this;
        return new Wound(id, bodyPart, type, severity, isBandaged, true, infectionTimer, infectionRisk, healingTicks);
    }
    public Wound withHealingTicks(int ticks) {
        return new Wound(id, bodyPart, type, severity, isBandaged, isSplinted, infectionTimer, infectionRisk, ticks);
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
        // Новая травма сбивает фиксацию/перевязку и прогресс. Старый объект остаётся неизменным.
        return new Wound(id, bodyPart, type, severity + incoming.severity, false, false,
                isBandaged ? 0 : infectionTimer, Math.max(infectionRisk, incoming.infectionRisk), 0);
    }
    public Wound tickInfection(boolean enabled, int delayTicks, float hazardPerSecond) {
        if (!enabled || isBandaged || (type != Type.CUT && type != Type.BURN)) return this;
        int age = Math.min(Physiology.MAX_TIMER, infectionTimer + 1);
        float hazard = Physiology.clamp(hazardPerSecond, 0, 1, 0);
        float risk = age > delayTicks ? (float) (1 - (1 - infectionRisk) * Math.exp(-hazard / 20.0)) : infectionRisk;
        return new Wound(id, bodyPart, type, severity, isBandaged, isSplinted, age, risk, healingTicks);
    }
}
