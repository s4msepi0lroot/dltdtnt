package com.s4fmer.boozecraft.cmd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.s4fmer.boozecraft.booze.BoozeManager;
import com.s4fmer.boozecraft.booze.PlayerBoozeData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /booze status | sober | passout | wake | set alcohol|addiction|caffeine */
public final class BoozeCommand {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("booze")
				.then(Commands.literal("status")
						.executes(ctx -> status(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.requires(source -> source.hasPermission(2))
								.executes(ctx -> status(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("sober")
						.requires(source -> source.hasPermission(2))
						.executes(ctx -> sober(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> sober(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("passout")
						.requires(source -> source.hasPermission(2))
						.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
								.executes(ctx -> passOut(ctx.getSource(), ctx.getSource().getPlayerOrException(),
										IntegerArgumentType.getInteger(ctx, "seconds")))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> passOut(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
												IntegerArgumentType.getInteger(ctx, "seconds"))))))
				.then(Commands.literal("wake")
						.requires(source -> source.hasPermission(2))
						.executes(ctx -> wake(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> wake(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("set")
						.requires(source -> source.hasPermission(2))
						.then(Commands.literal("alcohol")
								.then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 1000.0D))
										.executes(ctx -> set(ctx.getSource(), ctx.getSource().getPlayerOrException(),
												"alcohol", DoubleArgumentType.getDouble(ctx, "value")))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(ctx -> set(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
														"alcohol", DoubleArgumentType.getDouble(ctx, "value"))))))
						.then(Commands.literal("addiction")
								.then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 100.0D))
										.executes(ctx -> set(ctx.getSource(), ctx.getSource().getPlayerOrException(),
												"addiction", DoubleArgumentType.getDouble(ctx, "value")))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(ctx -> set(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
														"addiction", DoubleArgumentType.getDouble(ctx, "value"))))))
						.then(Commands.literal("caffeine")
								.then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 200.0D))
										.executes(ctx -> set(ctx.getSource(), ctx.getSource().getPlayerOrException(),
												"caffeine", DoubleArgumentType.getDouble(ctx, "value")))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(ctx -> set(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
														"caffeine", DoubleArgumentType.getDouble(ctx, "value")))))))); 
	}

	private static int status(CommandSourceStack source, ServerPlayer target) {
		PlayerBoozeData d = BoozeManager.data(target);
		int stage = BoozeManager.stageOf(d);
		String text = String.format("%s: alcohol %.1f (stage %d), addiction %.1f%s, caffeine %.1f, drinks %d (alcoholic %d), pass-outs %d",
				target.getGameProfile().getName(), d.alcohol, stage, d.addiction,
				BoozeManager.isAddicted(d) ? " [ADDICTED]" : "", d.caffeine,
				d.drinksTotal, d.drinksAlcohol, d.passOuts);
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int sober(CommandSourceStack source, ServerPlayer target) {
		BoozeManager.sober(target);
		source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " is sober now"), true);
		return 1;
	}

	private static int passOut(CommandSourceStack source, ServerPlayer target, int seconds) {
		BoozeManager.passOut(target, seconds);
		source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " passed out for " + seconds + "s"), true);
		return 1;
	}

	private static int wake(CommandSourceStack source, ServerPlayer target) {
		BoozeManager.wake(target);
		source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " woke up"), true);
		return 1;
	}

	private static int set(CommandSourceStack source, ServerPlayer target, String field, double value) {
		PlayerBoozeData d = BoozeManager.data(target);
		if ("alcohol".equals(field)) {
			d.alcohol = value;
			d.peak = Math.max(d.peak, value);
			d.lastAlcoholTime = System.currentTimeMillis();
		} else if ("addiction".equals(field)) {
			d.addiction = value;
		} else {
			d.caffeine = value;
		}
		source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() + ": " + field + " = " + value), true);
		return 1;
	}

	private BoozeCommand() {
	}
}
