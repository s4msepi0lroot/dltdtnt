# Vital Stages 0.2.0 — полный код MVP-2

Java 21 / Minecraft 1.21.1 / NeoForge 21.1.233. Чистые модели и синтаксис проверены; полный Gradle/игровой прогон ещё требуется.

## a. Хранение

### src/main/java/dev/vitalstages/health/PlayerHealthData.java

```java
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
```

### src/main/java/dev/vitalstages/health/HealthView.java

```java
package dev.vitalstages.health;
import java.util.List;
/** Read-only capability. Мутации выполняет серверный слой, а не потребитель API. */
public interface HealthView {
    float health();
    float bloodLevel();
    float bleedingRate();
    float consciousness();
    float pain();
    float bodyTemperature();
    boolean unconscious();
    int unconsciousTicks();
    List<Wound> wounds();
    LimbStatus limbStatus(BodyPart part);
}
```

### src/main/java/dev/vitalstages/registry/HealthAttachments.java

```java
package dev.vitalstages.registry;
import dev.vitalstages.VitalStages;
import dev.vitalstages.health.HealthView;
import dev.vitalstages.health.PlayerHealthData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

public final class HealthAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VitalStages.MOD_ID);
    public static final Supplier<AttachmentType<PlayerHealthData>> HEALTH = TYPES.register("health",
            () -> AttachmentType.serializable(PlayerHealthData::new).build());
    public static final EntityCapability<HealthView, Void> VIEW =
            EntityCapability.createVoid(VitalStages.id("health"), HealthView.class);
    private HealthAttachments() {}
    public static PlayerHealthData get(Player player) { return player.getData(HEALTH); }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Attachment — сохранение, capability — read-only API над ТЕМ ЖЕ объектом.
        // На клиенте несинхронизированный attachment не выдаётся за правду.
        event.registerEntity(VIEW, EntityType.PLAYER,
                (player, context) -> player.level().isClientSide ? null : get(player));
    }
}
```

## b. Раны

### src/main/java/dev/vitalstages/health/Wound.java

```java
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
```

### src/main/java/dev/vitalstages/health/WoundNbtCodec.java

```java
package dev.vitalstages.health;

import net.minecraft.nbt.CompoundTag;
import java.util.Optional;
import java.util.UUID;

/** Сохраняет все старые имена ключей. В MVP-1 healingTicks отсутствовал: его default равен 0. */
public final class WoundNbtCodec {
    private WoundNbtCodec() {}
    public static CompoundTag write(Wound w) {
        CompoundTag n = new CompoundTag();
        n.putUUID("id", w.id()); n.putString("bodyPart", w.bodyPart().name()); n.putString("type", w.type().name());
        n.putFloat("severity", w.severity()); n.putBoolean("isBandaged", w.isBandaged());
        n.putBoolean("isSplinted", w.isSplinted()); n.putInt("infectionTimer", w.infectionTimer());
        n.putFloat("infectionRisk", w.infectionRisk()); n.putInt("healingTicks", w.healingTicks());
        return n;
    }
    public static Optional<Wound> read(CompoundTag n) {
        try {
            return Optional.of(new Wound(n.hasUUID("id") ? n.getUUID("id") : UUID.randomUUID(),
                    BodyPart.valueOf(n.getString("bodyPart")), Wound.Type.valueOf(n.getString("type")),
                    n.getFloat("severity"), n.getBoolean("isBandaged"), n.getBoolean("isSplinted"),
                    n.getInt("infectionTimer"), n.getFloat("infectionRisk"), n.getInt("healingTicks")));
        } catch (IllegalArgumentException ex) { return Optional.empty(); }
    }
}
```

### src/main/java/dev/vitalstages/health/BodyPart.java

```java
package dev.vitalstages.health;

public enum BodyPart {
    HEAD(1.5f, 2.2f), TORSO(1.4f, 1.5f),
    LEFT_ARM(1.0f, 1.0f), RIGHT_ARM(1.0f, 1.0f),
    LEFT_LEG(1.1f, 1.0f), RIGHT_LEG(1.1f, 1.0f);
    public boolean isLeg() { return this == LEFT_LEG || this == RIGHT_LEG; }
    public boolean isArm() { return this == LEFT_ARM || this == RIGHT_ARM; }
    public boolean isLimb() { return isLeg() || isArm(); }
    public int bit() { return 1 << ordinal(); }
    public final float bleedFactor, shockFactor;
    BodyPart(float bleedFactor, float shockFactor) {
        this.bleedFactor = bleedFactor; this.shockFactor = shockFactor;
    }
}
```

### src/main/java/dev/vitalstages/health/LimbStatus.java

```java
package dev.vitalstages.health;
public enum LimbStatus { HEALTHY, BRUISED, FRACTURED }
```

### src/main/java/dev/vitalstages/health/LifeStage.java

```java
package dev.vitalstages.health;
public enum LifeStage { CONSCIOUS, PRESYNCOPE, UNCONSCIOUS }
```

### src/main/java/dev/vitalstages/health/RecoveryClock.java

```java
package dev.vitalstages.health;

/** Прогресс восстановления — игровые онлайн-тики. Смена часов ОС на него не влияет. */
public final class RecoveryClock {
    private RecoveryClock() {}
    public record Step(int ticks, boolean healed) {}
    public static Step advance(int previous, int duration, boolean allowed) {
        int age = Math.max(0, Math.min(Physiology.MAX_TIMER, previous));
        int required = Math.max(1, Math.min(Physiology.MAX_TIMER, duration));
        if (!allowed) return new Step(age, false);
        int next = Math.min(required, age + 1);
        return new Step(next, next >= required);
    }
}
```

### src/main/java/dev/vitalstages/health/FractureProfile.java

```java
package dev.vitalstages.health;

/** Чистая математика штрафов: не трогаем base value атрибутов и чужие модификаторы. */
public record FractureProfile(double movement, double attackSpeed, double miningSpeed,
                              int untreatedLegs, int untreatedArms) {
    public static FractureProfile calculate(int fractures, int splinted, boolean enabled,
                                            double legFactor, double attackFactor, double miningFactor) {
        int untreated = enabled ? fractures & ~splinted & 0x3F : 0;
        int legs = Integer.bitCount(untreated & (BodyPart.LEFT_LEG.bit() | BodyPart.RIGHT_LEG.bit()));
        int arms = Integer.bitCount(untreated & (BodyPart.LEFT_ARM.bit() | BodyPart.RIGHT_ARM.bit()));
        return new FractureProfile(Math.pow(valid(legFactor), legs), Math.pow(valid(attackFactor), arms),
                Math.pow(valid(miningFactor), arms), legs, arms);
    }
    private static double valid(double value) {
        return Double.isFinite(value) ? Math.max(0.05, Math.min(1, value)) : 1;
    }
}
```

## c. Урон

### src/main/java/dev/vitalstages/event/DamageEventHandler.java

```java
package dev.vitalstages.event;

import dev.vitalstages.VitalStages;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.BodyPart;
import dev.vitalstages.health.PlayerHealthData;
import dev.vitalstages.health.Wound;
import dev.vitalstages.network.HealthNetwork;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(modid = VitalStages.MOD_ID)
public final class DamageEventHandler {
    private DamageEventHandler() {}
    public static boolean eligible(Player p) { return !p.isCreative() && !p.isSpectator(); }
    public static boolean bypassesStages(DamageSource source) {
        // /kill и пустота — намеренные исключения, не бесконечное спасение вне мира.
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void preDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !eligible(player)
                || bypassesStages(event.getSource()) || HealthAttachments.get(player).finalizing()) return;
        float amount = event.getNewDamage();
        if (!Float.isFinite(amount) || amount <= 0) { event.setNewDamage(0); return; }
        // После брони/эффектов, но ДО absorption в NeoForge 1.21.1.
        // Конверсия до жёлтых сердец — явное правило баланса этого MVP.
        event.setNewDamage(amount * HealthConfig.f(HealthConfig.HEALTH_DAMAGE_FRACTION));
    }
    @SubscribeEvent
    public static void postDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !eligible(player)
                || bypassesStages(event.getSource()) || HealthAttachments.get(player).finalizing()) return;
        float actual = event.getNewDamage();
        // Щит, absorption и чужая нулевая модификация не создают «призрачные» раны.
        if (!Float.isFinite(actual) || actual <= 0) return;
        PlayerHealthData data = HealthAttachments.get(player);
        Wound.Type type = woundType(event.getSource(), actual);
        if (type != null) {
            BodyPart part = bodyPart(player, event.getSource());
            float severity = Math.min(Wound.MAX_SEVERITY, Math.max(0.05f, actual / 4));
            data.addImpact(Wound.fresh(part, type, severity), HealthConfig.f(HealthConfig.PAIN_PER_SEVERITY),
                    HealthConfig.f(HealthConfig.SHOCK_PER_SEVERITY));
            // Сильный физический удар может дополнительно сломать руку/ногу.
            // Дополнительный перелом не удваивает уже начисленный бюджет боли/шока.
            if (part.isLimb() && type != Wound.Type.FRACTURE && type != Wound.Type.BURN
                    && HealthConfig.ENABLE_FRACTURES.get()
                    && actual >= HealthConfig.f(HealthConfig.HEAVY_FRACTURE_THRESHOLD)
                    && player.getRandom().nextFloat() < HealthConfig.f(HealthConfig.HEAVY_FRACTURE_CHANCE)) {
                data.addImpact(Wound.fresh(part, Wound.Type.FRACTURE, severity), 0, 0);
            }
            TickHandler.recomputeBleeding(data);
            FractureEffects.refresh(player);
        }
        data.refreshVanillaHealth(player.getHealth(), player.getMaxHealth(), HealthConfig.f(HealthConfig.REVIVE_HEALTH));
        // При летальном ударе дождёмся vanilla-решения о тотеме, затем LivingDeathEvent.
        if (player.getHealth() > 0) HealthNetwork.sync(player);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !eligible(player)) return;
        PlayerHealthData data = HealthAttachments.get(player);
        if (data.finalizing()) {
            // Другая death-механика вправе отменить смерть. Не оставляем HP=0 у живой сущности.
            if (event.isCanceled() && player.getHealth() <= 0)
                player.setHealth(PlayerHealthData.vanillaFloor(player.getMaxHealth()));
            return;
        }
        if (event.isCanceled() || bypassesStages(event.getSource())) return;
        if ((HealthConfig.ENABLE_BLOOD_LOSS.get() && data.bloodLevel() <= 0)
                || (data.unconscious() && data.unconsciousTicks() >= HealthConfig.windowTicks())) return;
        event.setCanceled(true);
        data.enterCriticalTrauma(); // Повторный вызов не сбрасывает таймер.
        player.setHealth(PlayerHealthData.vanillaFloor(player.getMaxHealth()));
        player.stopUsingItem(); HealthNetwork.sync(player);
    }
    private static Wound.Type woundType(DamageSource source, float damage) {
        if (source.is(DamageTypeTags.IS_FIRE)) return Wound.Type.BURN;
        if (source.is(DamageTypeTags.IS_FALL))
            return HealthConfig.ENABLE_FRACTURES.get() && damage >= HealthConfig.f(HealthConfig.FRACTURE_DAMAGE_THRESHOLD)
                    ? Wound.Type.FRACTURE : Wound.Type.BRUISE;
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION)) return Wound.Type.CUT;
        // Асфиксия, голод и магия не равны открытой наружной ране.
        if (source.is(DamageTypes.DROWN) || source.is(DamageTypes.STARVE) || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.FREEZE) || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER)) return null;
        if (source.getEntity() instanceof LivingEntity attacker) {
            boolean sharp = !(attacker instanceof Player) || attacker.getMainHandItem().is(ItemTags.SWORDS)
                    || attacker.getMainHandItem().is(ItemTags.AXES);
            return sharp ? Wound.Type.CUT : Wound.Type.BRUISE;
        }
        return Wound.Type.BRUISE;
    }
    private static BodyPart bodyPart(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.IS_FALL))
            return player.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
        Vec3 position = source.getSourcePosition();
        if (source.is(DamageTypeTags.IS_PROJECTILE) && position != null) {
            // Приближение по высоте снаряда; bone-hitbox пока не реализован.
            double y = position.y - player.getY();
            if (y > player.getBbHeight() * 0.8) return BodyPart.HEAD;
            if (y > player.getBbHeight() * 0.4) return BodyPart.TORSO;
            return player.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
        }
        return switch (player.getRandom().nextInt(10)) {
            case 0 -> BodyPart.HEAD;
            case 1, 2, 3 -> BodyPart.TORSO;
            case 4 -> BodyPart.LEFT_ARM;
            case 5 -> BodyPart.RIGHT_ARM;
            case 6, 7 -> BodyPart.LEFT_LEG;
            default -> BodyPart.RIGHT_LEG;
        };
    }
}
```

