package com.s4fmer.boozecraft.reg;

import com.s4fmer.boozecraft.BoozeCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BoozeEffects {

	public static final DeferredRegister<MobEffect> EFFECTS =
			DeferredRegister.create(Registries.MOB_EFFECT, BoozeCraft.MODID);

	/** Stage 1: the pleasant part. */
	public static final DeferredHolder<MobEffect, MobEffect> TIPSY =
			reg("tipsy", MobEffectCategory.NEUTRAL, 0xE0B34C);
	/** Stage 2. */
	public static final DeferredHolder<MobEffect, MobEffect> DRUNK =
			reg("drunk", MobEffectCategory.HARMFUL, 0xA9662B);
	/** Stage 3. */
	public static final DeferredHolder<MobEffect, MobEffect> HEAVY_DRUNK =
			reg("heavy_drunk", MobEffectCategory.HARMFUL, 0x6B3A15);
	/** Lights out. */
	public static final DeferredHolder<MobEffect, MobEffect> PASSED_OUT =
			reg("passed_out", MobEffectCategory.HARMFUL, 0x2B2B2B);
	public static final DeferredHolder<MobEffect, MobEffect> HANGOVER =
			reg("hangover", MobEffectCategory.HARMFUL, 0x7F8C8D);
	public static final DeferredHolder<MobEffect, MobEffect> WITHDRAWAL =
			reg("withdrawal", MobEffectCategory.HARMFUL, 0x6C3FA0);
	public static final DeferredHolder<MobEffect, MobEffect> CAFFEINE =
			reg("caffeine", MobEffectCategory.BENEFICIAL, 0x6F4E37);
	public static final DeferredHolder<MobEffect, MobEffect> JITTERS =
			reg("jitters", MobEffectCategory.HARMFUL, 0xD2691E);

	private static DeferredHolder<MobEffect, MobEffect> reg(String name, MobEffectCategory category, int color) {
		return EFFECTS.register(name, () -> new SimpleBoozeEffect(category, color));
	}

	private BoozeEffects() {
	}
}
