package com.s4fmer.boozecraft.reg;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Marker effect. All logic lives in BoozeManager (server side) and in the client
 * renderer, so this class stays intentionally empty - that keeps it compatible
 * with every 1.21.1 build and with hybrid servers.
 */
public class SimpleBoozeEffect extends MobEffect {

	public SimpleBoozeEffect(MobEffectCategory category, int color) {
		super(category, color);
	}
}
