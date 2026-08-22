package com.s4fmer.boozecraft.booze;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.s4fmer.boozecraft.BoozeConfig;
import com.s4fmer.boozecraft.BoozeCraft;
import com.s4fmer.boozecraft.drink.DrinkDef;
import com.s4fmer.boozecraft.drink.EffectSpec;
import com.s4fmer.boozecraft.reg.BoozeEffects;
import com.s4fmer.boozecraft.util.BoozeSounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * The whole drunkenness simulation. Runs on the server only, which is what makes
 * the mod safe on hybrid servers: the client just sees vanilla mob effects.
 */
public final class BoozeManager {

	private static final Map<UUID, PlayerBoozeData> DATA = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> STAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3> ANCHOR = new ConcurrentHashMap<>();

	private static MinecraftServer server;
	private static long lastSave;

	// ------------------------------------------------------------------ access

	public static PlayerBoozeData data(UUID id) {
		return DATA.computeIfAbsent(id, key -> new PlayerBoozeData());
	}

	public static PlayerBoozeData data(ServerPlayer player) {
		return data(player.getUUID());
	}

	public static boolean isAddicted(PlayerBoozeData d) {
		return BoozeConfig.ADDICTION_ENABLED.get() && d.addiction >= BoozeConfig.ADDICTION_THRESHOLD.get();
	}

	public static int stageOf(PlayerBoozeData d) {
		double drunk = BoozeConfig.DRUNK_THRESHOLD.get();
		double heavy = BoozeConfig.HEAVY_THRESHOLD.get();
		if (d.alcohol >= heavy) {
			return 3;
		}
		if (d.alcohol >= drunk) {
			return 2;
		}
		return d.alcohol > 2.0D ? 1 : 0;
	}

	// ----------------------------------------------------------------- drinking

	public static void onDrink(ServerPlayer player, DrinkDef def) {
		PlayerBoozeData d = data(player);
		long now = System.currentTimeMillis();

		boolean allowStarter = !BoozeConfig.STARTER_EFFECTS_ONLY_WHEN_SOBER.get()
				|| d.alcohol < BoozeConfig.DRUNK_THRESHOLD.get();
		if (allowStarter) {
			for (EffectSpec spec : def.effects()) {
				player.addEffect(spec.create());
			}
		} else if (BoozeConfig.STATUS_MESSAGES.get() && !def.effects().isEmpty()) {
			msg(player, "msg.boozecraft.too_drunk_for_effects");
		}

		if (def.alcoholic()) {
			double add = def.abv() * 55.0D * BoozeConfig.DRINK_STRENGTH_MULTIPLIER.get();
			d.alcohol = Math.min(BoozeConfig.ALCOHOL_CAP.get(), d.alcohol + add);
			d.peak = Math.max(d.peak, d.alcohol);
			d.lastAlcoholTime = now;
			d.drinksAlcohol++;
			player.removeEffect(BoozeEffects.WITHDRAWAL);

			if (BoozeConfig.ADDICTION_ENABLED.get()) {
				double gain = def.addiction() * BoozeConfig.ADDICTION_GAIN_MULTIPLIER.get();
				if (now - d.lastDrinkTime < 120000L) {
					gain *= 1.5D; // binge drinking hooks you faster
				}
				boolean before = isAddicted(d);
				d.addiction = Math.max(0.0D, Math.min(100.0D, d.addiction + gain));
				if (!before && isAddicted(d)) {
					msg(player, "msg.boozecraft.addicted");
				}
			}
		}

		if (def.caffeine() > 0.0D) {
			d.caffeine = Math.min(200.0D, d.caffeine + def.caffeine());
			d.alcohol = Math.max(0.0D, d.alcohol - def.caffeine() * BoozeConfig.CAFFEINE_SOBER_FACTOR.get());
		}
		if (def.soberBonus() > 0.0D) {
			d.alcohol = Math.max(0.0D, d.alcohol - def.soberBonus());
		}
		if (def.addictionRelief() > 0.0D) {
			d.addiction = Math.max(0.0D, d.addiction - def.addictionRelief());
		}
		if (def.curesHangover()) {
			d.hangoverUntil = 0L;
			d.peak = 0.0D;
			player.removeEffect(BoozeEffects.HANGOVER);
		}

		d.lastDrinkTime = now;
		d.drinksTotal++;
	}

	/** Milk sobers you up a lot - it also wipes the mob effects (vanilla behaviour). */
	public static void milk(ServerPlayer player) {
		PlayerBoozeData d = data(player);
		d.alcohol *= 0.35D;
		d.caffeine *= 0.5D;
		d.hangoverUntil = 0L;
		if (BoozeConfig.STATUS_MESSAGES.get()) {
			msg(player, "msg.boozecraft.milk");
		}
	}