### src/main/java/dev/vitalstages/event/FractureEffects.java

```java
package dev.vitalstages.event;

import dev.vitalstages.VitalStages;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.FractureProfile;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class FractureEffects {
    private static final ResourceLocation LEGS = VitalStages.id("fractured_legs");
    private static final ResourceLocation ARMS_ATTACK = VitalStages.id("fractured_arms_attack");
    private static final ResourceLocation ARMS_MINING = VitalStages.id("fractured_arms_mining");
    private FractureEffects() {}
    public static void refresh(ServerPlayer p) {
        var d = HealthAttachments.get(p);
        var profile = FractureProfile.calculate(d.fracturedMask(), d.splintedMask(),
                DamageEventHandler.eligible(p) && HealthConfig.ENABLE_FRACTURES.get(),
                HealthConfig.LEG_MOVEMENT_FACTOR.get(), HealthConfig.ARM_ATTACK_FACTOR.get(), HealthConfig.ARM_MINING_FACTOR.get());
        apply(p, Attributes.MOVEMENT_SPEED, LEGS, profile.movement());
        apply(p, Attributes.ATTACK_SPEED, ARMS_ATTACK, profile.attackSpeed());
        apply(p, Attributes.BLOCK_BREAK_SPEED, ARMS_MINING, profile.miningSpeed());
    }
    private static void apply(ServerPlayer p, Holder<Attribute> key, ResourceLocation id, double factor) {
        AttributeInstance attribute = p.getAttribute(key);
        if (attribute == null) return; // Совместимость с нестандартным набором атрибутов.
        AttributeModifier old = attribute.getModifier(id);
        double amount = factor - 1;
        if (Math.abs(amount) < 1e-8) {
            if (old != null) attribute.removeModifier(id);
            return;
        }
        if (old != null && Math.abs(old.amount() - amount) < 1e-8
                && old.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) return;
        if (old != null) attribute.removeModifier(id);
        // Стабильные ID и transient исключают накопление после login/Clone/dimension.
        attribute.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
```

## d. Тики

### src/main/java/dev/vitalstages/event/TickHandler.java

```java
package dev.vitalstages.event;

import dev.vitalstages.VitalStages;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.Physiology;
import dev.vitalstages.health.PlayerHealthData;
import dev.vitalstages.network.HealthNetwork;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = VitalStages.MOD_ID)
public final class TickHandler {
    public static final ResourceKey<DamageType> ORGAN_FAILURE =
            ResourceKey.create(Registries.DAMAGE_TYPE, VitalStages.id("organ_failure"));
    private TickHandler() {}
    public static void recomputeBleeding(PlayerHealthData data) {
        data.recomputeBleeding(HealthConfig.ENABLE_BLOOD_LOSS.get(), HealthConfig.f(HealthConfig.BASE_BLEED_PER_SEVERITY),
                HealthConfig.f(HealthConfig.BLEED_RATE_MULTIPLIER), HealthConfig.f(HealthConfig.MAX_BLEED));
    }
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
        PlayerHealthData data = HealthAttachments.get(player);
        if (!DamageEventHandler.eligible(player)) {
            // Creative/spectator приостанавливают физиологию, но не стирают раны.
            data.updateControlLock(false);
            FractureEffects.refresh(player);
            if (player.tickCount % HealthConfig.SYNC_INTERVAL_TICKS.get() == 0) HealthNetwork.sync(player);
            return;
        }
        data.refreshVanillaHealth(player.getHealth(), player.getMaxHealth(), HealthConfig.f(HealthConfig.REVIVE_HEALTH));
        recomputeBleeding(data);
        data.tickInfection(HealthConfig.ENABLE_INFECTION.get(), HealthConfig.INFECTION_DELAY_SECONDS.get() * 20,
                HealthConfig.f(HealthConfig.INFECTION_HAZARD_PER_SECOND));
        Physiology.Result result = Physiology.step(data.physiologyState(), data.bleedingRate(), data.bodyTemperature(),
                player.getFoodData().getFoodLevel(), HealthConfig.rules());
        data.accept(result.state());
        boolean recovered = false;
        if (!result.terminal()) {
            boolean stable = !data.criticalTrauma() && (!HealthConfig.ENABLE_BLOOD_LOSS.get()
                    || data.bloodLevel() > HealthConfig.f(HealthConfig.CRITICAL_BLOOD));
            recovered = data.tickRecovery(stable, HealthConfig.BRUISE_HEAL_SECONDS.get() * 20,
                    HealthConfig.CUT_HEAL_SECONDS.get() * 20, HealthConfig.BURN_HEAL_SECONDS.get() * 20,
                    HealthConfig.FRACTURE_HEAL_SECONDS.get() * 20);
            if (recovered) recomputeBleeding(data);
        }
        FractureEffects.refresh(player);
        boolean controlsChanged = data.updateControlLock(data.unconscious());
        if (data.unconscious()) {
            player.stopUsingItem(); player.setSprinting(false); player.setJumping(false); player.stopFallFlying();
            player.xxa = 0; player.yya = 0; player.zza = 0;
            if (controlsChanged) player.closeContainer();
        }
        if (controlsChanged) player.containerMenu.broadcastFullState();
        if (result.terminal()) {
            if (data.allowTerminalAttempt()) finishDeath(player, data);
        } else data.clearTerminalRetry();
        if (player.isAlive() && (controlsChanged || recovered || player.tickCount % HealthConfig.SYNC_INTERVAL_TICKS.get() == 0))
            HealthNetwork.sync(player);
    }
    private static void finishDeath(ServerPlayer player, PlayerHealthData data) {
        var type = player.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ORGAN_FAILURE);
        data.setFinalizing(true);
        try {
            // Обычный hurt -> die -> LivingDeathEvent сохраняет vanilla-дропы/respawn/статистику.
            player.hurt(new DamageSource(type), Float.MAX_VALUE);
        } finally { data.setFinalizing(false); }
    }
}
```

### src/main/java/dev/vitalstages/health/Physiology.java

```java
package dev.vitalstages.health;

/** Детерминированная модель без зависимостей от Minecraft, сети и системных часов. */
public final class Physiology {
    public static final float DT = 1.0f / 20;
    public static final int MAX_TIMER = 2_000_000;
    private Physiology() {}
    public record State(float blood, float pain, float consciousness, boolean unconscious,
                        int unconsciousTicks, boolean criticalTrauma) {
        public State {
            blood = clamp(blood, 0, 100, 100);
            pain = clamp(pain, 0, 100, 0);
            consciousness = clamp(consciousness, 0, 100, 100);
            unconsciousTicks = Math.max(0, Math.min(MAX_TIMER, unconsciousTicks));
        }
    }
    public record Rules(boolean bloodLoss, boolean temperature, float comaBloodThreshold,
            float bloodPenaltyStart, float bloodPenaltyScale, float painPenaltyScale,
            float activeBleedPenalty, float hungerPenaltyScale, float temperaturePenaltyScale,
            float fallPerSecond, float recoveryPerSecond, float painRecoveryPerSecond,
            float wakeThreshold, int minimumDownTicks, int deathWindowTicks) {}
    public record Result(State state, float targetConsciousness, boolean terminal) {}

    public static Result step(State old, float bleedingRate, float temperature, int foodLevel, Rules r) {
        float bleed = r.bloodLoss ? clamp(bleedingRate, 0, 100, 0) : 0;
        // Вход — единицы/секунду. На каждом игровом тике снимаем 1/20, а не всю скорость.
        float blood = Math.max(0, old.blood - bleed * DT);
        float pain = Math.max(0, old.pain - r.painRecoveryPerSecond * DT);
        // Отключение системы не стирает кровь, но приостанавливает все её последствия.
        float effectiveBlood = r.bloodLoss ? blood : 100;
        float bloodPenalty = Math.max(0, r.bloodPenaltyStart - effectiveBlood) * r.bloodPenaltyScale;
        float tempPenalty = r.temperature
                ? Math.abs(clamp(temperature, 25, 45, 37) - 37) * r.temperaturePenaltyScale : 0;
        float hungerPenalty = (20 - Math.max(0, Math.min(20, foodLevel))) * r.hungerPenaltyScale;
        float target = clamp(100 - bloodPenalty - pain * r.painPenaltyScale
                - bleed * r.activeBleedPenalty - tempPenalty - hungerPenalty, 0, 100, 0);
        if (effectiveBlood <= r.comaBloodThreshold || old.criticalTrauma) target = 0;
        float speed = target < old.consciousness ? r.fallPerSecond : r.recoveryPerSecond;
        float consciousness = moveTowards(old.consciousness, target, speed * DT);
        boolean down = old.unconscious || consciousness <= 0;
        int ticks = down ? Math.min(MAX_TIMER, old.unconsciousTicks + 1) : 0;
        // Гистерезис исключает циклы «очнулся на один тик — снова упал».
        if (down && !old.criticalTrauma && consciousness >= r.wakeThreshold
                && ticks >= r.minimumDownTicks) { down = false; ticks = 0; }
        boolean terminal = (r.bloodLoss && blood <= 0) || (down && ticks >= r.deathWindowTicks);
        return new Result(new State(blood, pain, consciousness, down, ticks, old.criticalTrauma),
                target, terminal);
    }
    public static State knockOut(State old) {
        return new State(old.blood, old.pain, 0, true,
                old.unconscious ? old.unconsciousTicks : 0, old.criticalTrauma);
    }
    public static float clamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    public static float moveTowards(float value, float target, float delta) {
        // Достигает ровно нуля, в отличие от асимптотической lerp.
        return value < target ? Math.min(target, value + Math.max(0, delta))
                : Math.max(target, value - Math.max(0, delta));
    }
}
```

