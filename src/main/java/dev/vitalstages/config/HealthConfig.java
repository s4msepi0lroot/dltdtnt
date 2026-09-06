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
    public static final ModConfigSpec.IntValue PAINKILLER_SECONDS, ANTISEPTIC_SECONDS, ADRENALINE_SECONDS,
            ADRENALINE_COOLDOWN_SECONDS, DONATION_COOLDOWN_SECONDS, MEDICINE_COOLDOWN_TICKS,
            CHAT_MIN_SECONDS, CHAT_MAX_SECONDS;
    public static final ModConfigSpec.DoubleValue PAIN_RELIEF, ADRENALINE_BONUS, ADRENALINE_MIN_BLOOD,
            DONATION_MIN_BLOOD, CHAT_RADIUS, CHAT_MAX_CONSCIOUSNESS;
    public static final ModConfigSpec.BooleanValue CHAT_ENABLED;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("systems");
        ENABLE_BLOOD_LOSS = b.define("enableBloodLoss", true);
        ENABLE_FRACTURES = b.comment("Создание переломов и штрафы. Отключение не стирает раны/фиксацию.").define("enableFractures", true);
        ENABLE_DELIRIUM = b.comment("Визуальные эффекты и JSON-реплики; обязательный blackout не отключает.").define("enableDelirium", true);
        ENABLE_TEMPERATURE = b.comment("Штраф от сохранённой температуры. Климат вне MVP.").define("enableTemperature", false);
        ENABLE_INFECTION = b.comment("Таймер/риск с антисептиком. Клиническое заболевание пока не моделируется.").define("enableInfection", false);
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
        b.pop().push("medicines");
        PAINKILLER_SECONDS = b.defineInRange("painkillerSeconds", 120, 1, 86400);
        PAIN_RELIEF = b.defineInRange("painRelief", 35.0, 1, 100);
        ANTISEPTIC_SECONDS = b.defineInRange("antisepticSeconds", 300, 1, 86400);
        ADRENALINE_SECONDS = b.defineInRange("adrenalineSeconds", 20, 1, 3600);
        ADRENALINE_COOLDOWN_SECONDS = b.defineInRange("adrenalineCooldownSeconds", 180, 1, 86400);
        ADRENALINE_BONUS = b.defineInRange("adrenalineConsciousnessBonus", 30.0, 1, 100);
        ADRENALINE_MIN_BLOOD = b.defineInRange("adrenalineMinimumBlood", 25.0, 1, 100);
        DONATION_MIN_BLOOD = b.defineInRange("donationMinimumBlood", 70.0, 40, 100);
        DONATION_COOLDOWN_SECONDS = b.defineInRange("donationCooldownSeconds", 300, 1, 86400);
        MEDICINE_COOLDOWN_TICKS = b.defineInRange("medicineCooldownTicks", 40, 1, 200);
        b.pop().push("deliriumChat");
        CHAT_ENABLED = b.comment("Заполните config/vitalstages/delirium_phrases.json. Только серверный буквальный текст.")
                .define("enabled", false);
        CHAT_MIN_SECONDS = b.defineInRange("minIntervalSeconds", 45, 1, 86400);
        CHAT_MAX_SECONDS = b.defineInRange("maxIntervalSeconds", 120, 1, 86400);
        CHAT_RADIUS = b.comment("0 = всё текущее измерение; больше нуля = расстояние в блоках.")
                .defineInRange("radiusBlocks", 32.0, 0, 256);
        CHAT_MAX_CONSCIOUSNESS = b.defineInRange("maximumConsciousness", 35.0, 1, 99);
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
