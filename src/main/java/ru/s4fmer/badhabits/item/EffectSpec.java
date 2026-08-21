package ru.s4fmer.badhabits.item;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** One status effect granted by a substance. Duration is scaled by the player's tolerance. */
public record EffectSpec(Holder<MobEffect> effect, int seconds, int amplifier) {

    public static EffectSpec of(Holder<MobEffect> effect, int seconds, int amplifier) {
        return new EffectSpec(effect, seconds, amplifier);
    }

    public static EffectSpec of(Holder<MobEffect> effect, int seconds) {
        return new EffectSpec(effect, seconds, 0);
    }

    public void apply(LivingEntity entity, float toleranceFactor) {
        int ticks = Math.max(20, (int) (seconds * 20 * toleranceFactor));
        entity.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, true));
    }

    public static void applyAll(List<EffectSpec> specs, LivingEntity entity, float toleranceFactor) {
        for (EffectSpec spec : specs) {
            spec.apply(entity, toleranceFactor);
        }
    }
}