### src/main/java/dev/vitalstages/event/LifecycleHandler.java

```java
package dev.vitalstages.event;
import dev.vitalstages.VitalStages;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.PlayerHealthData;
import dev.vitalstages.network.HealthNetwork;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = VitalStages.MOD_ID)
public final class LifecycleHandler {
    private LifecycleHandler() {}
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Death-clone = новая жизнь; возвращение из End = новая сущность со старой медициной.
        // Заменяем целиком, не дописываем раны поверх уже скопированного attachment.
        player.setData(HealthAttachments.HEALTH, event.isWasDeath() ? new PlayerHealthData()
                : HealthAttachments.get(event.getOriginal()).copy());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) { sync(e); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { sync(e); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { sync(e); }
    private static void sync(PlayerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        PlayerHealthData d = HealthAttachments.get(p);
        d.refreshVanillaHealth(p.getHealth(), p.getMaxHealth(), HealthConfig.f(HealthConfig.REVIVE_HEALTH));
        TickHandler.recomputeBleeding(d); FractureEffects.refresh(p); HealthNetwork.sync(p);
    }
    // При logout ничего не очищаем. NeoForge сохраняет attachment в player NBT.
    // Офлайн-симуляции нет: reconnect продолжает прежний таймер, а не даёт новое окно.
}
```

## e. Сеть

### src/main/java/dev/vitalstages/network/HealthSyncPayload.java

```java
package dev.vitalstages.network;
import dev.vitalstages.VitalStages;
import dev.vitalstages.health.Physiology;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Owner-only S2C. Полные списки ран для другого игрока без необходимости не рассылаются. */
public record HealthSyncPayload(UUID playerId, ResourceLocation dimension, float health, float blood, float bleed,
        float consciousness, float pain, float temperature, int stage, int downTicks, int deathWindowTicks,
        int packedLimbs, int splintedMask, int openCutMask, boolean enabled, boolean delirium, float intensity) implements CustomPacketPayload {
    public static final Type<HealthSyncPayload> TYPE = new Type<>(VitalStages.id("health_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HealthSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public HealthSyncPayload decode(RegistryFriendlyByteBuf b) {
            return new HealthSyncPayload(b.readUUID(), b.readResourceLocation(), b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readFloat());
        }
        @Override public void encode(RegistryFriendlyByteBuf b, HealthSyncPayload p) {
            b.writeUUID(p.playerId); b.writeResourceLocation(p.dimension); b.writeFloat(p.health);
            b.writeFloat(p.blood); b.writeFloat(p.bleed); b.writeFloat(p.consciousness); b.writeFloat(p.pain);
            b.writeFloat(p.temperature); b.writeVarInt(p.stage); b.writeVarInt(p.downTicks);
            b.writeVarInt(p.deathWindowTicks); b.writeVarInt(p.packedLimbs);
            b.writeVarInt(p.splintedMask); b.writeVarInt(p.openCutMask);
            b.writeBoolean(p.enabled); b.writeBoolean(p.delirium); b.writeFloat(p.intensity);
        }
    };
    public HealthSyncPayload {
        health = Physiology.clamp(health, 0, 1024, 0); blood = Physiology.clamp(blood, 0, 100, 100);
        bleed = Physiology.clamp(bleed, 0, 100, 0); consciousness = Physiology.clamp(consciousness, 0, 100, 100);
        pain = Physiology.clamp(pain, 0, 100, 0); temperature = Physiology.clamp(temperature, 25, 45, 37);
        stage = Math.max(0, Math.min(2, stage)); downTicks = Math.max(0, Math.min(Physiology.MAX_TIMER, downTicks));
        deathWindowTicks = Math.max(100, Math.min(72000, deathWindowTicks)); packedLimbs &= 0xFFF; splintedMask &= 0x3F; openCutMask &= 0x3F;
        intensity = Physiology.clamp(intensity, 0, 3, 0);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```

### src/main/java/dev/vitalstages/network/HealthNetwork.java

```java
package dev.vitalstages.network;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.event.DamageEventHandler;
import dev.vitalstages.health.PlayerHealthData;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.Objects;
import java.util.function.Consumer;

public final class HealthNetwork {
    // Общий класс НЕ ссылается на Minecraft/client классы: dedicated server загружается отдельно.
    private static volatile Consumer<HealthSyncPayload> clientReceiver = payload -> {};
    private HealthNetwork() {}
    public static void installClientReceiver(Consumer<HealthSyncPayload> receiver) { clientReceiver = Objects.requireNonNull(receiver); }
    public static void register(RegisterPayloadHandlersEvent e) {
        // По умолчанию registrar выполняет handler на MAIN thread; дополнительный enqueueWork не нужен.
        e.registrar("2").playToClient(HealthSyncPayload.TYPE, HealthSyncPayload.STREAM_CODEC,
                (payload, context) -> clientReceiver.accept(payload));
    }
    public static void sync(ServerPlayer p) {
        PlayerHealthData d = HealthAttachments.get(p);
        PacketDistributor.sendToPlayer(p, new HealthSyncPayload(p.getUUID(), p.level().dimension().location(),
                d.health(), d.bloodLevel(), d.bleedingRate(), d.consciousness(), d.pain(), d.bodyTemperature(),
                d.stage(HealthConfig.f(HealthConfig.PRESYNCOPE_THRESHOLD)).ordinal(), d.unconsciousTicks(),
                HealthConfig.windowTicks(), d.packedLimbStatus(), d.splintedMask(), d.openCutMask(), DamageEventHandler.eligible(p),
                HealthConfig.ENABLE_DELIRIUM.get(), HealthConfig.f(HealthConfig.DELIRIUM_INTENSITY)));
    }
}
```

## f. Клиент

### src/main/java/dev/vitalstages/client/ClientBootstrap.java

```java
package dev.vitalstages.client;
import dev.vitalstages.VitalStages;
import dev.vitalstages.network.HealthNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
@EventBusSubscriber(modid = VitalStages.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientBootstrap {
    private ClientBootstrap() {}
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { e.register(ClientKeys.TOGGLE_HUD); }
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) {
        HealthNetwork.installClientReceiver(ClientHealthState::accept);
    }
}
```

### src/main/java/dev/vitalstages/client/ClientKeys.java

```java
package dev.vitalstages.client;
import dev.vitalstages.config.HudConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
public final class ClientKeys {
    public static final KeyMapping TOGGLE_HUD = new KeyMapping("key.vitalstages.toggle_hud",
            GLFW.GLFW_KEY_H, "key.categories.vitalstages");
    private ClientKeys() {}
    public static void tick() {
        while (TOGGLE_HUD.consumeClick()) {
            if (Minecraft.getInstance().screen != null || Minecraft.getInstance().level == null) continue;
            HudConfig.SHOW_HUD.set(!HudConfig.SHOW_HUD.get());
            HudConfig.SPEC.save();
        }
    }
}
```

### src/main/java/dev/vitalstages/client/ClientHealthState.java

```java
package dev.vitalstages.client;
import dev.vitalstages.health.LifeStage;
import dev.vitalstages.network.HealthSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
/** Только визуальная копия; не рассчитывает физиологию и не разрешает лечение. */
public final class ClientHealthState {
    private static HealthSyncPayload latest;
    private static float vignette;
    private ClientHealthState() {}
    public static void accept(HealthSyncPayload p) { latest = p; }
    public static void clear() { latest = null; vignette = 0; }
    public static HealthSyncPayload current() {
        Minecraft mc = Minecraft.getInstance();
        if (latest == null || mc.player == null || mc.level == null || !mc.player.isAlive()
                || !latest.enabled() || !mc.player.getUUID().equals(latest.playerId())
                || !mc.level.dimension().location().equals(latest.dimension())) return null;
        return latest;
    }
    public static boolean unconscious() {
        HealthSyncPayload p = current(); return p != null && p.stage() == LifeStage.UNCONSCIOUS.ordinal();
    }
    public static void tick() {
        HealthSyncPayload p = current();
        float target = p == null || !p.delirium() ? 0 : Mth.clamp(
                ((100 - p.consciousness()) * 0.008f + p.pain() * 0.002f) * p.intensity(), 0, 0.95f);
        vignette = Mth.lerp(0.25f, vignette, target);
    }
    public static float vignette() { return vignette; }
}
```

### src/main/java/dev/vitalstages/client/ClientEvents.java

```java
package dev.vitalstages.client;
import dev.vitalstages.VitalStages;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = VitalStages.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean cameraLocked;
    private static float lockedYaw, lockedPitch;
    private ClientEvents() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) { ClientHealthState.tick(); ClientKeys.tick(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { ClientHealthState.clear(); cameraLocked = false; }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn e) { ClientHealthState.clear(); cameraLocked = false; }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent e) {
        if (!ClientHealthState.unconscious()) return;
        var i = e.getInput();
        i.leftImpulse = 0; i.forwardImpulse = 0; i.up = false; i.down = false; i.left = false; i.right = false;
        i.jumping = false; i.shiftKeyDown = false; e.getEntity().setSprinting(false);
    }
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        if (ClientHealthState.unconscious()) { e.setCanceled(true); e.setSwingHand(false); }
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (!ClientHealthState.unconscious()) { cameraLocked = false; return; }
        if (!cameraLocked) { lockedYaw = e.getYaw(); lockedPitch = e.getPitch(); cameraLocked = true; }
        e.setYaw(lockedYaw); e.setPitch(lockedPitch); e.setRoll(0);
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post e) {
        // Рисуем вне условного health-layer: F1 не является способом выйти из blackout.
        VitalsOverlay.render(e.getGuiGraphics());
    }
}
```

### src/main/java/dev/vitalstages/client/VitalsOverlay.java

