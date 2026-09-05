package dev.vitalstages.health;

import net.minecraft.nbt.CompoundTag;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Неизменяемая рана; один агрегат данного типа на часть тела. */
public record Wound(UUID id, BodyPart bodyPart, Type type, float severity,
                    boolean isBandaged, boolean isSplinted, int infectionTimer, float infectionRisk) {
    public enum Type { CUT, BRUISE, FRACTURE, BURN }
    public static final float MAX_SEVERITY = 10;
    public Wound {
        Objects.requireNonNull(id); Objects.requireNonNull(bodyPart); Objects.requireNonNull(type);
        severity = Physiology.clamp(severity, 0.05f, MAX_SEVERITY, 0.05f);
        infectionTimer = Math.max(0, Math.min(Physiology.MAX_TIMER, infectionTimer));
        infectionRisk = Physiology.clamp(infectionRisk, 0, 1, 0);
    }
    public static Wound fresh(BodyPart part, Type type, float severity) {
        return new Wound(UUID.randomUUID(), part, type, severity, false, false, 0, 0);
    }
    public boolean isOpenCut() { return type == Type.CUT && !isBandaged; }
    public float bleedWeight() { return isOpenCut() ? severity * bodyPart.bleedFactor : 0; }
    public Wound bandage() {
        return new Wound(id, bodyPart, type, severity, true, isSplinted, infectionTimer, infectionRisk);
    }
    public Wound mergeImpact(Wound incoming) {
        if (bodyPart != incoming.bodyPart || type != incoming.type)
            throw new IllegalArgumentException("Несовместимые раны");
        // Новый удар по перевязанному месту открывает новую рану в том же агрегате.
        return new Wound(id, bodyPart, type, severity + incoming.severity, false, false,
                isBandaged ? 0 : infectionTimer, Math.max(infectionRisk, incoming.infectionRisk));
    }
    public Wound tickInfection(boolean enabled, int delayTicks, float hazardPerSecond) {
        if (!enabled || isBandaged || (type != Type.CUT && type != Type.BURN)) return this;
        int age = Math.min(Physiology.MAX_TIMER, infectionTimer + 1);
        // Накопленный риск. Не бросаем эту полную вероятность заново 20 раз в секунду.
        float risk = age > delayTicks
                ? (float) (1 - (1 - infectionRisk) * Math.exp(-hazardPerSecond / 20.0)) : infectionRisk;
        return new Wound(id, bodyPart, type, severity, isBandaged, isSplinted, age, risk);
    }
    public CompoundTag toNbt() {
        CompoundTag n = new CompoundTag();
        n.putUUID("id", id); n.putString("bodyPart", bodyPart.name()); n.putString("type", type.name());
        n.putFloat("severity", severity); n.putBoolean("isBandaged", isBandaged);
        n.putBoolean("isSplinted", isSplinted); n.putInt("infectionTimer", infectionTimer);
        n.putFloat("infectionRisk", infectionRisk); return n;
    }
    public static Optional<Wound> fromNbt(CompoundTag n) {
        try {
            return Optional.of(new Wound(n.hasUUID("id") ? n.getUUID("id") : UUID.randomUUID(),
                    BodyPart.valueOf(n.getString("bodyPart")), Type.valueOf(n.getString("type")),
                    n.getFloat("severity"), n.getBoolean("isBandaged"), n.getBoolean("isSplinted"),
                    n.getInt("infectionTimer"), n.getFloat("infectionRisk")));
        } catch (IllegalArgumentException ex) { return Optional.empty(); }
    }
}
