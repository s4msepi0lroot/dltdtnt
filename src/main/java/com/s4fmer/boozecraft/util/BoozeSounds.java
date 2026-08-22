package com.s4fmer.boozecraft.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Sounds are looked up from the registry by id instead of using the SoundEvents
 * constants - that way the mod stays source compatible across 1.21.x builds.
 */
public final class BoozeSounds {

	public static void play(Level level, BlockPos pos, String id, float volume, float pitch) {
		if (level == null || pos == null) {
			return;
		}
		BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.parse(id)).ifPresent(
				sound -> level.playSound(null, pos, sound, SoundSource.PLAYERS, volume, pitch));
	}

	private BoozeSounds() {
	}
}