```java
package dev.vitalstages.client;
import dev.vitalstages.network.HealthSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** POC без подмены чужого post-chain. Полный GLSL pipeline намеренно отложен. */
public final class VitalsOverlay {
    private VitalsOverlay() {}
    public static void render(GuiGraphics gui) {
        HealthSyncPayload p = ClientHealthState.current(); if (p == null) return;
        Minecraft mc = Minecraft.getInstance(); int w = gui.guiWidth(), h = gui.guiHeight();
        if (ClientHealthState.unconscious()) {
            gui.fill(0, 0, w, h, 0xFF000000);
            gui.drawCenteredString(mc.font, Component.translatable("vitalstages.unconscious"), w / 2, h / 2 - 12, 0xFFFFFFFF);
            int seconds = Math.max(0, (p.deathWindowTicks() - p.downTicks() + 19) / 20);
            gui.drawCenteredString(mc.font, Component.translatable("vitalstages.rescue_window", seconds), w / 2, h / 2 + 4, 0xFFB0B0B0);
            return;
        }
        int alpha = Math.round(ClientHealthState.vignette() * 255);
        int bx = Math.max(1, w / 5), by = Math.max(1, h / 4);
        gui.fillGradient(0, 0, w, by, alpha << 24, 0);
        gui.fillGradient(0, h - by, w, h, 0, alpha << 24);
        // GuiGraphics имеет вертикальный gradient; горизонтальные края — 32 недорогие полосы.
        for (int i = 0; i < 32; i++) {
            int a = Math.round(alpha * (1 - (i + 0.5f) / 32));
            int left = bx * i / 32, right = bx * (i + 1) / 32;
            gui.fill(left, 0, right, h, a << 24); gui.fill(w - right, 0, w - left, h, a << 24);
        }
        AnatomyHud.render(gui, p);
    }
}
```

### src/main/java/dev/vitalstages/client/AnatomyHud.java

```java
package dev.vitalstages.client;

import dev.vitalstages.config.HudConfig;
import dev.vitalstages.health.BodyPart;
import dev.vitalstages.health.LimbStatus;
import dev.vitalstages.network.HealthSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Анатомия дополняет vanilla HUD. Символы дублируют цвет; маленький экран уменьшает панель целиком. */
public final class AnatomyHud {
    private static final int WIDTH = 144, HEIGHT = 192;
    private static final int TEXT = 0xFFF2F1EC, MUTED = 0xFFBAC1C8;
    private AnatomyHud() {}
    public static void render(GuiGraphics gui, HealthSyncPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !HudConfig.SHOW_HUD.get()) return;
        float scale = Math.min(HudConfig.SCALE.get().floatValue(), Math.min(
                (gui.guiWidth() - 16) / (float) WIDTH, (gui.guiHeight() - 56) / (float) HEIGHT));
        if (scale <= 0) return;
        int x = Mth.clamp(HudConfig.X.get(), 0, Math.max(0, gui.guiWidth() - (int) Math.ceil(WIDTH * scale)));
        int y = Mth.clamp(HudConfig.Y.get(), 0, Math.max(0, gui.guiHeight() - 48 - (int) Math.ceil(HEIGHT * scale)));
        gui.pose().pushPose();
        try {
            gui.pose().translate(x, y, 0);
            gui.pose().scale(scale, scale, 1);
            gui.fill(0, 0, WIDTH, HEIGHT, 0xE8192027);
            gui.fill(0, 0, 3, HEIGHT, 0xFF629CC7);
            gui.drawString(mc.font, Component.translatable("vitalstages.hud.title"), 10, 7, TEXT);
            bar(gui, 24, p.blood(), 0xFFE97366, "vitalstages.hud.blood");
            bar(gui, 46, p.consciousness(), 0xFF74AEE8, "vitalstages.hud.consciousness");
            part(gui, p, BodyPart.HEAD, 62, 68, 22, 16);
            part(gui, p, BodyPart.TORSO, 50, 88, 46, 28);
            part(gui, p, BodyPart.LEFT_ARM, 25, 88, 21, 28);
            part(gui, p, BodyPart.RIGHT_ARM, 100, 88, 21, 28);
            part(gui, p, BodyPart.LEFT_LEG, 50, 120, 21, 30);
            part(gui, p, BodyPart.RIGHT_LEG, 75, 120, 21, 30);
            gui.drawString(mc.font, Component.translatable("vitalstages.hud.legend_fracture"), 8, 157, MUTED);
            gui.drawString(mc.font, Component.translatable("vitalstages.hud.legend_wound"), 8, 168, MUTED);
            gui.drawString(mc.font, Component.translatable("vitalstages.hud.footer",
                    Math.round(p.health() * 10) / 10.0f, Math.round(p.temperature() * 10) / 10.0f), 8, 180, MUTED);
        } finally { gui.pose().popPose(); }
    }
    private static void bar(GuiGraphics gui, int y, float value, int color, String label) {
        var font = Minecraft.getInstance().font;
        gui.drawString(font, Component.translatable(label, Math.round(value)), 8, y - 4, TEXT);
        gui.fill(8, y + 7, 136, y + 11, 0xFF38424E);
        int width = Math.round(128 * Mth.clamp(value / 100, 0, 1));
        if (width > 0) gui.fill(8, y + 7, 8 + width, y + 11, color);
    }
    private static void part(GuiGraphics gui, HealthSyncPayload p, BodyPart part, int x, int y, int w, int h) {
        var font = Minecraft.getInstance().font;
        int status = (p.packedLimbs() >>> (part.ordinal() * 2)) & 3;
        boolean splinted = status == LimbStatus.FRACTURED.ordinal() && (p.splintedMask() & part.bit()) != 0;
        boolean bleeding = (p.openCutMask() & part.bit()) != 0;
        int color = splinted ? 0xFF7BC7AA : status == LimbStatus.FRACTURED.ordinal() ? 0xFFE97366
                : status == LimbStatus.BRUISED.ordinal() ? 0xFFE4AD6A : 0xFF899BA8;
        String mark = splinted ? "+" : status == LimbStatus.FRACTURED.ordinal() ? "x"
                : status == LimbStatus.BRUISED.ordinal() ? "!" : "-";
        gui.fill(x, y, x + w, y + h, color);
        gui.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF242D36);
        Component label = Component.translatable("vitalstages.part." + part.name().toLowerCase(java.util.Locale.ROOT));
        if (h < 22) {
            gui.drawCenteredString(font, label.copy().append((mark.equals("-") ? "" : mark) + (bleeding ? "B" : "")), x + w / 2, y + 4, TEXT);
        } else {
            gui.drawCenteredString(font, label, x + w / 2, y + 4, TEXT);
            gui.drawCenteredString(font, mark + (bleeding ? "B" : ""), x + w / 2, y + h - 11, color);
        }
        // Внешняя красная полоска + отдельный символ B отличают открытую рану от одного перелома.
        if (bleeding) {
            gui.fill(x - 3, y, x - 1, y + h, 0xFFE97366);
            // B уже нарисован внутри ячейки: не перекрывает соседнюю часть тела.
        }
    }
}
```

## g. Предметы

### src/main/java/dev/vitalstages/item/MedicalItem.java

```java
package dev.vitalstages.item;

import dev.vitalstages.event.DamageEventHandler;
import dev.vitalstages.event.FractureEffects;
import dev.vitalstages.event.TickHandler;
import dev.vitalstages.health.PlayerHealthData;
import dev.vitalstages.network.HealthNetwork;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Одна серверная проверка для бинта и шины: новые лекарства не обходят правила через другой Item. */
public abstract class MedicalItem extends Item {
    protected MedicalItem(Properties properties) { super(properties); }
    protected abstract boolean treat(PlayerHealthData data);
    protected abstract int cooldownTicks();
    protected abstract String successKey();
    protected abstract String failureKey();
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer p)) return InteractionResultHolder.fail(stack);
        return apply(p, p, stack) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player actor, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player)) return InteractionResult.PASS;
        if (actor.level().isClientSide) return InteractionResult.SUCCESS;
        if (!(actor instanceof ServerPlayer a) || !(target instanceof ServerPlayer p)) return InteractionResult.FAIL;
        return apply(a, p, stack) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
    private boolean apply(ServerPlayer actor, ServerPlayer patient, ItemStack stack) {
        if (!actor.isAlive() || !patient.isAlive() || actor.isSpectator() || patient.isSpectator()
                || stack.isEmpty() || !stack.is(this) || actor.level() != patient.level()
                || actor.distanceToSqr(patient) > 9 || !actor.hasLineOfSight(patient)
                || actor.getCooldowns().isOnCooldown(this)
                || (DamageEventHandler.eligible(actor) && HealthAttachments.get(actor).unconscious())) return false;
        PlayerHealthData d = HealthAttachments.get(patient);
        if (!treat(d)) {
            actor.displayClientMessage(Component.translatable(failureKey()), true); return false;
        }
        if (!actor.getAbilities().instabuild) stack.shrink(1);
        actor.getCooldowns().addCooldown(this, cooldownTicks());
        TickHandler.recomputeBleeding(d); FractureEffects.refresh(patient); HealthNetwork.sync(patient);
        actor.displayClientMessage(Component.translatable(successKey()), true);
        return true;
    }
}
```

### src/main/java/dev/vitalstages/item/BandageItem.java

```java
package dev.vitalstages.item;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.PlayerHealthData;
public final class BandageItem extends MedicalItem {
    public BandageItem(Properties properties) { super(properties); }
    @Override protected boolean treat(PlayerHealthData d) { return d.bandageWorstOpenCut(); }
    @Override protected int cooldownTicks() { return HealthConfig.BANDAGE_COOLDOWN_TICKS.get(); }
    @Override protected String successKey() { return "vitalstages.bandaged"; }
    @Override protected String failureKey() { return "vitalstages.no_open_cut"; }
}
```

### src/main/java/dev/vitalstages/item/SplintItem.java

```java
package dev.vitalstages.item;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.PlayerHealthData;
public final class SplintItem extends MedicalItem {
    public SplintItem(Properties properties) { super(properties); }
    @Override protected boolean treat(PlayerHealthData d) { return d.splintWorstFracture(); }
    @Override protected int cooldownTicks() { return HealthConfig.SPLINT_COOLDOWN_TICKS.get(); }
    @Override protected String successKey() { return "vitalstages.splinted"; }
    @Override protected String failureKey() { return "vitalstages.no_fracture"; }
}
```

### src/main/java/dev/vitalstages/registry/ModItems.java

```java
package dev.vitalstages.registry;
import dev.vitalstages.VitalStages;
import dev.vitalstages.item.BandageItem;
import dev.vitalstages.item.SplintItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VitalStages.MOD_ID);
    public static final DeferredItem<BandageItem> BANDAGE = ITEMS.register("bandage",
            () -> new BandageItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<SplintItem> SPLINT = ITEMS.register("splint",
            () -> new SplintItem(new Item.Properties().stacksTo(8)));
    private ModItems() {}
}
```

## h. Инфраструктура

### src/main/java/dev/vitalstages/VitalStages.java

```java
package dev.vitalstages;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.config.HudConfig;
import dev.vitalstages.network.HealthNetwork;
import dev.vitalstages.registry.HealthAttachments;
import dev.vitalstages.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(VitalStages.MOD_ID)
public final class VitalStages {
    public static final String MOD_ID = "vitalstages";
    public VitalStages(IEventBus modBus, ModContainer container) {
        HealthAttachments.TYPES.register(modBus); ModItems.ITEMS.register(modBus);
        modBus.addListener(HealthAttachments::registerCapabilities);
        modBus.addListener(HealthNetwork::register);
        container.registerConfig(ModConfig.Type.SERVER, HealthConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, HudConfig.SPEC);
    }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }
}
```

