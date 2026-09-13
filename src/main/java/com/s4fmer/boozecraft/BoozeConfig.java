package com.s4fmer.boozecraft;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * All tuning knobs. The server config lives in <world>/serverconfig/boozecraft-server.toml,
 * the client config in config/boozecraft-client.toml.
 */
public final class BoozeConfig {

	public static final ModConfigSpec SERVER_SPEC;
	public static final ModConfigSpec CLIENT_SPEC;

	// ---- drunkenness ----
	public static final ModConfigSpec.DoubleValue DRUNK_THRESHOLD;
	public static final ModConfigSpec.DoubleValue HEAVY_THRESHOLD;
	public static final ModConfigSpec.DoubleValue SOBER_RATE;
	public static final ModConfigSpec.DoubleValue ALCOHOL_CAP;
	public static final ModConfigSpec.DoubleValue DRINK_STRENGTH_MULTIPLIER;
	public static final ModConfigSpec.BooleanValue STARTER_EFFECTS_ONLY_WHEN_SOBER;
	public static final ModConfigSpec.BooleanValue STATUS_MESSAGES;
	public static final ModConfigSpec.BooleanValue STUMBLE;
	public static final ModConfigSpec.BooleanValue VOMIT;
	public static final ModConfigSpec.DoubleValue VOMIT_CHANCE;
	public static final ModConfigSpec.BooleanValue SLUR_CHAT;

	// ---- random drunk events ----
	public static final ModConfigSpec.BooleanValue EVENTS_ENABLED;
	public static final ModConfigSpec.BooleanValue EVENTS_ONLY_HEAVY;
	public static final ModConfigSpec.DoubleValue EVENT_HICCUP;
	public static final ModConfigSpec.DoubleValue EVENT_BLUR;
	public static final ModConfigSpec.DoubleValue EVENT_TRIP;
	public static final ModConfigSpec.DoubleValue EVENT_DROP;
	public static final ModConfigSpec.DoubleValue EVENT_BREAK_GLASS;
	public static final ModConfigSpec.DoubleValue EVENT_SING;
	public static final ModConfigSpec.DoubleValue EVENT_WAKE_TELEPORT;
	public static final ModConfigSpec.IntValue EVENT_WAKE_RADIUS;

	// ---- pass out / black out ----
	public static final ModConfigSpec.BooleanValue PASS_OUT_ENABLED;
	public static final ModConfigSpec.DoubleValue PASS_OUT_CHANCE;
	public static final ModConfigSpec.IntValue PASS_OUT_SECONDS_MIN;
	public static final ModConfigSpec.IntValue PASS_OUT_SECONDS_MAX;
	public static final ModConfigSpec.ConfigValue<String> PASS_OUT_COMMANDS;
	public static final ModConfigSpec.ConfigValue<String> PASS_OUT_END_COMMANDS;
	public static final ModConfigSpec.BooleanValue PASS_OUT_REAPPLY;
	public static final ModConfigSpec.BooleanValue PASS_OUT_BLINDNESS;
	public static final ModConfigSpec.BooleanValue PASS_OUT_FORCE_POSE;

	// ---- hangover ----
	public static final ModConfigSpec.BooleanValue HANGOVER_ENABLED;
	public static final ModConfigSpec.IntValue HANGOVER_SECONDS;

	// ---- addiction ----
	public static final ModConfigSpec.BooleanValue ADDICTION_ENABLED;
	public static final ModConfigSpec.DoubleValue ADDICTION_THRESHOLD;
	public static final ModConfigSpec.DoubleValue ADDICTION_GAIN_MULTIPLIER;
	public static final ModConfigSpec.DoubleValue ADDICTION_DECAY_PER_MINUTE;
	public static final ModConfigSpec.IntValue WITHDRAWAL_DELAY_SECONDS;

	// ---- caffeine ----
	public static final ModConfigSpec.DoubleValue CAFFEINE_SOBER_FACTOR;
	public static final ModConfigSpec.DoubleValue CAFFEINE_DECAY_PER_SECOND;
	public static final ModConfigSpec.DoubleValue JITTERS_THRESHOLD;

	// ---- machines ----
	public static final ModConfigSpec.DoubleValue PROCESS_SPEED_MULTIPLIER;
	public static final ModConfigSpec.BooleanValue STILL_NEEDS_HEAT;

