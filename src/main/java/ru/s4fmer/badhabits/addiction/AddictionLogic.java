package ru.s4fmer.badhabits.addiction;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import ru.s4fmer.badhabits.BhConfig;
import ru.s4fmer.badhabits.util.CoughHelper;
import ru.s4fmer.badhabits.util.Msg;

/**
 * The heart of the mod: dose accounting, tolerance, tapering, withdrawal stages and overdose.
 * Every method here must only ever be called on the server thread.
 */
public final class AddictionLogic {
    private static final int EFFECT_REFRESH_TICKS = 60; // 3 s, refreshed every second

    private AddictionLogic() {
    }

    // ------------------------------------------------------------------ use

    /**
     * Registers a consumed dose and returns the tolerance factor (0.35..1.0) that scales effect duration.
     */
    public static float consume(ServerPlayer player, Substance substance, float dose, float addictionGain) {
        PlayerAddiction data = AddictionManager.get(player);
        Meter meter = data.meter(substance);

        float max = BhConfig.ADDICTION_MAX.get().floatValue();
        float taperCut = BhConfig.TAPER_REDUCTION.get().floatValue();

        // Tapering: a dose smaller than the previous one, taken when the body is already almost clean,
        // actually REDUCES addiction. This is the intended "cure" path.
        boolean taper = meter.lastDose > 0.01F
                && dose < meter.lastDose - 0.01F
                && meter.dose <= dose;

        if (taper) {
            meter.addiction = Math.max(0.0F, meter.addiction - taperCut);
            Msg.bar(player, Msg.tr("badhabits.msg.taper", fmt(taperCut), fmt(meter.addiction))
                    .withStyle(ChatFormatting.GREEN));
        } else {
            meter.addiction = Math.min(max, meter.addiction + addictionGain);
        }

        meter.dose = Math.min(200.0F, meter.dose + dose);
        meter.lastDose = dose;
        meter.withdrawalSeconds = 0;
        meter.stage = 0;
        meter.lastUseTick = player.level().getGameTime();
        AddictionManager.markDirty();

        checkOverdose(player, meter);
        return tolerance(meter.addiction);
    }

    public static float tolerance(float addiction) {
        float scale = BhConfig.TOLERANCE_SCALE.get().floatValue();
        float factor = 1.0F - (addiction / scale);
        return Math.max(0.35F, Math.min(1.0F, factor));
    }