### src/main/java/dev/vitalstages/config/HealthConfig.java

```java
package dev.vitalstages.config;

import dev.vitalstages.health.Physiology;
import net.neoforged.neoforge.common.ModConfigSpec;

/** SERVER-конфиг хранится в world/serverconfig и синхронизируется NeoForge. */
public final class HealthConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_BLOOD_LOSS, ENABLE_FRACTURES,
            ENABLE_DELIRIUM, ENABLE_TEMPERATURE, ENABLE_INFECTION;
    public static final ModConfigSpec.DoubleValue HEALTH_DAMAGE_FRACTION, BLEED_RATE_MULTIPLIER,
            BASE_BLEED_PER_SEVERITY, MAX_BLEED, DELIRIUM_INTENSITY, PAIN_PER_SEVERITY,
            SHOCK_PER_SEVERITY, PAIN_RECOVERY, CONSCIOUSNESS_FALL, CONSCIOUSNESS_RECOVERY,
            WAKE_THRESHOLD, PRESYNCOPE_THRESHOLD, CRITICAL_BLOOD, BLOOD_PENALTY_START,
            BLOOD_PENALTY_SCALE, PAIN_PENALTY_SCALE, ACTIVE_BLEED_PENALTY,
            HUNGER_PENALTY_SCALE, TEMPERATURE_PENALTY_SCALE, REVIVE_HEALTH,
            FRACTURE_DAMAGE_THRESHOLD, INFECTION_HAZARD_PER_SECOND;
    public static final ModConfigSpec.IntValue DEATH_WINDOW_SECONDS, MINIMUM_DOWN_TICKS,
            SYNC_INTERVAL_TICKS, BANDAGE_COOLDOWN_TICKS, INFECTION_DELAY_SECONDS;
    public static final ModConfigSpec.DoubleValue LEG_MOVEMENT_FACTOR, ARM_ATTACK_FACTOR,
            ARM_MINING_FACTOR, HEAVY_FRACTURE_THRESHOLD, HEAVY_FRACTURE_CHANCE;
    public static final ModConfigSpec.IntValue SPLINT_COOLDOWN_TICKS, BRUISE_HEAL_SECONDS,
            CUT_HEAL_SECONDS, BURN_HEAL_SECONDS, FRACTURE_HEAL_SECONDS;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("systems");
        ENABLE_BLOOD_LOSS = b.define("enableBloodLoss", true);
        ENABLE_FRACTURES = b.comment("Создание переломов и штрафы. Отключение не стирает раны/фиксацию.").define("enableFractures", true);
        ENABLE_DELIRIUM = b.comment("Виньетка; обязательный blackout не отключает.").define("enableDelirium", true);
        ENABLE_TEMPERATURE = b.comment("Штраф от сохранённой температуры. Климат вне MVP.").define("enableTemperature", false);
        ENABLE_INFECTION = b.comment("Только таймер и риск. Болезнь/антисептик вне MVP.").define("enableInfection", false);
        b.pop().push("damage");
        HEALTH_DAMAGE_FRACTION = b.defineInRange("healthDamageFraction", 0.6, 0.05, 1.0);
        PAIN_PER_SEVERITY = b.defineInRange("painPerSeverity", 14.0, 0, 100);
        SHOCK_PER_SEVERITY = b.defineInRange("shockPerSeverity", 8.0, 0, 100);
        FRACTURE_DAMAGE_THRESHOLD = b.defineInRange("fractureDamageThreshold", 3.0, 0.1, 1000);
        b.pop().push("blood");
        BLEED_RATE_MULTIPLIER = b.defineInRange("bleedRateMultiplier", 1.0, 0, 20);
        BASE_BLEED_PER_SEVERITY = b.defineInRange("baseBleedPerSeverity", 0.4, 0, 10);
        MAX_BLEED = b.defineInRange("maxBleedingPerSecond", 8.0, 0.01, 100);
        CRITICAL_BLOOD = b.defineInRange("comaBloodThreshold", 15.0, 0, 50);
        BLOOD_PENALTY_START = b.defineInRange("bloodPenaltyStart", 65.0, 0, 100);
        BLOOD_PENALTY_SCALE = b.defineInRange("bloodPenaltyScale", 2.0, 0, 10);
        b.pop().push("consciousness");
        DEATH_WINDOW_SECONDS = b.defineInRange("deathWindowSeconds", 60, 5, 3600);
        MINIMUM_DOWN_TICKS = b.defineInRange("minimumDownTicks", 40, 0, 100);
        WAKE_THRESHOLD = b.defineInRange("wakeThreshold", 10.0, 1, 50);
        PRESYNCOPE_THRESHOLD = b.defineInRange("presyncopeThreshold", 35.0, 1, 99);
        CONSCIOUSNESS_FALL = b.defineInRange("fallPerSecond", 20.0, 0.1, 100);
        CONSCIOUSNESS_RECOVERY = b.defineInRange("recoveryPerSecond", 4.0, 0.1, 100);
        PAIN_RECOVERY = b.defineInRange("painRecoveryPerSecond", 0.35, 0, 20);
        PAIN_PENALTY_SCALE = b.defineInRange("painPenaltyScale", 0.25, 0, 2);
        ACTIVE_BLEED_PENALTY = b.defineInRange("activeBleedPenalty", 12.0, 0, 100);
        HUNGER_PENALTY_SCALE = b.defineInRange("hungerPenaltyScale", 0.45, 0, 5);
        TEMPERATURE_PENALTY_SCALE = b.defineInRange("temperaturePenaltyScale", 10.0, 0, 100);
        REVIVE_HEALTH = b.defineInRange("reviveHealth", 4.0, 1, 1024);
        b.pop().push("presentation");
        DELIRIUM_INTENSITY = b.defineInRange("deliriumIntensityMultiplier", 1.0, 0, 3);
        SYNC_INTERVAL_TICKS = b.defineInRange("syncIntervalTicks", 5, 1, 40);
        b.pop().push("medicine");
        BANDAGE_COOLDOWN_TICKS = b.defineInRange("bandageCooldownTicks", 30, 1, 200);
        INFECTION_DELAY_SECONDS = b.defineInRange("infectionDelaySeconds", 600, 1, 86400);
        INFECTION_HAZARD_PER_SECOND = b.defineInRange("infectionHazardPerSecond", 0.0001, 0, 1);
        b.pop().push("fractures");
        LEG_MOVEMENT_FACTOR = b.comment("Множитель на одну нефиксированную сломанную ногу.")
                .defineInRange("movementPerLeg", 0.55, 0.05, 1.0);
        ARM_ATTACK_FACTOR = b.defineInRange("attackSpeedPerArm", 0.65, 0.05, 1.0);
        ARM_MINING_FACTOR = b.defineInRange("miningSpeedPerArm", 0.60, 0.05, 1.0);
        HEAVY_FRACTURE_THRESHOLD = b.comment("Реальный урон HP после absorption для дополнительного перелома конечности.")
                .defineInRange("heavyHitThreshold", 6.0, 0.1, 1000);
        HEAVY_FRACTURE_CHANCE = b.defineInRange("heavyHitFractureChance", 0.35, 0, 1);
        SPLINT_COOLDOWN_TICKS = b.defineInRange("splintCooldownTicks", 40, 1, 200);
        b.pop().push("recovery");
        BRUISE_HEAL_SECONDS = b.defineInRange("bruiseSeconds", 120, 5, 86400);
        CUT_HEAL_SECONDS = b.comment("Только перевязанный CUT.").defineInRange("bandagedCutSeconds", 180, 5, 86400);
        BURN_HEAL_SECONDS = b.defineInRange("burnSeconds", 300, 5, 86400);
        FRACTURE_HEAL_SECONDS = b.comment("Для рук/ног нужна шина; фиксация снимает штраф сразу, кость заживает позже.")
                .defineInRange("splintedFractureSeconds", 600, 5, 86400);
        b.pop(); SPEC = b.build();
    }
    private HealthConfig() {}
    public static float f(ModConfigSpec.DoubleValue v) { return v.get().floatValue(); }
    public static int windowTicks() { return DEATH_WINDOW_SECONDS.get() * 20; }
    public static Physiology.Rules rules() {
        return new Physiology.Rules(ENABLE_BLOOD_LOSS.get(), ENABLE_TEMPERATURE.get(),
                f(CRITICAL_BLOOD), f(BLOOD_PENALTY_START), f(BLOOD_PENALTY_SCALE),
                f(PAIN_PENALTY_SCALE), f(ACTIVE_BLEED_PENALTY), f(HUNGER_PENALTY_SCALE),
                f(TEMPERATURE_PENALTY_SCALE), f(CONSCIOUSNESS_FALL), f(CONSCIOUSNESS_RECOVERY),
                f(PAIN_RECOVERY), f(WAKE_THRESHOLD), MINIMUM_DOWN_TICKS.get(), windowTicks());
    }
}
```

### src/main/java/dev/vitalstages/config/HudConfig.java

```java
package dev.vitalstages.config;
import net.neoforged.neoforge.common.ModConfigSpec;
/** Только отображение: отключение HUD не меняет серверную физиологию/обморок. */
public final class HudConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SHOW_HUD;
    public static final ModConfigSpec.DoubleValue SCALE;
    public static final ModConfigSpec.IntValue X, Y;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        SHOW_HUD = b.define("showAnatomyHud", true);
        SCALE = b.defineInRange("hudScale", 1.0, 0.5, 1.5);
        X = b.defineInRange("hudX", 8, 0, 4096);
        Y = b.defineInRange("hudY", 8, 0, 4096);
        SPEC = b.build();
    }
    private HudConfig() {}
}
```

### src/main/java/dev/vitalstages/mixin/UnconsciousInputMixin.java

```java
package dev.vitalstages.mixin;
import dev.vitalstages.event.DamageEventHandler;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Серверный gate: модифицированный клиент не может просто включить атаки/самолечение. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class UnconsciousInputMixin {
    @Shadow public ServerPlayer player;
    @Unique private boolean vitalstages$locked() {
        // HEAD также исполняется на Netty до PacketUtils: attachment читаем ТОЛЬКО на серверном потоке.
        return player.getServer() != null && player.getServer().isSameThread() && player.isAlive()
                && DamageEventHandler.eligible(player) && HealthAttachments.get(player).unconscious();
    }
    @ModifyVariable(method = "handleMovePlayer", at = @At("HEAD"), argsOnly = true)
    private ServerboundMovePlayerPacket vitalstages$filterMovement(ServerboundMovePlayerPacket packet) {
        if (!vitalstages$locked() || player.isPassenger()) return packet;
        // Не отменяем обработчик целиком: падение и vanilla-проверки продолжают работать.
        // Горизонтальный ввод, прыжок и поворот запрещены; серверные teleport не перехватываются.
        // Вертикальные пакеты остаются под обычными vanilla-проверками; это не anti-cheat.
        return new ServerboundMovePlayerPacket.PosRot(player.getX(), Math.min(player.getY(), packet.getY(player.getY())),
                player.getZ(), player.getYRot(), player.getXRot(), packet.isOnGround());
    }
    @Inject(method = {"handlePlayerAction", "handleInteract", "handleUseItem", "handleUseItemOn",
            "handleContainerButtonClick", "handlePlaceRecipe", "handlePlayerCommand", "handlePlayerInput",
            "handleMoveVehicle", "handlePaddleBoat", "handlePickItem", "handlePlayerAbilities"},
            at = @At("HEAD"), cancellable = true)
    private void vitalstages$blockActions(CallbackInfo ci) { if (vitalstages$locked()) ci.cancel(); }
    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void vitalstages$blockInventory(CallbackInfo ci) {
        if (!vitalstages$locked()) return;
        player.containerMenu.broadcastFullState(); ci.cancel();
    }
    // KeepAlive, чат, команды, teleport-confirm не блокируются: игрок может попросить помощи/выйти.
}
```