	// ---- client ----
	public static final ModConfigSpec.BooleanValue CAMERA_SWAY;
	public static final ModConfigSpec.DoubleValue CAMERA_SWAY_STRENGTH;

	static {
		ModConfigSpec.Builder s = new ModConfigSpec.Builder();

		s.comment("Drunkenness model. 'Alcohol level' is an abstract 0-150 scale;",
				"one shot of 40% spirit adds about 22 points.").push("drunkenness");
		DRUNK_THRESHOLD = s.comment("Alcohol level at which stage 2 (Drunk) starts.")
				.defineInRange("drunkThreshold", 20.0D, 1.0D, 500.0D);
		HEAVY_THRESHOLD = s.comment("Alcohol level at which stage 3 (Heavily drunk) starts.")
				.defineInRange("heavyDrunkThreshold", 55.0D, 1.0D, 500.0D);
		SOBER_RATE = s.comment("Alcohol points burned per second. 0.18 => one vodka shot keeps you drunk for ~2 minutes.")
				.defineInRange("soberRatePerSecond", 0.18D, 0.001D, 50.0D);
		ALCOHOL_CAP = s.comment("Hard cap for the alcohol level.")
				.defineInRange("alcoholCap", 150.0D, 10.0D, 1000.0D);
		DRINK_STRENGTH_MULTIPLIER = s.comment("Global multiplier for how much every drink adds.")
				.defineInRange("drinkStrengthMultiplier", 1.0D, 0.05D, 20.0D);
		STARTER_EFFECTS_ONLY_WHEN_SOBER = s.comment("If true, the nice per-drink buffs only trigger while you are still below the Drunk threshold.")
				.define("starterEffectsOnlyWhenSober", true);
		STATUS_MESSAGES = s.comment("Send action bar messages about your state.")
				.define("statusMessages", true);
		STUMBLE = s.comment("Random stumbling while drunk.")
				.define("stumble", true);
		VOMIT = s.comment("Vomiting while heavily drunk.")
				.define("vomit", true);
		VOMIT_CHANCE = s.comment("Chance per second to vomit while heavily drunk.")
				.defineInRange("vomitChancePerSecond", 0.02D, 0.0D, 1.0D);
		SLUR_CHAT = s.comment("Garble chat messages of drunk players (ignored if the server routes chat through a plugin).")
				.define("slurChat", true);
		s.pop();

		s.comment("Passing out. Works with the GSit plugin on hybrid servers (Youer/Mohist):",
				"the command is executed as the player with permission level 4.").push("passOut");
		PASS_OUT_ENABLED = s.define("enabled", true);
		PASS_OUT_CHANCE = s.comment("Chance per second to pass out while heavily drunk.")
				.defineInRange("chancePerSecond", 0.012D, 0.0D, 1.0D);
		PASS_OUT_SECONDS_MIN = s.defineInRange("secondsMin", 20, 1, 3600);
		PASS_OUT_SECONDS_MAX = s.defineInRange("secondsMax", 45, 1, 3600);
		PASS_OUT_COMMANDS = s.comment("Commands run when a player passes out. Separate several with ';'.",
				"%player% is replaced with the player name. Default = GSit's /lay.",
				"Set to an empty string to use the built in immobilise-only fallback.")
				.define("commands", "lay");
		PASS_OUT_END_COMMANDS = s.comment("Commands run when the player wakes up (usually not needed, GSit releases on dismount).")
				.define("endCommands", "");
		PASS_OUT_REAPPLY = s.comment("Re-run the command every second while passed out, so the player cannot stand up.")
				.define("reapplyCommand", true);
		PASS_OUT_BLINDNESS = s.comment("Apply blindness while passed out (looks like closed eyes).")
				.define("blindness", true);
		PASS_OUT_FORCE_POSE = s.comment("Try to force a lying pose without GSit (uses a reflective vanilla call, silently ignored if unavailable).")
				.define("forcePoseFallback", true);
		s.pop();

		s.push("hangover");
		HANGOVER_ENABLED = s.define("enabled", true);
		HANGOVER_SECONDS = s.comment("Hangover duration after sobering up from a heavy session.")
				.defineInRange("seconds", 180, 1, 100000);
		s.pop();

		s.comment("Long term addiction. Drinking often raises 'addiction'; staying sober lowers it.").push("addiction");
		ADDICTION_ENABLED = s.define("enabled", true);
		ADDICTION_THRESHOLD = s.comment("Addiction points needed to become addicted (0-100).")
				.defineInRange("threshold", 40.0D, 1.0D, 100.0D);
		ADDICTION_GAIN_MULTIPLIER = s.comment("Multiplier for addiction gained per alcoholic drink.")
				.defineInRange("gainMultiplier", 1.0D, 0.0D, 50.0D);
		ADDICTION_DECAY_PER_MINUTE = s.comment("Addiction points lost per minute without alcohol.")
				.defineInRange("decayPerMinute", 0.8D, 0.0D, 100.0D);
		WITHDRAWAL_DELAY_SECONDS = s.comment("Seconds without alcohol before an addicted player starts having withdrawal.")
				.defineInRange("withdrawalDelaySeconds", 600, 10, 1000000);
		s.pop();

		s.push("caffeine");
		CAFFEINE_SOBER_FACTOR = s.comment("How strongly caffeine burns alcohol (fraction of the caffeine value).")
				.defineInRange("soberFactor", 0.25D, 0.0D, 5.0D);
		CAFFEINE_DECAY_PER_SECOND = s.defineInRange("decayPerSecond", 0.35D, 0.001D, 50.0D);
		JITTERS_THRESHOLD = s.comment("Caffeine value above which you get the jitters.")
				.defineInRange("jittersThreshold", 80.0D, 1.0D, 1000.0D);
		s.pop();

		s.push("machines");
		PROCESS_SPEED_MULTIPLIER = s.comment("Multiplier for fermenter / still / aging barrel speed. 2.0 = twice as fast.")
				.defineInRange("speedMultiplier", 1.0D, 0.05D, 100.0D);
		STILL_NEEDS_HEAT = s.comment("The still only works with fire, lava, a lit campfire or magma below it.")
				.define("stillNeedsHeat", true);
		s.pop();

		s.comment("Random events for drunk players. Every chance is rolled once per second",
				"while the player is heavily drunk (see onlyWhenHeavilyDrunk).").push("events");
		EVENTS_ENABLED = s.define("enabled", true);
		EVENTS_ONLY_HEAVY = s.comment("true = events only while heavily drunk.",
				"false = merely drunk players also get them, at 35% of the chance.")
				.define("onlyWhenHeavilyDrunk", true);
		EVENT_HICCUP = s.comment("Hiccup: sound, splash particles, action bar line.")
				.defineInRange("hiccupChance", 0.06D, 0.0D, 1.0D);
		EVENT_BLUR = s.comment("Double vision: a six second nausea burst.")
				.defineInRange("blurChance", 0.04D, 0.0D, 1.0D);
		EVENT_TRIP = s.comment("Trip over: a shove plus one point of fall damage.")
				.defineInRange("tripChance", 0.02D, 0.0D, 1.0D);
		EVENT_DROP = s.comment("Drop whatever is in the main hand.")
				.defineInRange("dropItemChance", 0.012D, 0.0D, 1.0D);
		EVENT_BREAK_GLASS = s.comment("Break the glass, mug, shot glass or can held in the main hand.")
				.defineInRange("breakGlassChance", 0.02D, 0.0D, 1.0D);
		EVENT_SING = s.comment("Sing out loud - everyone within 16 blocks sees it.")
				.defineInRange("singChance", 0.02D, 0.0D, 1.0D);
		EVENT_WAKE_TELEPORT = s.comment("Chance to wake up somewhere else after a black out.")
				.defineInRange("wakeUpElsewhereChance", 0.35D, 0.0D, 1.0D);
		EVENT_WAKE_RADIUS = s.comment("How far away a player can wake up, in blocks.")
				.defineInRange("wakeUpElsewhereRadius", 8, 1, 64);
		s.pop();

		SERVER_SPEC = s.build();

		ModConfigSpec.Builder c = new ModConfigSpec.Builder();
		c.push("visuals");
		CAMERA_SWAY = c.comment("Sway the camera while drunk (client side only).")
				.define("cameraSway", true);
		CAMERA_SWAY_STRENGTH = c.defineInRange("cameraSwayStrength", 1.0D, 0.0D, 5.0D);
		c.pop();
		CLIENT_SPEC = c.build();
	}

	private BoozeConfig() {
	}
}
