#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""BoozeCraft patch pass: JEI/EMI recipe support + drunk events.

Run it right after the generator:

    python3 boozecraft_gen.py && python3 boozecraft_patch.py

The pass is idempotent - running it twice changes nothing.
"""
import io
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
if os.path.isdir(os.path.join(HERE, "boozecraft", "src")):
    ROOT = os.path.join(HERE, "boozecraft")
elif os.path.isdir(os.path.join(HERE, "..", "src")):
    ROOT = os.path.abspath(os.path.join(HERE, ".."))
else:
    ROOT = HERE

JAVA = os.path.join(ROOT, "src", "main", "java", "com", "s4fmer", "boozecraft")
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "boozecraft")
JEI_VERSION = "19.27.0.350"


def read(path):
    with io.open(path, "r", encoding="utf-8") as handle:
        return handle.read()


def write(path, text):
    folder = os.path.dirname(path)
    if folder and not os.path.isdir(folder):
        os.makedirs(folder)
    with io.open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)


def write_json(path, obj):
    write(path, json.dumps(obj, indent=2, ensure_ascii=False, sort_keys=True) + "\n")


def patch(path, old, new, marker):
    """Replace a unique anchor. Does nothing when marker is already present."""
    text = read(path)
    if marker in text:
        return False
    if text.count(old) != 1:
        raise SystemExit("anchor missing or not unique in %s: %r" % (path, old[:70]))
    write(path, text.replace(old, new))
    return True


# ---------------------------------------------------------------------------
# 1. drunk events
# ---------------------------------------------------------------------------
DRUNK_EVENTS = r'''package com.s4fmer.boozecraft.booze;

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
'''

write(os.path.join(JAVA, "booze", "DrunkEvents.java"), DRUNK_EVENTS)
print("DrunkEvents.java written")

# hook the events into the player tick
patch(
    os.path.join(JAVA, "booze", "BoozeEvents.java"),
    "\t\t\tBoozeManager.tick(player);\n",
    "\t\t\tBoozeManager.tick(player);\n\t\t\tDrunkEvents.tick(player);\n",
    "DrunkEvents.tick(player)",
)

# ---------------------------------------------------------------------------
# 2. config: [events] section
# ---------------------------------------------------------------------------
CONFIG_FIELDS = r'''	public static final ModConfigSpec.BooleanValue SLUR_CHAT;

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
'''

CONFIG_INIT = r'''		s.comment("Random events for drunk players. Every chance is rolled once per second",
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

		SERVER_SPEC = s.build();'''

CONFIG = os.path.join(JAVA, "BoozeConfig.java")
patch(CONFIG, "\tpublic static final ModConfigSpec.BooleanValue SLUR_CHAT;\n", CONFIG_FIELDS,
      "EVENTS_ENABLED")
patch(CONFIG, "\t\tSERVER_SPEC = s.build();", CONFIG_INIT, 'push("events")')
print("config patched")

# ---------------------------------------------------------------------------
# 3. lang keys
# ---------------------------------------------------------------------------
EXTRA_LANG = {
    "msg.boozecraft.event_hiccup": ("*hic*", "Ик..."),
    "msg.boozecraft.event_blur": ("Everything is doubling.", "В глазах всё двоится."),
    "msg.boozecraft.event_trip": ("You trip over your own feet.", "Вы споткнулись на ровном месте."),
    "msg.boozecraft.event_drop": ("You drop %s.", "Вы выронили %s."),
    "msg.boozecraft.event_break": ("The glass slips out of your hand and shatters.",
                                   "Стакан выскользнул из рук и разбился."),
    "msg.boozecraft.event_sing": ("%s is singing very loudly.", "%s орёт песни на всю округу."),
    "msg.boozecraft.event_wake_elsewhere": ("You wake up... somewhere else.",
                                            "Вы проснулись... где-то не там."),
    "gui.boozecraft.category.mixing": ("Bar counter", "Барная стойка"),
    "gui.boozecraft.seconds": ("%s s", "%s с"),
    "jei.boozecraft.info.drink": (
        "Alcohol first gives a short starter effect (only while you are sober), "
        "then tipsy, drunk and heavily drunk for two to three minutes. "
        "Heavy drunkenness brings stumbling, vomiting, random events and black outs. "
        "Drinking often builds addiction; milk, coffee and the hangover cure help.",
        "Алкоголь сначала даёт короткий эффект (только пока вы трезвы), затем идут "
        "«навеселе», «пьян» и «сильное опьянение» на 2-3 минуты. При сильном опьянении "
        "вас шатает, тошнит, случаются события и можно вырубиться. Частые попойки дают "
        "зависимость; помогают молоко, кофе и средство от похмелья."),
    "jei.boozecraft.info.counter": (
        "Right click the bar counter with a drink to put it down, then right click with a "
        "shaker to mix everything into a cocktail. Sneak + right click takes drinks back.",
        "ПКМ по барной стойке напитком - поставить его, затем ПКМ шейкером - смешать всё "
        "в коктейль. Shift + ПКМ - забрать напитки обратно."),
    "jei.boozecraft.info.machine": (
        "Right click with ingredients to load the machine, right click empty handed to see "
        "the progress and to take the result. The still needs fire, lava or a lit campfire below it.",
        "ПКМ с ингредиентами - загрузить аппарат, ПКМ пустой рукой - посмотреть прогресс и "
        "забрать результат. Самогонному аппарату нужен огонь, лава или костёр снизу."),
}

for lang_file, index in (("en_us.json", 0), ("ru_ru.json", 1)):
    path = os.path.join(ASSETS, "lang", lang_file)
    data = json.loads(read(path))
    for key, values in EXTRA_LANG.items():
        data[key] = values[index]
    write_json(path, data)
print("lang patched: +%d keys" % len(EXTRA_LANG))

# ---------------------------------------------------------------------------
# 4. gradle: optional JEI API dependency
# ---------------------------------------------------------------------------
REPOS_OLD = "repositories {\n    mavenCentral()\n}\n"
REPOS_NEW = r'''repositories {
    mavenCentral()
    maven {
        // JEI API lives here
        name = "Jared's maven"
        url = "https://maven.blamejared.com/"
    }
    maven {
        // fallback mirror for the JEI API
        name = "ModMaven"
        url = "https://modmaven.dev"
    }
}

// Recipe viewer support. The JEI API is compile only - the mod runs fine without
// JEI installed, and EMI shows the same recipes through its JEI compatibility layer.
// Build completely without it (no extra downloads):
//   ./gradlew build -Pjei_support=false
def jeiSupport = Boolean.parseBoolean((project.findProperty('jei_support') ?: 'true').toString())

dependencies {
    if (jeiSupport) {
        compileOnly "mezz.jei:jei-1.21.1-common-api:${jei_version}"
    }
}

if (!jeiSupport) {
    sourceSets.main.java {
        exclude 'com/s4fmer/boozecraft/compat/jei/**'
    }
}
'''

patch(os.path.join(ROOT, "build.gradle"), REPOS_OLD, REPOS_NEW, "jeiSupport")

GRADLE_PROPS = os.path.join(ROOT, "gradle.properties")
props = read(GRADLE_PROPS)
if "jei_version" not in props:
    if not props.endswith("\n"):
        props += "\n"
    props += ("\n# Recipe viewers. The JEI API is compile only; EMI reads the same plugin.\n"
              "# Build without it: ./gradlew build -Pjei_support=false\n"
              "jei_support=true\n"
              "jei_version=%s\n" % JEI_VERSION)
    write(GRADLE_PROPS, props)
print("gradle patched")

# ---------------------------------------------------------------------------
# 5. JEI plugin (compat/jei) - EMI reads it through its JEI compat layer
# ---------------------------------------------------------------------------
PROCESS_CATEGORY = r'''package com.s4fmer.boozecraft.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.s4fmer.boozecraft.BoozeCraft;
import com.s4fmer.boozecraft.block.ProcessorType;
import com.s4fmer.boozecraft.recipe.ProcessRecipes;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** GENERATED - JEI category for the fermenter, the still and the aging barrel. */
public class ProcessCategory implements IRecipeCategory<ProcessRecipes.Entry> {

	public static final RecipeType<ProcessRecipes.Entry> FERMENTING =
			RecipeType.create(BoozeCraft.MODID, "fermenting", ProcessRecipes.Entry.class);
	public static final RecipeType<ProcessRecipes.Entry> DISTILLING =
			RecipeType.create(BoozeCraft.MODID, "distilling", ProcessRecipes.Entry.class);
	public static final RecipeType<ProcessRecipes.Entry> AGING =
			RecipeType.create(BoozeCraft.MODID, "aging", ProcessRecipes.Entry.class);

	private static final int WIDTH = 150;
	private static final int HEIGHT = 44;

	private final ProcessorType machine;
	private final IDrawable icon;
	private final IDrawable slot;

	public ProcessCategory(IGuiHelper helper, ProcessorType machine, Block block) {
		this.machine = machine;
		this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(block));
		this.slot = helper.getSlotDrawable();
	}

	public static RecipeType<ProcessRecipes.Entry> typeOf(ProcessorType machine) {
		if (machine == ProcessorType.STILL) {
			return DISTILLING;
		}
		if (machine == ProcessorType.AGING) {
			return AGING;
		}
		return FERMENTING;
	}

	public static List<ProcessRecipes.Entry> recipesOf(ProcessorType machine) {
		List<ProcessRecipes.Entry> out = new ArrayList<>();
		for (ProcessRecipes.Entry entry : ProcessRecipes.all()) {
			if (entry.type == machine) {
				out.add(entry);
			}
		}
		return out;
	}

	@Override
	public RecipeType<ProcessRecipes.Entry> getRecipeType() {
		return typeOf(this.machine);
	}

	@Override
	public Component getTitle() {
		return Component.translatable(this.machine.translationKey());
	}

	@Override
	public IDrawable getIcon() {
		return this.icon;
	}

	@Override
	public int getWidth() {
		return WIDTH;
	}

	@Override
	public int getHeight() {
		return HEIGHT;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, ProcessRecipes.Entry recipe, IFocusGroup focuses) {
		int x = 1;
		for (String id : recipe.inputs) {
			Item item = ProcessRecipes.item(id);
			if (item != null) {
				builder.addSlot(RecipeIngredientRole.INPUT, x, 22)
						.setBackground(this.slot, -1, -1)
						.addItemStack(new ItemStack(item));
			}
			x += 19;
		}
		Item result = ProcessRecipes.item(recipe.result);
		if (result != null) {
			builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 21, 22)
					.setBackground(this.slot, -1, -1)
					.addItemStack(new ItemStack(result, recipe.count));
		}
	}

	@Override
	public void draw(ProcessRecipes.Entry recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
		Font font = Minecraft.getInstance().font;
		Component seconds = Component.translatable("gui.boozecraft.seconds", Integer.toString(recipe.time / 20));
		graphics.drawString(font, seconds.getString(), 1, 4, 0x404040, false);
	}
}
'''

MIX_CATEGORY = r'''package com.s4fmer.boozecraft.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.s4fmer.boozecraft.BoozeCraft;
import com.s4fmer.boozecraft.recipe.MixRecipes;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** GENERATED - JEI category for bar counter mixing. */
public class MixCategory implements IRecipeCategory<MixRecipes.Entry> {

	public static final RecipeType<MixRecipes.Entry> TYPE =
			RecipeType.create(BoozeCraft.MODID, "mixing", MixRecipes.Entry.class);

	private static final int WIDTH = 150;
	private static final int HEIGHT = 44;

	private final IDrawable icon;
	private final IDrawable slot;

	public MixCategory(IGuiHelper helper, Block counter) {
		this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(counter));
		this.slot = helper.getSlotDrawable();
	}

	public static List<MixRecipes.Entry> recipes() {
		return new ArrayList<>(MixRecipes.all());
	}

	@Override
	public RecipeType<MixRecipes.Entry> getRecipeType() {
		return TYPE;
	}

	@Override
	public Component getTitle() {
		return Component.translatable("gui.boozecraft.category.mixing");
	}

	@Override
	public IDrawable getIcon() {
		return this.icon;
	}

	@Override
	public int getWidth() {
		return WIDTH;
	}

	@Override
	public int getHeight() {
		return HEIGHT;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, MixRecipes.Entry recipe, IFocusGroup focuses) {
		int x = 1;
		for (String id : recipe.inputs) {
			Item item = MixRecipes.item(id);
			if (item != null) {
				builder.addSlot(RecipeIngredientRole.INPUT, x, 14)
						.setBackground(this.slot, -1, -1)
						.addItemStack(new ItemStack(item));
			}
			x += 19;
		}
		Item shaker = MixRecipes.item("boozecraft:shaker");
		if (shaker != null) {
			builder.addSlot(RecipeIngredientRole.CATALYST, x + 6, 14)
					.setBackground(this.slot, -1, -1)
					.addItemStack(new ItemStack(shaker));
		}
		Item result = MixRecipes.item(recipe.result);
		if (result != null) {
			builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 21, 14)
					.setBackground(this.slot, -1, -1)
					.addItemStack(new ItemStack(result, recipe.count));
		}
	}
}
'''

JEI_PLUGIN = r'''package com.s4fmer.boozecraft.compat.jei;

import com.s4fmer.boozecraft.BoozeCraft;
import com.s4fmer.boozecraft.block.ProcessorType;
import com.s4fmer.boozecraft.drink.DrinkItem;
import com.s4fmer.boozecraft.reg.BoozeBlocks;
import com.s4fmer.boozecraft.reg.BoozeItems;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** GENERATED - JEI plugin. EMI shows the same recipes through its JEI compat layer. */
@JeiPlugin
public class BoozeJeiPlugin implements IModPlugin {

	@Override
	public ResourceLocation getPluginUid() {
		return BoozeCraft.id("jei");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		IGuiHelper helper = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(
				new ProcessCategory(helper, ProcessorType.FERMENTER, BoozeBlocks.FERMENTER.get()),
				new ProcessCategory(helper, ProcessorType.STILL, BoozeBlocks.STILL.get()),
				new ProcessCategory(helper, ProcessorType.AGING, BoozeBlocks.AGING_BARREL.get()),
				new MixCategory(helper, BoozeBlocks.BAR_COUNTER.get()));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		for (ProcessorType machine : ProcessorType.values()) {
			registration.addRecipes(ProcessCategory.typeOf(machine), ProcessCategory.recipesOf(machine));
		}
		registration.addRecipes(MixCategory.TYPE, MixCategory.recipes());

		Component drinkInfo = Component.translatable("jei.boozecraft.info.drink");
		for (Item item : BuiltInRegistries.ITEM) {
			if (item instanceof DrinkItem) {
				registration.addItemStackInfo(new ItemStack(item), drinkInfo);
			}
		}

		Component counterInfo = Component.translatable("jei.boozecraft.info.counter");
		registration.addItemStackInfo(new ItemStack(BoozeBlocks.BAR_COUNTER.get()), counterInfo);
		registration.addItemStackInfo(new ItemStack(BoozeItems.SHAKER.get()), counterInfo);

		Component machineInfo = Component.translatable("jei.boozecraft.info.machine");
		registration.addItemStackInfo(new ItemStack(BoozeBlocks.FERMENTER.get()), machineInfo);
		registration.addItemStackInfo(new ItemStack(BoozeBlocks.STILL.get()), machineInfo);
		registration.addItemStackInfo(new ItemStack(BoozeBlocks.AGING_BARREL.get()), machineInfo);
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addRecipeCatalyst(new ItemStack(BoozeBlocks.FERMENTER.get()), ProcessCategory.FERMENTING);
		registration.addRecipeCatalyst(new ItemStack(BoozeBlocks.STILL.get()), ProcessCategory.DISTILLING);
		registration.addRecipeCatalyst(new ItemStack(BoozeBlocks.AGING_BARREL.get()), ProcessCategory.AGING);
		registration.addRecipeCatalyst(new ItemStack(BoozeBlocks.BAR_COUNTER.get()), MixCategory.TYPE);
		registration.addRecipeCatalyst(new ItemStack(BoozeItems.SHAKER.get()), MixCategory.TYPE);
	}
}
'''

JEI_DIR = os.path.join(JAVA, "compat", "jei")
if not os.path.isdir(JEI_DIR):
    os.makedirs(JEI_DIR)
write(os.path.join(JEI_DIR, "ProcessCategory.java"), PROCESS_CATEGORY)
write(os.path.join(JEI_DIR, "MixCategory.java"), MIX_CATEGORY)
write(os.path.join(JEI_DIR, "BoozeJeiPlugin.java"), JEI_PLUGIN)
print("jei plugin written: 3 files")

# ---------------------------------------------------------------------------
# 6. documentation
# ---------------------------------------------------------------------------
DOC_SECTIONS = r'''## 13. Рецепты в JEI и EMI

Мод добавляет свой плагин для просмотрщиков рецептов, так что нестандартные рецепты
(аппараты и барная стойка) видно прямо в игре.

| Категория | Что показывает | Катализатор |
| --- | --- | --- |
| Бродильная бочка | брожение мешанины в пиво, сидр, вино, саке | бродильная бочка |
| Самогонный аппарат | перегонка в крепкий алкоголь | самогонный аппарат |
| Бочка для выдержки | выдержка в виски, ром и коньяк | бочка для выдержки |
| Барная стойка | смешивание коктейлей | барная стойка, шейкер |

Всего в просмотрщике: 30 рецептов аппаратов и 19 смешиваний. 45 обычных крафтов
верстака и печи JEI/EMI показывает сам — это рецепты датапака.

Как читать категорию аппарата:

* слева — ингредиенты, которые надо положить в аппарат (порядок не важен, количество — важно);
* справа — результат с количеством;
* в левом верхнем углу — время в секундах при `speedMultiplier = 1.0`;
* в категории стойки шейкер показан как катализатор — он не тратится.

Ещё плагин добавляет инфо-страницы (вкладка Info / кнопка «i»):

* на каждом напитке — как работают стадии опьянения, зависимость и чем лечиться;
* на барной стойке и шейкере — как ставить напитки и смешивать;
* на аппаратах — как загружать, где смотреть прогресс и что самогонному аппарату нужен огонь.

### EMI и другие просмотрщики

Отдельный плагин для EMI не нужен: в EMI встроен слой совместимости JEMI, который
сам читает плагины JEI.

| Что установлено | Результат |
| --- | --- |
| только JEI | работает сразу |
| EMI + JEI | работает сразу, EMI читает плагин через JEMI |
| только EMI | поставьте TooManyRecipeViewers — он транслирует плагины JEI в EMI |
| ничего из этого | мод работает как обычно, классы плагина просто не загружаются |

Плагин клиентский: серверу (в том числе Youer / Mohist / Arclight) JEI и EMI не нужны,
а игроки без просмотрщика спокойно заходят на сервер с просмотрщиком и наоборот.

### Сборка

API JEI подключается как `compileOnly` — в jar он не попадает. Версия задаётся в `gradle.properties`:

```
jei_support=true
jei_version=19.27.0.350
```

Если maven `maven.blamejared.com` недоступен или поддержка просмотрщиков не нужна:

```bash
./gradlew build -Pjei_support=false
```

В этом случае пакет `com/s4fmer/boozecraft/compat/jei` исключается из компиляции,
остальной мод собирается без изменений.

## 14. Случайные события при сильном опьянении

Раз в секунду для каждого сильно пьяного игрока бросается кубик на каждое событие
отдельно. Всё настраивается в секции `[events]` серверного конфига.

| Событие | Что происходит | Шанс/сек |
| --- | --- | --- |
| Икота | звук, брызги частиц, строка над хотбаром | 6% |
| Двоение в глазах | 6 секунд тошноты (Nausea) | 4% |
| Спотыкание | толчок в случайную сторону и 1 ед. урона | 2% |
| Разбитая посуда | стакан, кружка, рюмка или банка в руке разбивается | 2% |
| Выронил предмет | предмет из активной руки выпадает на землю | 1.2% |
| Пьяные песни | громкий звук и сообщение всем в радиусе 16 блоков | 2% |
| Проснулся не там | после вырубания телепорт в пределах 8 блоков | 35% при пробуждении |

Настройки секции `[events]`:

| Ключ | По умолчанию | Описание |
| --- | --- | --- |
| `enabled` | `true` | включает и выключает все случайные события |
| `onlyWhenHeavilyDrunk` | `true` | если `false`, события идут уже со стадии «пьян», но с 35% от шанса |
| `hiccupChance` | `0.06` | икота |
| `blurChance` | `0.04` | двоение в глазах |
| `tripChance` | `0.02` | спотыкание |
| `dropItemChance` | `0.012` | выронить предмет |
| `breakGlassChance` | `0.02` | разбить посуду |
| `singChance` | `0.02` | пьяные песни |
| `wakeUpElsewhereChance` | `0.35` | шанс проснуться не на том месте после вырубания |
| `wakeUpElsewhereRadius` | `8` | радиус такого телепорта в блоках |

Детали:

* пока игрок вырублен, события не бросаются — само вырубание описано в разделе 2;
* шанс вырубиться не изменился: 1.2%/сек, на 20–45 секунд;
* сообщения событий идут над хотбаром и отключаются вместе с `statusMessages`;
* «проснулся не там» ищет безопасную точку сам (как фрукт хоруса) и не бросит игрока в пустоту;
* вся логика серверная и на ванильных вызовах, так что работает в мультиплеере и на гибридах.

## 15. Частые вопросы
'''

patch(os.path.join(ROOT, "DOCUMENTATION.md"),
      "## 13. Частые вопросы\n",
      DOC_SECTIONS,
      "## 13. Рецепты в JEI")

README_EXTRA = r'''
## Просмотрщики рецептов

Рецепты аппаратов и коктейлей видны в JEI и EMI (EMI читает плагин JEI через встроенный JEMI;
если EMI стоит без JEI — добавьте TooManyRecipeViewers). API JEI подключается как
`compileOnly`, версия — `jei_version` в `gradle.properties`.

Сборка без просмотрщиков (если недоступен maven.blamejared.com):

```bash
./gradlew build -Pjei_support=false
```

## Генерация исходников

Java-файлы, ресурсы и датапак собираются двумя скриптами из `tools/`:

```bash
python3 tools/boozecraft_gen.py     # генерирует мод целиком
python3 tools/boozecraft_patch.py   # события опьянения и плагин JEI
```
'''

readme_path = os.path.join(ROOT, "README.md")
readme = read(readme_path)
if "Просмотрщики рецептов" not in readme:
    write(readme_path, readme.rstrip("\n") + "\n" + README_EXTRA)
print("docs patched")
print("patch done")