### src/main/java/dev/vitalstages/event/DebugCommands.java

```java
package dev.vitalstages.event;

import dev.vitalstages.VitalStages;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.BodyPart;
import dev.vitalstages.health.PlayerHealthData;
import dev.vitalstages.health.Wound;
import dev.vitalstages.network.HealthNetwork;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Только OP level 2. Нужны для повторяемого ручного QA, не доступны обычному клиенту. */
@EventBusSubscriber(modid = VitalStages.MOD_ID)
public final class DebugCommands {
    private DebugCommands() {}
    @SubscribeEvent public static void register(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
        var target = Commands.argument("player", EntityArgument.player());
        for (BodyPart part : BodyPart.values()) if (part.isLimb()) {
            target.then(Commands.literal(part.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx -> {
                ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                var d = HealthAttachments.get(p);
                d.addImpact(Wound.fresh(part, Wound.Type.FRACTURE, 2), 0, 0);
                FractureEffects.refresh(p); HealthNetwork.sync(p);
                ctx.getSource().sendSuccess(() -> Component.translatable("vitalstages.debug.fracture", p.getDisplayName()), false);
                return 1;
            }));
        }
        var reset = Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
            p.setData(HealthAttachments.HEALTH, new PlayerHealthData()); p.setHealth(p.getMaxHealth());
            HealthAttachments.get(p).refreshVanillaHealth(p.getHealth(), p.getMaxHealth(), HealthConfig.f(HealthConfig.REVIVE_HEALTH));
            FractureEffects.refresh(p); HealthNetwork.sync(p);
            ctx.getSource().sendSuccess(() -> Component.translatable("vitalstages.debug.reset", p.getDisplayName()), false);
            return 1;
        }));
        var debug = Commands.literal("debug").then(Commands.literal("fracture").then(target)).then(reset);
        e.getDispatcher().register(Commands.literal("vitalstages").requires(s -> s.hasPermission(2)).then(debug));
    }
}
```

## i. Сборка, тесты и текстовые ресурсы

### settings.gradle

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = 'https://maven.neoforged.net/releases' }
    }
}
rootProject.name = 'vitalstages'
```

### build.gradle

```groovy
plugins {
    id 'java-library'
    id 'net.neoforged.moddev' version '2.0.143'
}
version = project.mod_version
group = 'dev.vitalstages'
base { archivesName = 'vitalstages' }
java.toolchain.languageVersion = JavaLanguageVersion.of(21)
java.withSourcesJar()
neoForge {
    version = project.neo_version
    runs {
        client { client() }
        server { server(); programArgument '--nogui' }
    }
    mods { vitalstages { sourceSet sourceSets.main } }
}
tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release = 21
}
processResources {
    inputs.property 'version', project.version
    filesMatching('META-INF/neoforge.mods.toml') { expand version: project.version }
}
// Проверяем чистую модель без запуска Minecraft.
tasks.register('physiologyTest', JavaExec) {
    dependsOn testClasses
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'dev.vitalstages.health.PhysiologyTest'
}
check.dependsOn physiologyTest
wrapper {
    gradleVersion = '9.2.1'
    distributionType = Wrapper.DistributionType.BIN
}

// Gradle 9: main()-тесты исполняются JavaExec, а не JUnit/TestNG.
tasks.named('test') { failOnNoDiscoveredTests = false }
tasks.register('mvp2Test', JavaExec) {
    dependsOn testClasses
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'dev.vitalstages.health.Mvp2CoreTest'
}
check.dependsOn mvp2Test

// Эта задача использует настоящий Minecraft NBT; её нельзя подменять офлайн-заглушками.
tasks.register('mvp2NbtTest', JavaExec) {
    dependsOn testClasses
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'dev.vitalstages.health.Mvp2NbtTest'
}
check.dependsOn mvp2NbtTest
```

### gradle.properties

```text
org.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8
org.gradle.daemon=false
mod_version=0.2.0
neo_version=21.1.233
```

### src/test/java/dev/vitalstages/health/Mvp2CoreTest.java

```java
package dev.vitalstages.health;

public final class Mvp2CoreTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void near(double a, double b, String m) { check(Math.abs(a - b) < 1e-6, m + ": " + a + " != " + b); }
    private static FractureProfile profile(int broken, int splinted, boolean on) {
        return FractureProfile.calculate(broken, splinted, on, 0.55, 0.65, 0.60);
    }
    public static void main(String[] args) {
        int legs = BodyPart.LEFT_LEG.bit() | BodyPart.RIGHT_LEG.bit();
        int arms = BodyPart.LEFT_ARM.bit() | BodyPart.RIGHT_ARM.bit();
        near(profile(BodyPart.LEFT_LEG.bit(), 0, true).movement(), 0.55, "Одна нога");
        near(profile(legs, 0, true).movement(), 0.3025, "Две ноги: произведение, не полный паралич");
        near(profile(legs, BodyPart.LEFT_LEG.bit(), true).movement(), 0.55, "Шина снимает один штраф");
        near(profile(legs, legs, true).movement(), 1, "Обе шины возвращают обычный множитель");
        near(profile(arms, 0, true).attackSpeed(), 0.4225, "Две руки: атака");
        near(profile(arms, 0, true).miningSpeed(), 0.36, "Две руки: добыча");
        near(profile(63, 0, false).movement(), 1, "Выключение механики снимает штрафы");
        near(profile(BodyPart.HEAD.bit() | BodyPart.TORSO.bit(), 0, true).attackSpeed(), 1, "Голова/торс не считаются руками");
        for (int f = 0; f < 64; f++) for (int s = 0; s < 64; s++) {
            var p = profile(f, s, true);
            int untreated = f & ~s;
            check(p.untreatedLegs() == Integer.bitCount(untreated & legs), "Маска ног");
            check(p.untreatedArms() == Integer.bitCount(untreated & arms), "Маска рук");
            check(p.movement() > 0 && p.movement() <= 1, "Граница скорости");
            check(p.attackSpeed() > 0 && p.attackSpeed() <= 1, "Граница атаки");
            check(p.miningSpeed() > 0 && p.miningSpeed() <= 1, "Граница добычи");
        }
        Wound fracture = Wound.fresh(BodyPart.LEFT_LEG, Wound.Type.FRACTURE, 2);
        check(fracture.needsSplint(), "Нужна шина");
        check(!fracture.treatmentAllowsHealing(), "Без шины сращивания нет");
        Wound splinted = fracture.splint();
        check(splinted.isSplinted() && splinted.treatmentAllowsHealing(), "Фиксация разрешает сращивание");
        check(!fracture.isSplinted(), "Исходная рана неизменна");
        check(splinted.id().equals(fracture.id()), "ID раны сохраняется");
        check(!splinted.needsSplint(), "Повторная шина не нужна");
        check(splinted.splint() == splinted, "Идемпотентность фиксации");
        int ticks = 0;
        for (int i = 0; i < 12000; i++) {
            var step = RecoveryClock.advance(ticks, 12000, splinted.treatmentAllowsHealing());
            ticks = step.ticks();
            check(step.healed() == (i == 11999), "Ровно десять игровых минут");
        }
        check(RecoveryClock.advance(500, 12000, false).ticks() == 500, "Пауза не обнуляет прогресс");
        check(!RecoveryClock.advance(500, 100, false).healed(), "Нестабильный организм не лечится при смене конфига");
        check(RecoveryClock.advance(Integer.MAX_VALUE, Physiology.MAX_TIMER, true).healed(), "Нет overflow таймера");
        Wound progressing = splinted.withHealingTicks(500);
        Wound newHit = progressing.mergeImpact(Wound.fresh(BodyPart.LEFT_LEG, Wound.Type.FRACTURE, 3));
        check(newHit.healingTicks() == 0 && !newHit.isSplinted(), "Новый удар сбрасывает фиксацию/прогресс");
        near(newHit.severity(), 5, "Тяжесть складывается");
        check(progressing.healingTicks() == 500 && progressing.isSplinted(), "Старый snapshot не мутировал");
        Wound cut = Wound.fresh(BodyPart.RIGHT_ARM, Wound.Type.CUT, 2);
        check(cut.bleedWeight() > 0 && !cut.treatmentAllowsHealing(), "Открытая рана не заживает сама");
        Wound closed = cut.bandage().withHealingTicks(123);
        check(closed.bleedWeight() == 0 && closed.treatmentAllowsHealing(), "Бинт закрывает рану");
        check(closed.tickInfection(true, 0, 1).healingTicks() == 123, "Инфекционный тик не стирает заживление");
        Wound opened = closed.mergeImpact(Wound.fresh(BodyPart.RIGHT_ARM, Wound.Type.CUT, 1));
        check(opened.isOpenCut() && opened.healingTicks() == 0, "Повторное ранение открывает CUT");
        Wound corrupt = new Wound(java.util.UUID.randomUUID(), BodyPart.HEAD, Wound.Type.BURN,
                Float.NaN, false, true, Integer.MAX_VALUE, Float.NaN, Integer.MAX_VALUE);
        check(Float.isFinite(corrupt.severity()) && Float.isFinite(corrupt.infectionRisk()), "NaN защита");
        check(!corrupt.isSplinted(), "Нельзя фиксировать ожог головы шиной");
        check(corrupt.healingTicks() == Physiology.MAX_TIMER, "Ограничение загруженного прогресса");
        boolean rejected = false;
        try { cut.mergeImpact(fracture); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Разные раны не объединяются");
        System.out.println("PASS: " + checks + " MVP-2 checks (fractures, splints, wound recovery)");
    }
}
```

### src/test/java/dev/vitalstages/health/Mvp2NbtTest.java

```java
package dev.vitalstages.health;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.UUID;

