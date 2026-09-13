package com.s4fmer.boozecraft.booze;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.s4fmer.boozecraft.BoozeConfig;
import com.s4fmer.boozecraft.reg.BoozeEffects;
import com.s4fmer.boozecraft.reg.BoozeItems;
import com.s4fmer.boozecraft.util.BoozeSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Random events for drunk players: hiccups, double vision, tripping over,
 * dropping or breaking what you hold, singing out loud and waking up somewhere
 * else after a black out.
 *
 * Everything runs server side with plain vanilla calls (sounds, particles,
 * effects, inventory, randomTeleport), so hybrid servers stay happy and no
 * custom packets are needed.
 */
public final class DrunkEvents {

	/** players who were passed out when we looked at them the last time */
	private static final Set<UUID> WAS_OUT = new HashSet<>();

	public static void tick(ServerPlayer player) {
		if (!BoozeConfig.EVENTS_ENABLED.get()) {
			WAS_OUT.remove(player.getUUID());
			return;
		}
		if (player.hasEffect(BoozeEffects.PASSED_OUT)) {
			WAS_OUT.add(player.getUUID());
			return;
		}
		if (WAS_OUT.remove(player.getUUID())) {
			wokeUp(player);
		}
		if (player.tickCount % 20 != 0) {
			return;
		}
		PlayerBoozeData data = BoozeManager.data(player);
		double factor = factor(data.alcohol);
		if (factor <= 0.0D) {
			return;
		}
		ServerLevel level = player.serverLevel();
		RandomSource rnd = player.getRandom();
		if (rnd.nextDouble() < BoozeConfig.EVENT_HICCUP.get() * factor) {
			hiccup(player, level, rnd);
		}
		if (rnd.nextDouble() < BoozeConfig.EVENT_BLUR.get() * factor) {
			blur(player);
		}
		if (rnd.nextDouble() < BoozeConfig.EVENT_TRIP.get() * factor) {
			trip(player, level, rnd);
		}
		if (rnd.nextDouble() < BoozeConfig.EVENT_BREAK_GLASS.get() * factor) {
			breakGlass(player, level, rnd);
		}
		if (rnd.nextDouble() < BoozeConfig.EVENT_DROP.get() * factor) {
			dropItem(player);
		}
		if (rnd.nextDouble() < BoozeConfig.EVENT_SING.get() * factor) {
			sing(player, level, rnd);
		}
	}

	/** 1.0 while heavily drunk, 0.35 while merely drunk, 0 while sober-ish. */
	private static double factor(double alcohol) {
		if (alcohol >= BoozeConfig.HEAVY_THRESHOLD.get()) {
			return 1.0D;
		}
		if (!BoozeConfig.EVENTS_ONLY_HEAVY.get() && alcohol >= BoozeConfig.DRUNK_THRESHOLD.get()) {
			return 0.35D;
		}
		return 0.0D;
	}

	private static void hiccup(ServerPlayer player, ServerLevel level, RandomSource rnd) {
		BoozeSounds.play(level, player.blockPosition(), "minecraft:entity.player.burp", 0.7F,
				0.8F + rnd.nextFloat() * 0.4F);
		level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getEyeY(), player.getZ(),
				4, 0.2D, 0.1D, 0.2D, 0.0D);
		message(player, "msg.boozecraft.event_hiccup");
	}

	private static void blur(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false, true));
		message(player, "msg.boozecraft.event_blur");
	}

	private static void trip(ServerPlayer player, ServerLevel level, RandomSource rnd) {
		double angle = rnd.nextDouble() * Math.PI * 2.0D;
		player.push(Math.cos(angle) * 0.35D, 0.12D, Math.sin(angle) * 0.35D);
		player.hurtMarked = true;
		player.hurt(player.damageSources().fall(), 1.0F);
		BoozeSounds.play(level, player.blockPosition(), "minecraft:entity.player.small_fall", 0.8F, 1.0F);
		message(player, "msg.boozecraft.event_trip");
	}

	private static void breakGlass(ServerPlayer player, ServerLevel level, RandomSource rnd) {
		ItemStack held = player.getInventory().getSelected();
		if (held.isEmpty() || !isVessel(held)) {
			return;
		}
		held.shrink(1);
		BoozeSounds.play(level, player.blockPosition(), "minecraft:block.glass.break", 0.8F,
				0.9F + rnd.nextFloat() * 0.2F);
		message(player, "msg.boozecraft.event_break");
	}

	private static boolean isVessel(ItemStack stack) {
		return stack.is(BoozeItems.GLASS_CUP.get()) || stack.is(BoozeItems.MUG.get())
				|| stack.is(BoozeItems.SHOT_GLASS.get()) || stack.is(BoozeItems.EMPTY_CAN.get());
	}

	private static void dropItem(ServerPlayer player) {
		Inventory inv = player.getInventory();
		ItemStack held = inv.getSelected();
		if (held.isEmpty()) {
			return;
		}
		ItemStack dropped = inv.removeItem(inv.selected, held.getCount());
		if (dropped.isEmpty()) {
			return;
		}
		player.drop(dropped, false);
		if (BoozeConfig.STATUS_MESSAGES.get()) {
			player.displayClientMessage(
					Component.translatable("msg.boozecraft.event_drop", dropped.getHoverName()), true);
		}
	}

	private static void sing(ServerPlayer player, ServerLevel level, RandomSource rnd) {
		BoozeSounds.play(level, player.blockPosition(), "minecraft:entity.goat.screaming.ambient", 0.9F,
				0.8F + rnd.nextFloat() * 0.4F);
		Component text = Component.translatable("msg.boozecraft.event_sing", player.getDisplayName());
		for (ServerPlayer other : level.players()) {
			if (other.distanceToSqr(player) <= 256.0D) {
				other.displayClientMessage(text, true);
			}
		}
	}

	/** called on the first tick after a black out ended */
	private static void wokeUp(ServerPlayer player) {
		double chance = BoozeConfig.EVENT_WAKE_TELEPORT.get();
		RandomSource rnd = player.getRandom();
		if (chance <= 0.0D || rnd.nextDouble() >= chance) {
			return;
		}
		int radius = BoozeConfig.EVENT_WAKE_RADIUS.get();
		double x = player.getX() + (rnd.nextDouble() - 0.5D) * 2.0D * radius;
		double z = player.getZ() + (rnd.nextDouble() - 0.5D) * 2.0D * radius;
		double y = player.getY() + 1.0D;
		if (player.randomTeleport(x, y, z, true)) {
			BoozeSounds.play(player.serverLevel(), player.blockPosition(),
					"minecraft:entity.player.burp", 0.6F, 0.7F);
			message(player, "msg.boozecraft.event_wake_elsewhere");
		}
	}

	private static void message(ServerPlayer player, String key) {
		if (BoozeConfig.STATUS_MESSAGES.get()) {
			player.displayClientMessage(Component.translatable(key), true);
		}
	}

	private DrunkEvents() {
	}
}
