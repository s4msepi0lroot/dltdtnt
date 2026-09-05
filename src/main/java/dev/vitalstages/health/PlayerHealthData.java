package dev.vitalstages.health;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import java.util.ArrayList;
import java.util.List;

/** Все мутации происходят на основном серверном потоке. Attachment владеет этим объектом. */
public final class PlayerHealthData implements INBTSerializable<CompoundTag>, HealthView {
    public static final int FORMAT_VERSION = 2;
    public static final int MAX_WOUNDS = BodyPart.values().length * Wound.Type.values().length;
    private float health = 20, bloodLevel = 100, bleedingRate, consciousness = 100, pain, bodyTemperature = 37;
    private boolean unconscious, criticalTrauma;
    private int unconsciousTicks;
    private final ArrayList<Wound> wounds = new ArrayList<>();
    // Технические флаги НЕ переживают загрузку/clone и не попадают в NBT.
    private boolean finalizing, controlsLocked;
    private int terminalRetryTicks;

    @Override public float health() { return health; }
    @Override public float bloodLevel() { return bloodLevel; }
    @Override public float bleedingRate() { return bleedingRate; }
    @Override public float consciousness() { return consciousness; }
    @Override public float pain() { return pain; }
    @Override public float bodyTemperature() { return bodyTemperature; }
    @Override public boolean unconscious() { return unconscious; }
    @Override public int unconsciousTicks() { return unconsciousTicks; }
    @Override public List<Wound> wounds() { return List.copyOf(wounds); }
    public boolean criticalTrauma() { return criticalTrauma; }
    public boolean finalizing() { return finalizing; }
    public void setFinalizing(boolean value) { finalizing = value; }
    public boolean updateControlLock(boolean now) {
        boolean changed = controlsLocked != now; controlsLocked = now; return changed;
    }
    public LifeStage stage(float threshold) {
        return unconscious ? LifeStage.UNCONSCIOUS
                : consciousness < threshold ? LifeStage.PRESYNCOPE : LifeStage.CONSCIOUS;
    }
    public static float vanillaFloor(float maxHealth) { return Math.min(1, Math.max(0.001f, maxHealth / 4)); }
    public void refreshVanillaHealth(float vanillaHealth, float maxHealth, float reviveHealth) {
        float floor = vanillaFloor(maxHealth);
        float wakeHp = Math.min(maxHealth, Math.max(floor * 2, reviveHealth));
        if (criticalTrauma && vanillaHealth >= wakeHp) criticalTrauma = false;
        // HP остаётся vanilla-источником истины. Единственное исключение: технический HP-floor
        // после отменённой смерти соответствует физиологическому health=0.
        health = criticalTrauma && vanillaHealth <= floor + 0.0001f ? 0
                : Physiology.clamp(vanillaHealth, 0, Math.max(0, maxHealth), 0);
    }
    public void knockOut() {
        accept(Physiology.knockOut(physiologyState()));
        // Повторная травма никогда не обнуляет уже запущенное окно спасения.
    }
    public void enterCriticalTrauma() { criticalTrauma = true; health = 0; knockOut(); }
    public void addImpact(Wound incoming, float painPerSeverity, float shockPerSeverity) {
        int index = -1;
        for (int i = 0; i < wounds.size(); i++) {
            Wound old = wounds.get(i);
            if (old.bodyPart() == incoming.bodyPart() && old.type() == incoming.type()) { index = i; break; }
        }
        if (index < 0) wounds.add(incoming); else wounds.set(index, wounds.get(index).mergeImpact(incoming));
        float factor = incoming.bodyPart().shockFactor;
        pain = Physiology.clamp(pain + incoming.severity() * painPerSeverity * factor, 0, 100, 100);
        consciousness = Math.max(0, consciousness - incoming.severity() * shockPerSeverity * factor);
        if (consciousness <= 0) knockOut();
    }
    public boolean bandageWorstOpenCut() {
        int index = -1; float worst = 0;
        for (int i = 0; i < wounds.size(); i++) {
            float weight = wounds.get(i).bleedWeight();
            if (weight > worst) { worst = weight; index = i; }
        }
        if (index < 0) return false;
        wounds.set(index, wounds.get(index).bandage());
        // Не прибавляем HP/кровь/сознание: бинт только закрывает конкретную рану.
        return true;
    }
    public boolean splintWorstFracture() {
        int selected = -1;
        float worst = -1;
        for (int i = 0; i < wounds.size(); i++) {
            Wound w = wounds.get(i);
            if (!w.needsSplint()) continue;
            float score = w.severity() * (w.bodyPart().isLeg() ? 1.5f : 1);
            if (score > worst) { selected = i; worst = score; }
        }
        if (selected < 0) return false;
        wounds.set(selected, wounds.get(selected).splint());
        return true;
    }
    public int fracturedMask() {
        int mask = 0;
        for (Wound w : wounds) if (w.type() == Wound.Type.FRACTURE) mask |= w.bodyPart().bit();
        return mask;
    }
    public int splintedMask() {
        int mask = 0;
        for (Wound w : wounds) if (w.type() == Wound.Type.FRACTURE && w.isSplinted()) mask |= w.bodyPart().bit();
        return mask;
    }
    public int openCutMask() {
        int mask = 0;
        for (Wound w : wounds) if (w.isOpenCut()) mask |= w.bodyPart().bit();
        return mask;
    }
    public boolean tickRecovery(boolean stable, int bruiseTicks, int cutTicks, int burnTicks, int fractureTicks) {
        boolean removed = false;
        for (int i = wounds.size() - 1; i >= 0; i--) {
            Wound w = wounds.get(i);
            int duration = switch (w.type()) {
                case CUT -> cutTicks;
                case BRUISE -> bruiseTicks;
                case BURN -> burnTicks;
                case FRACTURE -> fractureTicks;
            };
            RecoveryClock.Step step = RecoveryClock.advance(w.healingTicks(), duration,
                    stable && w.treatmentAllowsHealing());
            if (step.healed()) { wounds.remove(i); removed = true; }
            else if (step.ticks() != w.healingTicks()) wounds.set(i, w.withHealingTicks(step.ticks()));
        }
        // Восстановление тканей не даёт HP/кровь и не сбрасывает таймер обморока.
        return removed;
    }
    public void recomputeBleeding(boolean enabled, float base, float multiplier, float maximum) {
        float weight = 0; for (Wound w : wounds) weight += w.bleedWeight();
        bleedingRate = enabled ? Physiology.clamp(weight * base * multiplier, 0, maximum, 0) : 0;
    }
    public void tickInfection(boolean enabled, int delay, float hazard) {
        for (int i = 0; i < wounds.size(); i++) wounds.set(i, wounds.get(i).tickInfection(enabled, delay, hazard));
    }
    public Physiology.State physiologyState() {
        return new Physiology.State(bloodLevel, pain, consciousness, unconscious, unconsciousTicks, criticalTrauma);
    }
    public void accept(Physiology.State s) {
        bloodLevel = s.blood(); pain = s.pain(); consciousness = s.consciousness();
        unconscious = s.unconscious(); unconsciousTicks = s.unconsciousTicks(); criticalTrauma = s.criticalTrauma();
    }
    public boolean allowTerminalAttempt() {
        if (terminalRetryTicks > 0) { terminalRetryTicks--; return false; }
        terminalRetryTicks = 19; return true;
    }
    public void clearTerminalRetry() { terminalRetryTicks = 0; }
    @Override public LimbStatus limbStatus(BodyPart part) {
        LimbStatus result = LimbStatus.HEALTHY;
        for (Wound w : wounds) if (w.bodyPart() == part) {
            if (w.type() == Wound.Type.FRACTURE) return LimbStatus.FRACTURED;
            result = LimbStatus.BRUISED;
        }
        return result;
    }
    public int packedLimbStatus() {
        int result = 0;
        for (BodyPart part : BodyPart.values()) result |= limbStatus(part).ordinal() << (part.ordinal() * 2);
        return result;
    }
    public PlayerHealthData copy() {
        PlayerHealthData d = new PlayerHealthData();
        d.health = health; d.bloodLevel = bloodLevel; d.bleedingRate = bleedingRate;
        d.consciousness = consciousness; d.pain = pain; d.bodyTemperature = bodyTemperature;
        d.unconscious = unconscious; d.unconsciousTicks = unconsciousTicks; d.criticalTrauma = criticalTrauma;
        d.wounds.addAll(wounds); // Wound неизменяем, контейнер списка новый.
        return d;
    }
    @Override public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        CompoundTag n = new CompoundTag();
        n.putInt("version", FORMAT_VERSION); n.putFloat("health", health); n.putFloat("bloodLevel", bloodLevel);
        n.putFloat("bleedingRate", bleedingRate); n.putFloat("consciousness", consciousness);
        n.putFloat("pain", pain); n.putFloat("bodyTemperature", bodyTemperature);
        n.putBoolean("unconscious", unconscious); n.putInt("unconsciousTicks", unconsciousTicks);
        n.putBoolean("criticalTrauma", criticalTrauma);
        ListTag list = new ListTag(); for (Wound w : wounds) list.add(WoundNbtCodec.write(w)); n.put("wounds", list);
        return n;
    }
    @Override public void deserializeNBT(HolderLookup.Provider registries, CompoundTag n) {
        if (n.getInt("version") > FORMAT_VERSION)
            throw new IllegalArgumentException("Vital Stages: NBT из более новой версии; downgrade запрещён");
        health = number(n, "health", 20, 0, 1024); bloodLevel = number(n, "bloodLevel", 100, 0, 100);
        bleedingRate = number(n, "bleedingRate", 0, 0, 100); // Производное: пересчёт при входе.
        consciousness = number(n, "consciousness", 100, 0, 100); pain = number(n, "pain", 0, 0, 100);
        bodyTemperature = number(n, "bodyTemperature", 37, 25, 45);
        unconscious = n.getBoolean("unconscious") || consciousness <= 0;
        criticalTrauma = n.getBoolean("criticalTrauma");
        if (criticalTrauma) { unconscious = true; consciousness = 0; }
        unconsciousTicks = unconscious ? Math.max(0, Math.min(Physiology.MAX_TIMER, n.getInt("unconsciousTicks"))) : 0;
        wounds.clear(); ListTag list = n.getList("wounds", Tag.TAG_COMPOUND);
        // Ограничены и размер состояния, и объём обрабатываемого входного списка.
        for (int i = 0; i < Math.min(MAX_WOUNDS, list.size()); i++) {
            WoundNbtCodec.read(list.getCompound(i)).ifPresent(w -> {
                if (wounds.stream().noneMatch(old -> old.bodyPart() == w.bodyPart() && old.type() == w.type()))
                    wounds.add(w);
            });
        }
        finalizing = false; controlsLocked = false; terminalRetryTicks = 0;
    }
    private static float number(CompoundTag n, String key, float fallback, float min, float max) {
        return n.contains(key, Tag.TAG_ANY_NUMERIC) ? Physiology.clamp(n.getFloat(key), min, max, fallback) : fallback;
    }
}