/** Исполняется Gradle с настоящим Minecraft classpath. Никаких NBT-заглушек. */
public final class Mvp2NbtTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("version", 1); legacy.putFloat("bloodLevel", 37);
        legacy.putFloat("consciousness", 0); legacy.putBoolean("unconscious", true);
        legacy.putBoolean("criticalTrauma", true); legacy.putInt("unconsciousTicks", 230);
        CompoundTag oldCut = new CompoundTag();
        UUID woundId = UUID.randomUUID(); oldCut.putUUID("id", woundId);
        oldCut.putString("bodyPart", "LEFT_ARM"); oldCut.putString("type", "CUT"); oldCut.putFloat("severity", 2);
        oldCut.putInt("infectionTimer", 300); oldCut.putFloat("infectionRisk", 0.25f);
        ListTag wounds = new ListTag(); wounds.add(oldCut); legacy.put("wounds", wounds);
        PlayerHealthData data = new PlayerHealthData();
        // provider не используется этим форматом: он хранит только примитивы/enum, без registry references.
        data.deserializeNBT(null, legacy);
        check(data.bloodLevel() == 37 && data.unconsciousTicks() == 230, "MVP-1: кровь и окно сохранены");
        check(data.wounds().size() == 1 && data.wounds().get(0).healingTicks() == 0, "Новый прогресс по умолчанию нулевой");
        check(data.wounds().get(0).id().equals(woundId), "UUID не меняется при миграции");
        data.enterCriticalTrauma(); check(data.unconsciousTicks() == 230, "Миграция не продлевает окно");
        var saved = data.serializeNBT(null); check(saved.getInt("version") == 2, "Записываем версию 2");
        var roundTrip = new PlayerHealthData(); roundTrip.deserializeNBT(null, saved);
        check(roundTrip.physiologyState().equals(data.physiologyState()), "Организм пережил NBT round-trip");
        check(roundTrip.wounds().equals(data.wounds()), "Раны пережили NBT round-trip");
        PlayerHealthData copy = data.copy();
        check(copy.bandageWorstOpenCut(), "Копия получает перевязку");
        check(data.wounds().get(0).isOpenCut(), "Clone не разделяет изменяемый список");
        check(copy.unconsciousTicks() == 230 && data.unconsciousTicks() == 230, "Clone не меняет таймер");
        Wound progressed = Wound.fresh(BodyPart.RIGHT_LEG, Wound.Type.FRACTURE, 2).splint().withHealingTicks(999);
        check(WoundNbtCodec.read(WoundNbtCodec.write(progressed)).orElseThrow().equals(progressed), "Фиксация и прогресс сохраняются");
        PlayerHealthData recovery = new PlayerHealthData();
        recovery.addImpact(Wound.fresh(BodyPart.LEFT_LEG, Wound.Type.FRACTURE, 2), 0, 0);
        check(!recovery.tickRecovery(true, 1, 1, 1, 1), "Без шины перелом остаётся");
        check(recovery.splintWorstFracture(), "Фиксируется одна рана");
        check(!recovery.splintWorstFracture(), "Нет второй нефиксированной раны");
        check(recovery.tickRecovery(true, 1, 1, 1, 1) && recovery.wounds().isEmpty(), "Сросшийся перелом удаляется");
        check(recovery.limbStatus(BodyPart.LEFT_LEG) == LimbStatus.HEALTHY, "Конечность снова здорова");
        PlayerHealthData many = new PlayerHealthData();
        for (int i = 0; i < 3; i++) for (BodyPart p : BodyPart.values()) for (Wound.Type t : Wound.Type.values())
            many.addImpact(Wound.fresh(p, t, 1), 0, 0);
        check(many.wounds().size() == PlayerHealthData.MAX_WOUNDS, "Лимит 24 агрегата");
        var future = saved.copy(); future.putInt("version", 3);
        boolean rejected = false;
        try { roundTrip.deserializeNBT(null, future); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Будущий формат не затирается");
        System.out.println("PASS: " + checks + " real-NBT checks (MVP-1 migration, copy, round-trip, recovery)");
    }
}
```

### src/test/java/dev/vitalstages/health/PhysiologyTest.java

```java
package dev.vitalstages.health;

import java.util.Random;

/** Настоящие проверки production-класса Physiology, без копии формул на другом языке. */
public final class PhysiologyTest {
    private static int assertions;
    private static final Physiology.Rules RULES = new Physiology.Rules(true, false,
            15, 65, 2, 0.25f, 12, 0.45f, 10, 20, 4, 0.35f, 10, 40, 1200);
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.001f, message + ": " + actual + " != " + expected);
    }
    private static Physiology.State state(float blood, float pain, float consciousness, boolean down, int ticks, boolean critical) {
        return new Physiology.State(blood, pain, consciousness, down, ticks, critical);
    }
    private static Physiology.Result step(Physiology.State s, float bleed) {
        return Physiology.step(s, bleed, 37, 20, RULES);
    }
    public static void main(String[] args) {
        Physiology.State s = state(100, 0, 100, false, 0, false);
        for (int i = 0; i < 20; i++) s = step(s, 2).state();
        near(s.blood(), 98, "20 тиков = одна секунда кровопотери");
        for (int i = 0; i < 12000; i++) s = step(s, 0).state();
        near(s.blood(), 98, "Кровь не регенерирует");

        check(step(state(0, 0, 100, false, 0, false), 0).terminal(), "Ноль крови завершает процесс");
        Physiology.Result r = step(state(0.01f, 0, 100, false, 0, false), 10);
        near(r.state().blood(), 0, "Не допускаем отрицательную кровь");
        check(r.terminal(), "Опустошение крови не ждёт таймер");

        s = Physiology.knockOut(state(100, 0, 100, false, 999, true));
        check(s.unconsciousTicks() == 0, "Первый knockout стартует окно");
        for (int i = 0; i < 1199; i++) {
            s = Physiology.knockOut(s); // Повторные летальные удары не дают вечного окна.
            r = step(s, 0); s = r.state();
            check(!r.terminal(), "До 60 секунд ещё можно спасти");
        }
        check(s.unconsciousTicks() == 1199, "Повторный knockout не обнуляет таймер");
        check(step(s, 0).terminal(), "Смерть ровно на 1200-м тике");

        s = Physiology.knockOut(state(100, 0, 100, false, 0, false));
        for (int i = 0; i < 40; i++) s = step(s, 0).state();
        check(s.unconscious(), "Не просыпаемся раньше гистерезиса сознания");
        for (int i = 0; i < 20; i++) s = step(s, 0).state();
        check(!s.unconscious() && s.unconsciousTicks() == 0, "Самовосстановление при стабильном состоянии");

        s = state(26, 50, 0, true, 10, false);
        check(step(s, 1).targetConsciousness() == 0, "Активная потеря поддерживает обморок");
        // Эквивалент перевязки: скорость стала нулевой, кровь и HP не прибавлены.
        for (int i = 0; i < 400; i++) {
            r = step(s, 0); s = r.state(); check(!r.terminal(), "После ранней перевязки сохраняется окно");
        }
        check(!s.unconscious(), "Закрытие раны может позволить очнуться");
        near(s.blood(), 26, "Перевязка не создала кровь");
        s = state(14, 0, 0, true, 0, false);
        for (int i = 0; i < 1200; i++) { r = step(s, 0); s = r.state(); }
        check(r.terminal(), "При слишком малом объёме крови одного бинта недостаточно");

        s = state(100, 0, 0, true, 0, true);
        for (int i = 0; i < 200; i++) s = step(s, 0).state();
        check(s.unconscious() && s.consciousness() == 0, "Критическая травма требует восстановления HP");
        s = state(s.blood(), s.pain(), s.consciousness(), s.unconscious(), s.unconsciousTicks(), false);
        for (int i = 0; i < 80; i++) s = step(s, 0).state();
        check(!s.unconscious(), "После снятия критической травмы возможен выход");

        Physiology.Rules off = new Physiology.Rules(false, false, 15, 65, 2, 0.25f, 12, 0.45f, 10, 20, 4, 0.35f, 10, 40, 1200);
        s = state(0, 0, 0, true, 0, false);
        for (int i = 0; i < 80; i++) {
            r = Physiology.step(s, 100, 37, 20, off); s = r.state(); check(!r.terminal(), "Отключена вся кровяная механика");
        }
        check(!s.unconscious(), "Отключённая система не держит в коме");
        near(s.blood(), 0, "Отключение не перезаписывает сохранённую кровь");
        s = state(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, false, Integer.MAX_VALUE, false);
        check(Float.isFinite(s.blood()) && Float.isFinite(s.pain()) && Float.isFinite(s.consciousness()), "Защита NaN/Infinity");
        check(s.unconsciousTicks() == Physiology.MAX_TIMER, "Ограничение таймера");
        near(Physiology.moveTowards(0.001f, 0, 1), 0, "Достижение точного нуля");

        Random random = new Random(1211);
        for (int i = 0; i < 10000; i++) {
            s = state(random.nextFloat() * 100, random.nextFloat() * 100, random.nextFloat() * 100,
                    random.nextBoolean(), random.nextInt(1300), random.nextBoolean());
            r = Physiology.step(s, random.nextFloat() * 20, random.nextFloat() * 20 + 25, random.nextInt(21), RULES);
            var t = r.state();
            check(t.blood() >= 0 && t.blood() <= s.blood(), "Кровь монотонна и ограничена");
            check(t.consciousness() >= 0 && t.consciousness() <= 100, "Сознание ограничено");
            check(t.pain() >= 0 && t.pain() <= 100, "Боль ограничена");
            check(t.unconsciousTicks() >= 0, "Таймер не переполнен");
        }
        System.out.println("PASS: " + assertions + " checks; deterministic scenarios + 10,000 randomized steps");
    }
}
```

### scripts/test-core.sh

```sh
#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/core-tests
P=src/main/java/dev/vitalstages/health
T=src/test/java/dev/vitalstages/health
java -m jdk.compiler/com.sun.tools.javac.Main --release 21 -d build/core-tests   "$P/Physiology.java" "$P/BodyPart.java" "$P/FractureProfile.java" "$P/RecoveryClock.java" "$P/Wound.java"   "$T/PhysiologyTest.java" "$T/Mvp2CoreTest.java"
java -cp build/core-tests dev.vitalstages.health.PhysiologyTest
java -cp build/core-tests dev.vitalstages.health.Mvp2CoreTest
# Mvp2NbtTest требует реальный Minecraft classpath: gradle mvp2NbtTest.
```

### scripts/JavaSyntaxCheck.java

```java
import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

