package ru.s4fmer.badhabits;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side config. File: &lt;world&gt;/serverconfig/badhabits-server.toml
 * Comments are kept in ASCII on purpose so the TOML file stays readable in any console/editor encoding.
 */
public final class BhConfig {
    public static final ModConfigSpec SPEC;

    // ---- general ----
    public static final ModConfigSpec.BooleanValue LIGHTER_IGNITES;
    public static final ModConfigSpec.BooleanValue CLEAR_ON_DEATH;
    public static final ModConfigSpec.BooleanValue STATUS_MESSAGES;

    // ---- addiction core ----
    public static final ModConfigSpec.DoubleValue ADDICTION_MAX;
    public static final ModConfigSpec.DoubleValue DOSE_DECAY_PER_SECOND;
    public static final ModConfigSpec.DoubleValue WITHDRAWAL_MIN_ADDICTION;
    public static final ModConfigSpec.DoubleValue CLEAN_DECAY_PER_SECOND;
    public static final ModConfigSpec.DoubleValue TAPER_REDUCTION;
    public static final ModConfigSpec.DoubleValue COLD_TURKEY_DECAY_PER_SECOND;
    public static final ModConfigSpec.DoubleValue TOLERANCE_SCALE;

    // ---- withdrawal stages ----
    public static final ModConfigSpec.IntValue STAGE1_SECONDS;
    public static final ModConfigSpec.IntValue STAGE2_SECONDS;
    public static final ModConfigSpec.IntValue STAGE3_SECONDS;
    public static final ModConfigSpec.IntValue STAGE4_SECONDS;
    public static final ModConfigSpec.DoubleValue STAGE3_HEALTH_DRAIN;
    public static final ModConfigSpec.DoubleValue STAGE4_HEALTH_DRAIN;
    public static final ModConfigSpec.DoubleValue MIN_HEALTH;
    public static final ModConfigSpec.BooleanValue WITHDRAWAL_CAN_KILL;

    // ---- overdose ----
    public static final ModConfigSpec.DoubleValue OVERDOSE_DOSE;
    public static final ModConfigSpec.DoubleValue HARD_OVERDOSE_DOSE;

    // ---- cough ----
    public static final ModConfigSpec.BooleanValue CHAT_COUGH;
    public static final ModConfigSpec.BooleanValue COUGH_REBROADCAST;
    public static final ModConfigSpec.DoubleValue COUGH_CHANCE_MIN;
    public static final ModConfigSpec.DoubleValue COUGH_CHANCE_MAX;
    public static final ModConfigSpec.DoubleValue COUGH_ON_SMOKE_CHANCE;
    public static final ModConfigSpec.ConfigValue<String> COUGH_TEXT_LIGHT;
    public static final ModConfigSpec.ConfigValue<String> COUGH_TEXT_HEAVY;

    // ---- detox ----
    public static final ModConfigSpec.DoubleValue DETOX_REDUCTION;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("General switches").push("general");
        LIGHTER_IGNITES = b
                .comment("Allow the lighter to set fire to blocks (like flint and steel, costs 2 durability).")
                .define("lighterIgnitesBlocks", true);
        CLEAR_ON_DEATH = b
                .comment("Wipe all addiction data when the player dies. false = death only clears the current dose.")
                .define("clearAddictionOnDeath", false);
        STATUS_MESSAGES = b
                .comment("Send action-bar status messages (withdrawal stages, taper, overdose).")
                .define("statusMessages", true);
        b.pop();

