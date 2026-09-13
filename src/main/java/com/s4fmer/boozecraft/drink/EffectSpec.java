package com.s4fmer.boozecraft.drink;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * A "starter" effect of a drink. The MobEffectInstance is only created when the
 * drink is actually consumed, so deferred holders are always resolved by then.
 */
public record EffectSpec(Holder<MobEffect> effect, int seconds, int amplifier) {

	public MobEffectInstance create() {
		return new MobEffectInstance(this.effect, this.seconds * 20, this.amplifier, false, true, true);
	}
}