// Синтаксис Java 21, НЕ типизация NeoForge и НЕ проверка mixin-targets.
class JavaSyntaxCheck {
    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, java.nio.charset.StandardCharsets.UTF_8);
             var files = Files.walk(Path.of(args[0]))) {
            var paths = files.filter(p -> p.toString().endsWith(".java")).toList();
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none"), null, manager.getJavaFileObjectsFromPaths(paths));
            task.parse();
            for (var d : diagnostics.getDiagnostics()) if (d.getKind() == Diagnostic.Kind.ERROR)
                throw new IllegalStateException(d.toString());
            System.out.println("PASS: Java 21 syntax, " + paths.size() + " source files (not an integration compilation)");
        }
    }
}
```

### .github/workflows/build.yml

```yaml
name: Build
on: [push, pull_request]
permissions:
  contents: read
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '9.2.1'
      - run: gradle --no-daemon build
      - uses: actions/upload-artifact@v4
        with:
          name: vitalstages-jars
          path: build/libs/*.jar
```

### src/main/resources/META-INF/neoforge.mods.toml

```toml
modLoader="javafml"
loaderVersion="[4,)"
license="MIT"
[[mods]]
modId="vitalstages"
version="${version}"
displayName="Vital Stages"
authors="Vital Stages contributors"
description='''Server-authoritative staged health, wounds, unconsciousness and first aid.'''
[[dependencies.vitalstages]]
modId="neoforge"
type="required"
versionRange="[21.1.233,21.2)"
ordering="NONE"
side="BOTH"
[[dependencies.vitalstages]]
modId="minecraft"
type="required"
versionRange="[1.21.1]"
ordering="NONE"
side="BOTH"
[[mixins]]
config="vitalstages.mixins.json"
```

### src/main/resources/assets/vitalstages/lang/en_us.json

```json
{
  "item.vitalstages.bandage": "Bandage",
  "vitalstages.unconscious": "You are unconscious",
  "vitalstages.rescue_window": "Rescue window: up to %s s remaining",
  "vitalstages.vitals": "Blood: %s%%  |  Consciousness: %s%%",
  "vitalstages.no_open_cut": "No open cuts to bandage",
  "vitalstages.bandaged": "The most dangerous open cut has been bandaged",
  "death.attack.vitalstages.organ_failure": "%1$s could not be saved",
  "death.attack.vitalstages.organ_failure.player": "%1$s could not be saved after fighting %2$s",
  "item.vitalstages.splint": "Splint",
  "vitalstages.splinted": "Fracture stabilized. Recovery has started.",
  "vitalstages.no_fracture": "No untreated arm or leg fracture",
  "key.vitalstages.toggle_hud": "Toggle anatomy HUD",
  "key.categories.vitalstages": "Vital Stages",
  "vitalstages.hud.title": "VITAL STATUS",
  "vitalstages.hud.blood": "Blood: %s%%",
  "vitalstages.hud.consciousness": "Awareness: %s%%",
  "vitalstages.hud.legend_fracture": "x fracture  + splint",
  "vitalstages.hud.legend_wound": "! wound  B open cut",
  "vitalstages.hud.footer": "HP %s  |  %s°C",
  "vitalstages.part.head": "H",
  "vitalstages.part.torso": "T",
  "vitalstages.part.left_arm": "LA",
  "vitalstages.part.right_arm": "RA",
  "vitalstages.part.left_leg": "LL",
  "vitalstages.part.right_leg": "RL",
  "vitalstages.debug.fracture": "Test fracture added: %s",
  "vitalstages.debug.reset": "Health state reset: %s"
}
```

### src/main/resources/assets/vitalstages/lang/ru_ru.json

```json
{
  "item.vitalstages.bandage": "Бинт",
  "vitalstages.unconscious": "Вы без сознания",
  "vitalstages.rescue_window": "Помощь ещё возможна: не более %s с",
  "vitalstages.vitals": "Кровь: %s%%  |  Сознание: %s%%",
  "vitalstages.no_open_cut": "Нет открытых порезов для перевязки",
  "vitalstages.bandaged": "Самая опасная открытая рана перевязана",
  "death.attack.vitalstages.organ_failure": "%1$s не удалось спасти",
  "death.attack.vitalstages.organ_failure.player": "%1$s не удалось спасти после боя с %2$s",
  "item.vitalstages.splint": "Шина",
  "vitalstages.splinted": "Перелом зафиксирован. Началось сращивание.",
  "vitalstages.no_fracture": "Нет нефиксированного перелома руки или ноги",
  "key.vitalstages.toggle_hud": "Показать / скрыть анатомию",
  "key.categories.vitalstages": "Vital Stages",
  "vitalstages.hud.title": "СОСТОЯНИЕ",
  "vitalstages.hud.blood": "Кровь: %s%%",
  "vitalstages.hud.consciousness": "Сознание: %s%%",
  "vitalstages.hud.legend_fracture": "x перелом  + шина",
  "vitalstages.hud.legend_wound": "! рана  B порез",
  "vitalstages.hud.footer": "HP %s  |  %s°C",
  "vitalstages.part.head": "Г",
  "vitalstages.part.torso": "Т",
  "vitalstages.part.left_arm": "ЛР",
  "vitalstages.part.right_arm": "ПР",
  "vitalstages.part.left_leg": "ЛН",
  "vitalstages.part.right_leg": "ПН",
  "vitalstages.debug.fracture": "Добавлен тестовый перелом: %s",
  "vitalstages.debug.reset": "Состояние сброшено: %s"
}
```

### src/main/resources/assets/vitalstages/models/item/bandage.json

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "vitalstages:item/bandage"
  }
}
```

### src/main/resources/assets/vitalstages/models/item/splint.json

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "vitalstages:item/splint"
  }
}
```

### src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json

```json
{
  "replace": false,
  "values": [
    "vitalstages:organ_failure"
  ]
}
```

### src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json

```json
{
  "replace": false,
  "values": [
    "vitalstages:organ_failure"
  ]
}
```

### src/main/resources/data/minecraft/tags/damage_type/bypasses_effects.json

```json
{
  "replace": false,
  "values": [
    "vitalstages:organ_failure"
  ]
}
```

### src/main/resources/data/minecraft/tags/damage_type/bypasses_enchantments.json

```json
{
  "replace": false,
  "values": [
    "vitalstages:organ_failure"
  ]
}
```

### src/main/resources/data/minecraft/tags/damage_type/bypasses_invulnerability.json

```json
{
  "replace": false,
  "values": [
    "vitalstages:organ_failure"
  ]
}
```

### src/main/resources/data/minecraft/tags/damage_type/bypasses_resistance.json

```json
{
  "replace": false,
  "values": [
    "vitalstages:organ_failure"
  ]
}
```

### src/main/resources/data/minecraft/tags/damage_type/bypasses_shield.json

```json
{
  "replace": false,
  "values": [
    "vitalstages:organ_failure"
  ]
}
```

### src/main/resources/data/vitalstages/damage_type/organ_failure.json

```json
{
  "exhaustion": 0.0,
  "message_id": "vitalstages.organ_failure",
  "scaling": "never",
  "effects": "hurt"
}
```

### src/main/resources/data/vitalstages/recipe/bandage.json

```json
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [
    {
      "tag": "minecraft:wool"
    },
    {
      "item": "minecraft:string"
    }
  ],
  "result": {
    "id": "vitalstages:bandage",
    "count": 4
  }
}
```

### src/main/resources/data/vitalstages/recipe/splint.json

```json
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [
    {
      "item": "minecraft:stick"
    },
    {
      "item": "minecraft:stick"
    },
    {
      "item": "minecraft:string"
    },
    {
      "item": "vitalstages:bandage"
    }
  ],
  "result": {
    "id": "vitalstages:splint",
    "count": 1
  }
}
```

### src/main/resources/pack.mcmeta

```json
{
  "pack": {
    "pack_format": 34,
    "description": "Vital Stages resources"
  }
}
```

### src/main/resources/vitalstages.mixins.json

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "dev.vitalstages.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": [
    "UnconsciousInputMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

## j. Следующие этапы

# План развития Vital Stages

## MVP-1 — базовая физиология
- [x] Attachment/capability, NBT, урон, раны, кровь, боль и сознание.
- [x] Окно спасения, финальная смерть, бинт, S2C, виньетка и blackout.

## MVP-2 — переломы и анатомия (0.2.0)
- [x] Штрафы переломов рук/ног без накопления атрибутов.
- [x] Шина для себя/напарника, заживление и сращивание в NBT.
- [x] Анатомический HUD, клиентские настройки и H.
- [x] Исправление Gradle 9, новые чистые тесты и NBT-проверки для CI.
- [ ] Полный зелёный build 0.2.0 и игровой dedicated-server QA.

## MVP-3 — расширенная медицина
- [ ] Выбор конкретной раны вместо автоматической самой опасной.
- [ ] Жгут: установка/снятие, ишемия и повреждение тканей при затягивании помощи.
- [ ] Антисептик, обезболивающее и ограниченное время действия.
- [ ] Адреналин/реанимация напарником с серверными условиями и cooldown.
- [ ] Игровой способ пополнения крови без пассивной регенерации.

## MVP-4 — бред: картинка, звук и реплики
- [ ] Post-processing: десатурация, двоение, размытие, resize/reload и совместимость.
- [ ] Пульс, шёпоты и декоративные клиентские фантомы.
- [ ] Настройки доступности и интенсивности эффектов.
- [ ] **Добавлено по запросу пользователя: во время бреда от имени персонажа могут появляться фразы из отдельного JSON-файла, который заполняет пользователь.**

### Запланированная спецификация JSON-реплик

Статус: план, НЕ работающая функция MVP-2. В 0.2.0 нет loader/таймера/автоматической отправки сообщений.

Планируемый серверный файл: config/vitalstages/delirium_phrases.json.

```json
{
  "version": 1,
  "phrases": []
}
```

Пользователь заполняет phrases своими строками. Пустой массив означает отсутствие реплик. Пустой шаблон приложен как docs/planned/delirium_phrases.example.json; сейчас он не загружается модом.

Планируемые правила:

- Только при бреде и сознании выше нуля. Мёртвые, бессознательные, creative/spectator игроки молчат.
- Отключено по умолчанию. Серверный toggle и возможность игрока разрешить/отключить реплики от своего персонажа.
- Случайный интервал, например 45–120 секунд; ограничение частоты, запрет непосредственного повтора, защита от спама через reconnect/dimension.
- По умолчанию локальная слышимость, например 24 блока в том же измерении, не весь сервер.
- Вид сообщения: **[Бред] ИмяИгрока: фраза**. Ник берётся сервером; пометка отличает игровой эффект от сознательного сообщения человека.
- Системное сообщение через Component.literal. Не подделывать подписанный player-chat, подписи или ввод игрока.
- JSON содержит только текст, не команды и не произвольные компоненты с click/hover actions. Ограничить длину, управляющие символы и переносы строк.
- Проверять версию, UTF-8, размер файла и массива. Плохой JSON не должен ронять сервер.
- Будущая OP-команда reload с атомарной заменой набора; при ошибке сохраняется последний исправный набор.

## MVP-5 — среда, инфекция и транспортировка
- [ ] Теплообмен с биомом/водой/погодой/бронёй и температурные зоны.
- [ ] Переход риска инфекции в заболевание с симптомами и лечением.
- [ ] Перенос пострадавшего и корректная физика тела/транспорта.
- [ ] Аптечка-блок, доставка живого игрока и отдельные правила восстановления.

## Перед стабильным релизом
- [ ] Интеграционные GameTests, NBT/codec checks, два клиента + dedicated server.
- [ ] Совместимость с health/death/gravestone/anti-cheat модами.
- [ ] Долгий прогон, аудит пакетов и игровой баланс.