	public static void sober(ServerPlayer player) {
		PlayerBoozeData d = data(player);
		d.alcohol = 0.0D;
		d.peak = 0.0D;
		d.caffeine = 0.0D;
		d.hangoverUntil = 0L;
		d.passedOutUntil = 0L;
		player.removeEffect(BoozeEffects.TIPSY);
		player.removeEffect(BoozeEffects.DRUNK);
		player.removeEffect(BoozeEffects.HEAVY_DRUNK);
		player.removeEffect(BoozeEffects.PASSED_OUT);
		player.removeEffect(BoozeEffects.HANGOVER);
		player.removeEffect(BoozeEffects.WITHDRAWAL);
		player.removeEffect(BoozeEffects.JITTERS);
		player.removeEffect(BoozeEffects.CAFFEINE);
		player.removeEffect(MobEffects.CONFUSION);
		ANCHOR.remove(player.getUUID());
		PassOutHelper.forceLay(player, false);
	}

	// --------------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		PlayerBoozeData d = data(player);
		long now = System.currentTimeMillis();

		if (d.passedOutUntil > now) {
			holdDown(player, d);
		} else if (d.passedOutUntil > 0L) {
			wake(player);
		}

		if (player.tickCount % 20 != 0) {
			return;
		}
		second(player, d, now);