    private static void checkOverdose(ServerPlayer player, Meter meter) {
        float soft = BhConfig.OVERDOSE_DOSE.get().floatValue();
        float hard = BhConfig.HARD_OVERDOSE_DOSE.get().floatValue();

        if (meter.dose >= hard) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 400, 1, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 160, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 1, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300, 1, false, true));
            drainHealth(player, 6.0F);
            Msg.bar(player, Msg.tr("badhabits.msg.overdose_hard").withStyle(ChatFormatting.DARK_RED));
        } else if (meter.dose >= soft) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0, false, true));
            Msg.bar(player, Msg.tr("badhabits.msg.overdose").withStyle(ChatFormatting.RED));
        }
    }

    // ----------------------------------------------------------------- tick

    public static void tickPlayer(ServerPlayer player, int seconds) {
        PlayerAddiction data = AddictionManager.getIfPresent(player.getUUID());
        if (data == null) {
            return;
        }
        for (Substance substance : Substance.values()) {
            tickMeter(player, substance, data.meter(substance), seconds);
        }
    }

    private static void tickMeter(ServerPlayer player, Substance substance, Meter meter, int seconds) {
        if (meter.addiction <= 0.0F && meter.dose <= 0.0F) {
            return;
        }

        if (meter.dose > 0.0F) {
            float decay = (float) (BhConfig.DOSE_DECAY_PER_SECOND.get() * seconds);
            meter.dose = Math.max(0.0F, meter.dose - decay);
            AddictionManager.markDirty();
        }

        // Still high enough -> no withdrawal.
        if (meter.dose > 0.01F) {
            meter.withdrawalSeconds = 0;
            meter.stage = 0;
            return;
        }

        float minAddiction = BhConfig.WITHDRAWAL_MIN_ADDICTION.get().floatValue();
        if (meter.addiction < minAddiction) {
            // Not addicted enough to suffer: slowly get clean.
            meter.withdrawalSeconds = 0;
            meter.stage = 0;
            float before = meter.addiction;
            meter.addiction = Math.max(0.0F, meter.addiction - (float) (BhConfig.CLEAN_DECAY_PER_SECOND.get() * seconds));
            if (before > 0.0F && meter.addiction <= 0.0F) {
                meter.lastDose = 0.0F;
                Msg.bar(player, Msg.tr("badhabits.msg.clean." + substance.key()).withStyle(ChatFormatting.GREEN));
            }
            AddictionManager.markDirty();
            return;
        }

        meter.withdrawalSeconds += seconds;

        // The heavier the addiction, the faster withdrawal escalates.
        float speed = 0.5F + meter.addiction / 100.0F;
        int effective = (int) (meter.withdrawalSeconds * speed);
        int stage = stageFor(effective);

        if (stage != meter.stage) {
            meter.stage = stage;
            if (stage > 0) {
                Msg.bar(player, Msg.tr("badhabits.msg.wd." + substance.key() + "." + stage)
                        .withStyle(stage >= 3 ? ChatFormatting.DARK_RED : ChatFormatting.RED));
            }
        } else if (stage > 0 && meter.withdrawalSeconds % 30 == 0) {
            Msg.bar(player, Msg.tr("badhabits.msg.wd." + substance.key() + "." + stage)
                    .withStyle(stage >= 3 ? ChatFormatting.DARK_RED : ChatFormatting.RED));
        }

        applyWithdrawal(player, substance, stage, seconds);

        if (stage >= 4) {
            // Cold turkey: the body slowly heals, but it hurts all the way down.
            meter.addiction = Math.max(0.0F,
                    meter.addiction - (float) (BhConfig.COLD_TURKEY_DECAY_PER_SECOND.get() * seconds));
        }
        AddictionManager.markDirty();
    }

    private static int stageFor(int effectiveSeconds) {
        if (effectiveSeconds >= BhConfig.STAGE4_SECONDS.get()) {
            return 4;
        }
        if (effectiveSeconds >= BhConfig.STAGE3_SECONDS.get()) {
            return 3;
        }
        if (effectiveSeconds >= BhConfig.STAGE2_SECONDS.get()) {
            return 2;
        }
        if (effectiveSeconds >= BhConfig.STAGE1_SECONDS.get()) {
            return 1;
        }
        return 0;
    }

    private static void applyWithdrawal(ServerPlayer player, Substance substance, int stage, int seconds) {
        if (stage <= 0) {
            return;
        }

        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_REFRESH_TICKS, stage >= 3 ? 1 : 0, true, false));

        if (stage >= 2) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_REFRESH_TICKS, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, EFFECT_REFRESH_TICKS, 0, true, false));
            if (player.getRandom().nextFloat() < 0.15F) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, true, false));
            }
        }

        if (stage >= 3) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, EFFECT_REFRESH_TICKS, 0, true, false));
            if (player.getRandom().nextFloat() < 0.10F) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, true, false));
            }
            drainHealth(player, (float) (BhConfig.STAGE3_HEALTH_DRAIN.get() * seconds));
        }

        if (stage >= 4) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_REFRESH_TICKS, 1, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, EFFECT_REFRESH_TICKS, 1, true, false));
            if (player.getRandom().nextFloat() < 0.25F) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, true, false));
            }
            drainHealth(player, (float) (BhConfig.STAGE4_HEALTH_DRAIN.get() * seconds));
        }

        if (substance == Substance.NICOTINE && player.getRandom().nextFloat() < 0.04F * stage) {
            CoughHelper.cough(player, stage >= 3);
        }
    }

    // --------------------------------------------------------------- health

    /**
     * Drains health without ever killing the player (unless the admin allows it in the config).
     * "Full refusal = practically dead" - health goes down to minHealth (1 HP by default) and stays there.
     */
    public static void drainHealth(ServerPlayer player, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        if (BhConfig.WITHDRAWAL_CAN_KILL.get()) {
            player.hurt(player.damageSources().magic(), amount);
            return;
        }
        float min = BhConfig.MIN_HEALTH.get().floatValue();
        float current = player.getHealth();
        float target = Math.max(min, current - amount);
        if (target < current) {
            player.setHealth(target);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_HURT, SoundSource.PLAYERS, 0.35F, 0.9F);
        }
    }

    public static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