        b.comment("Addiction math. 'dose' = substance currently in the body, 'addiction' = 0..max scale").push("addiction");
        ADDICTION_MAX = b
                .comment("Maximum addiction value.")
                .defineInRange("addictionMax", 100.0D, 1.0D, 1000.0D);
        DOSE_DECAY_PER_SECOND = b
                .comment("How much dose is burned per second. Bigger = withdrawal starts sooner.")
                .defineInRange("doseDecayPerSecond", 0.06D, 0.001D, 100.0D);
        WITHDRAWAL_MIN_ADDICTION = b
                .comment("Withdrawal only starts above this addiction value.")
                .defineInRange("withdrawalMinAddiction", 12.0D, 0.0D, 1000.0D);
        CLEAN_DECAY_PER_SECOND = b
                .comment("Addiction decay per second while below withdrawalMinAddiction (light users recover).")
                .defineInRange("cleanDecayPerSecond", 0.02D, 0.0D, 10.0D);
        TAPER_REDUCTION = b
                .comment("Addiction removed when you take a SMALLER dose than the previous one (tapering down).")
                .defineInRange("taperReduction", 6.0D, 0.0D, 100.0D);
        COLD_TURKEY_DECAY_PER_SECOND = b
                .comment("Addiction decay per second while suffering stage 4 withdrawal (quitting cold turkey, very slow).")
                .defineInRange("coldTurkeyDecayPerSecond", 0.01D, 0.0D, 10.0D);
        TOLERANCE_SCALE = b
                .comment("Tolerance divisor: effect duration multiplier = 1 - addiction / this value (min 0.35).")
                .defineInRange("toleranceScale", 150.0D, 10.0D, 10000.0D);
        b.pop();

        b.comment("Withdrawal timings (seconds without any dose, scaled by addiction level)").push("withdrawal");
        STAGE1_SECONDS = b.defineInRange("stage1Seconds", 120, 1, 1000000);
        STAGE2_SECONDS = b.defineInRange("stage2Seconds", 360, 1, 1000000);
        STAGE3_SECONDS = b.defineInRange("stage3Seconds", 720, 1, 1000000);
        STAGE4_SECONDS = b.defineInRange("stage4Seconds", 1200, 1, 1000000);
        STAGE3_HEALTH_DRAIN = b
                .comment("Health drained per second at stage 3.")
                .defineInRange("stage3HealthDrainPerSecond", 0.08D, 0.0D, 20.0D);
        STAGE4_HEALTH_DRAIN = b
                .comment("Health drained per second at stage 4 (full refusal = practically dead).")
                .defineInRange("stage4HealthDrainPerSecond", 0.25D, 0.0D, 20.0D);
        MIN_HEALTH = b
                .comment("Withdrawal never drains health below this value.")
                .defineInRange("minHealth", 1.0D, 0.5D, 20.0D);
        WITHDRAWAL_CAN_KILL = b
                .comment("If true, withdrawal deals real damage and CAN kill the player (ignores minHealth).")
                .define("withdrawalCanKill", false);
        b.pop();

        b.comment("Overdose thresholds (current dose in the body)").push("overdose");
        OVERDOSE_DOSE = b.defineInRange("overdoseDose", 60.0D, 1.0D, 1000.0D);
        HARD_OVERDOSE_DOSE = b.defineInRange("hardOverdoseDose", 95.0D, 1.0D, 1000.0D);
        b.pop();

        b.comment("Chat cough feature").push("cough");
        CHAT_COUGH = b
                .comment("Prefix chat messages of nicotine addicted players with a cough.")
                .define("chatCough", true);
        COUGH_REBROADCAST = b
                .comment("Compatibility mode for hybrid servers: cancel the vanilla chat and broadcast the coughed message manually.",
                        "Turn this ON only if chat plugins on your hybrid core ignore the modified message.")
                .define("rebroadcastInsteadOfEditing", false);
        COUGH_CHANCE_MIN = b
                .comment("Cough chance at minimal addiction.")
                .defineInRange("chanceMin", 0.05D, 0.0D, 1.0D);
        COUGH_CHANCE_MAX = b
                .comment("Cough chance at maximum addiction.")
                .defineInRange("chanceMax", 0.55D, 0.0D, 1.0D);
        COUGH_ON_SMOKE_CHANCE = b
                .comment("Chance to cough right after smoking a cigarette.")
                .defineInRange("chanceAfterSmoking", 0.30D, 0.0D, 1.0D);
        COUGH_TEXT_LIGHT = b
                .comment("Custom light cough text. Leave empty to use the translated text from the language file.")
                .define("textLight", "");
        COUGH_TEXT_HEAVY = b
                .comment("Custom heavy cough text. Leave empty to use the translated text from the language file.")
                .define("textHeavy", "");
        b.pop();

        b.comment("Detox tonic").push("detox");
        DETOX_REDUCTION = b
                .comment("Addiction removed by one Detox Tonic (applies to both nicotine and narcotic).")
                .defineInRange("detoxReduction", 12.0D, 0.0D, 1000.0D);
        b.pop();

        SPEC = b.build();
    }

    private BhConfig() {
    }
}