		if (now - lastSave > 120000L) {
			lastSave = now;
			save();
		}
	}

	private static void second(ServerPlayer player, PlayerBoozeData d, long now) {
		double heavy = BoozeConfig.HEAVY_THRESHOLD.get();

		if (d.alcohol > 0.0D) {
			double rate = BoozeConfig.SOBER_RATE.get();
			if (d.caffeine > 20.0D) {
				rate *= 1.35D;
			}
			if (player.isSprinting()) {
				rate *= 1.15D;
			}
			d.alcohol = Math.max(0.0D, d.alcohol - rate);
		}
		if (d.caffeine > 0.0D) {
			d.caffeine = Math.max(0.0D, d.caffeine - BoozeConfig.CAFFEINE_DECAY_PER_SECOND.get());
		}
		if (BoozeConfig.ADDICTION_ENABLED.get() && d.addiction > 0.0D && now - d.lastAlcoholTime > 60000L) {
			d.addiction = Math.max(0.0D, d.addiction - BoozeConfig.ADDICTION_DECAY_PER_MINUTE.get() / 60.0D);
		}

		int stage = stageOf(d);
		Integer previous = STAGE.put(player.getUUID(), stage);
		if (BoozeConfig.STATUS_MESSAGES.get() && (previous == null || previous.intValue() != stage)) {
			if (stage == 1) {
				msg(player, "msg.boozecraft.stage_tipsy");
			} else if (stage == 2) {
				msg(player, "msg.boozecraft.stage_drunk");
			} else if (stage == 3) {
				msg(player, "msg.boozecraft.stage_heavy");
			} else if (previous != null && previous.intValue() > 0) {
				msg(player, "msg.boozecraft.stage_sober");
			}
		}

		if (stage == 1) {
			apply(player, BoozeEffects.TIPSY, 60, 0);
		} else if (stage == 2) {
			apply(player, BoozeEffects.DRUNK, 60, 0);
			apply(player, MobEffects.MOVEMENT_SLOWDOWN, 60, 0);
			if (player.getRandom().nextDouble() < 0.45D) {
				apply(player, MobEffects.CONFUSION, 80, 0);
			}
			stumble(player, 0.22D);
		} else if (stage == 3) {
			apply(player, BoozeEffects.HEAVY_DRUNK, 60, 0);
			apply(player, MobEffects.CONFUSION, 140, 0);
			apply(player, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
			apply(player, MobEffects.DIG_SLOWDOWN, 60, 1);
			apply(player, MobEffects.DAMAGE_RESISTANCE, 60, 0);
			if (player.getRandom().nextDouble() < 0.2D) {
				apply(player, MobEffects.BLINDNESS, 50, 0);
			}
			stumble(player, 0.5D);
			if (BoozeConfig.VOMIT.get() && player.getRandom().nextDouble() < BoozeConfig.VOMIT_CHANCE.get()) {
				vomit(player);
			}
			if (d.passedOutUntil <= now && BoozeConfig.PASS_OUT_ENABLED.get()
					&& player.getRandom().nextDouble() < BoozeConfig.PASS_OUT_CHANCE.get()) {
				int min = BoozeConfig.PASS_OUT_SECONDS_MIN.get();
				int max = Math.max(min, BoozeConfig.PASS_OUT_SECONDS_MAX.get());
				passOut(player, min + player.getRandom().nextInt(max - min + 1));
			}
		}

		if (BoozeConfig.HANGOVER_ENABLED.get() && d.alcohol < 3.0D && d.peak >= heavy * 0.8D) {
			d.hangoverUntil = now + BoozeConfig.HANGOVER_SECONDS.get() * 1000L;
			d.peak = 0.0D;
			msg(player, "msg.boozecraft.hangover_start");
		}
		if (d.hangoverUntil > now) {
			apply(player, BoozeEffects.HANGOVER, 60, 0);
			apply(player, MobEffects.WEAKNESS, 60, 0);
			apply(player, MobEffects.DIG_SLOWDOWN, 60, 0);
			if (player.getRandom().nextDouble() < 0.12D) {
				apply(player, MobEffects.CONFUSION, 60, 0);
			}
		}

		if (isAddicted(d) && now - d.lastAlcoholTime > BoozeConfig.WITHDRAWAL_DELAY_SECONDS.get() * 1000L) {
			apply(player, BoozeEffects.WITHDRAWAL, 60, 0);
			apply(player, MobEffects.WEAKNESS, 60, 1);
			apply(player, MobEffects.MOVEMENT_SLOWDOWN, 60, 0);
			if (player.getRandom().nextDouble() < 0.08D) {
				apply(player, MobEffects.CONFUSION, 100, 0);
			}
			if (now - d.lastWithdrawalMessage > 45000L) {
				d.lastWithdrawalMessage = now;
				msg(player, "msg.boozecraft.withdrawal");
			}
		}

		if (d.caffeine > BoozeConfig.JITTERS_THRESHOLD.get()) {
			apply(player, BoozeEffects.JITTERS, 60, 0);
			apply(player, MobEffects.DIG_SPEED, 60, 1);
			if (player.getRandom().nextDouble() < 0.25D) {
				apply(player, MobEffects.CONFUSION, 60, 0);
			}
		} else if (d.caffeine > 10.0D) {
			apply(player, BoozeEffects.CAFFEINE, 60, 0);
			apply(player, MobEffects.MOVEMENT_SPEED, 60, 0);
			apply(player, MobEffects.DIG_SPEED, 60, 0);
		}
	}

	// ----------------------------------------------------------------- pass out

	public static void passOut(ServerPlayer player, int seconds) {
		PlayerBoozeData d = data(player);
		long now = System.currentTimeMillis();
		d.passedOutUntil = now + seconds * 1000L;
		d.passOuts++;
		ANCHOR.put(player.getUUID(), player.position());

		apply(player, BoozeEffects.PASSED_OUT, seconds * 20 + 20, 0);
		if (BoozeConfig.PASS_OUT_BLINDNESS.get()) {
			apply(player, MobEffects.BLINDNESS, seconds * 20 + 20, 0);
		}
		apply(player, MobEffects.MOVEMENT_SLOWDOWN, seconds * 20 + 20, 6);
		apply(player, MobEffects.WEAKNESS, seconds * 20 + 20, 2);

		runCommands(player, BoozeConfig.PASS_OUT_COMMANDS.get());
		if (BoozeConfig.PASS_OUT_FORCE_POSE.get() && player.getVehicle() == null) {
			PassOutHelper.forceLay(player, true);
		}
		BoozeSounds.play(player.level(), player.blockPosition(), "entity.player.big_fall", 0.6F, 0.7F);
		msg(player, "msg.boozecraft.passed_out");
	}

	public static void wake(ServerPlayer player) {
		PlayerBoozeData d = data(player);
		d.passedOutUntil = 0L;
		ANCHOR.remove(player.getUUID());
		player.removeEffect(BoozeEffects.PASSED_OUT);
		player.removeEffect(MobEffects.BLINDNESS);
		player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		PassOutHelper.forceLay(player, false);
		if (player.getVehicle() != null) {
			player.stopRiding();
		}
		runCommands(player, BoozeConfig.PASS_OUT_END_COMMANDS.get());
		msg(player, "msg.boozecraft.woke_up");
	}

	private static void holdDown(ServerPlayer player, PlayerBoozeData d) {
		if (player.getVehicle() == null) {
			Vec3 anchor = ANCHOR.computeIfAbsent(player.getUUID(), key -> player.position());
			player.setDeltaMovement(0.0D, Math.min(0.0D, player.getDeltaMovement().y), 0.0D);
			if (player.position().distanceToSqr(anchor) > 1.0D) {
				player.teleportTo(anchor.x, anchor.y, anchor.z);
			}
			if (player.tickCount % 20 == 0 && BoozeConfig.PASS_OUT_REAPPLY.get()) {
				runCommands(player, BoozeConfig.PASS_OUT_COMMANDS.get());
				if (BoozeConfig.PASS_OUT_FORCE_POSE.get()) {
					PassOutHelper.forceLay(player, true);
				}
			}
		}
		if (player.tickCount % 20 == 0) {
			apply(player, BoozeEffects.PASSED_OUT, 60, 0);
			apply(player, MobEffects.MOVEMENT_SLOWDOWN, 60, 6);
			if (BoozeConfig.PASS_OUT_BLINDNESS.get()) {
				apply(player, MobEffects.BLINDNESS, 60, 0);
			}
		}
	}

	/** Runs plugin or vanilla commands as the player with permission level 4 (GSit friendly). */
	private static void runCommands(ServerPlayer player, String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return;
		}
		MinecraftServer srv = player.getServer();
		if (srv == null) {
			return;
		}
		CommandSourceStack source = player.createCommandSourceStack().withSuppressedOutput().withPermission(4);
		for (String part : raw.split(";")) {
			String command = part.trim().replace("%player%", player.getGameProfile().getName());
			if (command.isEmpty()) {
				continue;
			}
			if (command.startsWith("/")) {
				command = command.substring(1);
			}
			try {
				srv.getCommands().performPrefixedCommand(source, command);
			} catch (Throwable t) {
				BoozeCraft.LOGGER.warn("[BoozeCraft] pass-out command '{}' failed: {}", command, t.toString());
			}
		}
	}

	// ------------------------------------------------------------------ helpers

	private static void stumble(ServerPlayer player, double chance) {
		if (!BoozeConfig.STUMBLE.get() || !player.onGround()) {
			return;
		}
		if (player.getRandom().nextDouble() > chance) {
			return;
		}
		double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(motion.x + Math.cos(angle) * 0.3D, motion.y, motion.z + Math.sin(angle) * 0.3D);
		player.hurtMarked = true;
	}

	private static void vomit(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		Vec3 look = player.getLookAngle();
		level.sendParticles(ParticleTypes.ITEM_SLIME,
				player.getX() + look.x * 0.6D, player.getEyeY() - 0.2D, player.getZ() + look.z * 0.6D,
				25, 0.2D, 0.12D, 0.2D, 0.02D);
		BoozeSounds.play(level, player.blockPosition(), "entity.player.burp", 1.0F, 0.7F);
		apply(player, MobEffects.HUNGER, 200, 1);
		player.causeFoodExhaustion(2.0F);
		msg(player, "msg.boozecraft.vomit");
	}

	private static void apply(LivingEntity entity, Holder<MobEffect> effect, int ticks, int amplifier) {
		entity.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, false, true));
	}

	private static void msg(ServerPlayer player, String key) {
		player.displayClientMessage(Component.translatable(key), true);
	}

	// -------------------------------------------------------------- persistence

	public static void load(MinecraftServer srv) {
		server = srv;
		DATA.clear();
		STAGE.clear();
		ANCHOR.clear();
		try {
			Path path = file(srv);
			if (path == null || !Files.exists(path)) {
				return;
			}
			String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) {
				return;
			}
			for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				try {
					DATA.put(UUID.fromString(entry.getKey()),
							PlayerBoozeData.fromJson(entry.getValue().getAsJsonObject()));
				} catch (IllegalArgumentException ignored) {
					// bad uuid, skip
				}
			}
			BoozeCraft.LOGGER.info("[BoozeCraft] loaded state for {} players", DATA.size());
		} catch (Exception e) {
			BoozeCraft.LOGGER.warn("[BoozeCraft] could not read player state: {}", e.toString());
		}
	}

	public static void save() {
		MinecraftServer srv = server;
		if (srv == null) {
			return;
		}
		try {
			JsonObject root = new JsonObject();
			for (Map.Entry<UUID, PlayerBoozeData> entry : DATA.entrySet()) {
				root.add(entry.getKey().toString(), entry.getValue().toJson());
			}
			Path path = file(srv);
			if (path == null) {
				return;
			}
			Files.write(path, root.toString().getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			BoozeCraft.LOGGER.warn("[BoozeCraft] could not write player state: {}", e.toString());
		}
	}

	public static void shutdown() {
		save();
		server = null;
	}

	private static Path file(MinecraftServer srv) {
		try {
			return srv.getWorldPath(LevelResource.ROOT).resolve("boozecraft_players.json");
		} catch (Exception e) {
			return null;
		}
	}

	private BoozeManager() {
	}
}
