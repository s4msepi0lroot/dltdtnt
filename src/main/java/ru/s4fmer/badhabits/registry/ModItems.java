package ru.s4fmer.badhabits.registry;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.s4fmer.badhabits.BadHabits;
import ru.s4fmer.badhabits.addiction.Substance;
import ru.s4fmer.badhabits.item.DetoxItem;
import ru.s4fmer.badhabits.item.EffectSpec;
import ru.s4fmer.badhabits.item.LighterItem;
import ru.s4fmer.badhabits.item.SubstanceItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * All items of the mod. The declaration order is also the creative tab order.
 *
 * <p>Numbers: dose = how much substance enters the body, gain = how much addiction one use adds,
 * ticks = length of the use animation, toolDmg = durability spent from the required tool.</p>
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BadHabits.MODID);

    private static final List<DeferredItem<? extends Item>> ORDER = new ArrayList<>();

    // ------------------------------------------------------------- utility

    public static final DeferredItem<LighterItem> LIGHTER =
            add("lighter", () -> new LighterItem(new Item.Properties().durability(100)));

    public static final DeferredItem<Item> SYRINGE =
            add("syringe", () -> new Item(new Item.Properties().durability(32)));

    public static final DeferredItem<Item> GLASS_TUBE =
            add("glass_tube", () -> new Item(new Item.Properties().durability(16)));

    public static final DeferredItem<Item> VIAL =
            add("vial", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> TOBACCO_LEAF =
            add("tobacco_leaf", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> ROLLING_PAPER =
            add("rolling_paper", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> CHARCOAL_FILTER =
            add("charcoal_filter", () -> new Item(new Item.Properties()));

    // ------------------------------------------------------------ synthesis

    public static final DeferredItem<Item> CHEM_REAGENT =
            add("chem_reagent", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> RAW_BATCH =
            add("raw_batch", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> CRYSTAL_BASE =
            add("crystal_base", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    // ----------------------------------------------------------- cigarettes

    /** Tier 0: the smallest dose in the mod - the tool for tapering nicotine down. */
    public static final DeferredItem<SubstanceItem> CIG_SLIM = add("cig_slim", () -> new SubstanceItem(
            new Item.Properties().stacksTo(16),
            Substance.NICOTINE, SubstanceItem.Style.SMOKE,
            4.0F, 1.0F, 28, 1,
            Collections.emptyList(), true, false));

    /** Tier 1: cheapest, gives nothing at all. */
    public static final DeferredItem<SubstanceItem> CIG_ROLLUP = add("cig_rollup", () -> new SubstanceItem(
            new Item.Properties().stacksTo(16),
            Substance.NICOTINE, SubstanceItem.Style.SMOKE,
            6.0F, 1.5F, 32, 1,
            Collections.emptyList(), true, false));

    /** Tier 2 */
    public static final DeferredItem<SubstanceItem> CIG_CLASSIC = add("cig_classic", () -> new SubstanceItem(
            new Item.Properties().stacksTo(16),
            Substance.NICOTINE, SubstanceItem.Style.SMOKE,
            10.0F, 2.5F, 32, 1,
            List.of(EffectSpec.of(MobEffects.MOVEMENT_SPEED, 20)), true, false));

    /** Tier 3 */
    public static final DeferredItem<SubstanceItem> CIG_MENTHOL = add("cig_menthol", () -> new SubstanceItem(
            new Item.Properties().stacksTo(16),
            Substance.NICOTINE, SubstanceItem.Style.SMOKE,
            12.0F, 3.0F, 32, 1,
            List.of(EffectSpec.of(MobEffects.NIGHT_VISION, 60),
                    EffectSpec.of(MobEffects.WATER_BREATHING, 30)), true, false));

    /** Tier 3.5 */
    public static final DeferredItem<SubstanceItem> CIG_CLOVE = add("cig_clove", () -> new SubstanceItem(
            new Item.Properties().stacksTo(16),
            Substance.NICOTINE, SubstanceItem.Style.SMOKE,
            14.0F, 3.5F, 36, 1,
            List.of(EffectSpec.of(MobEffects.REGENERATION, 12, 0),
                    EffectSpec.of(MobEffects.HEALTH_BOOST, 60, 0)), true, false));

    /** Tier 4 */
    public static final DeferredItem<SubstanceItem> CIG_CIGAR = add("cig_cigar", () -> new SubstanceItem(
            new Item.Properties().stacksTo(8),
            Substance.NICOTINE, SubstanceItem.Style.SMOKE,
            16.0F, 4.0F, 48, 2,
            List.of(EffectSpec.of(MobEffects.DAMAGE_RESISTANCE, 40),
                    EffectSpec.of(MobEffects.FIRE_RESISTANCE, 30),
                    EffectSpec.of(MobEffects.ABSORPTION, 60)), true, false));

    /** Tier 4.5: strong, but the tar bites back. */
    public static final DeferredItem<SubstanceItem> CIG_BLACK = add("cig_black", () -> new SubstanceItem(
            new Item.Properties().stacksTo(8),
            Substance.NICOTINE, SubstanceItem.Style.SMOKE,
            18.0F, 4.5F, 44, 2,
            List.of(EffectSpec.of(MobEffects.DAMAGE_BOOST, 30, 1),
                    EffectSpec.of(MobEffects.DAMAGE_RESISTANCE, 30, 0),
                    EffectSpec.of(MobEffects.CONFUSION, 6, 0)), true, false));

    /** Tier 5: the best one. */
    public static final DeferredItem<SubstanceItem> CIG_GOLD = add("cig_gold", () -> new SubstanceItem(
            new Item.Properties().stacksTo(8).rarity(Rarity.RARE),
            Substance.NICOTINE, SubstanceItem.Style.SMOKE,
            20.0F, 5.0F, 40, 2,
            List.of(EffectSpec.of(MobEffects.DIG_SPEED, 60, 1),
                    EffectSpec.of(MobEffects.DAMAGE_BOOST, 30, 0),
                    EffectSpec.of(MobEffects.REGENERATION, 8, 0),
                    EffectSpec.of(MobEffects.LUCK, 60, 0)), true, false));

    // ------------------------------------------------- fictional synthetics

    /** Calm variant, swallowed, no tool required. */
    public static final DeferredItem<SubstanceItem> PILL_CALMEX = add("pill_calmex", () -> new SubstanceItem(
            new Item.Properties().stacksTo(16),
            Substance.NARCOTIC, SubstanceItem.Style.SWALLOW,
            10.0F, 2.5F, 24, 0,
            List.of(EffectSpec.of(MobEffects.DAMAGE_RESISTANCE, 60, 0),
                    EffectSpec.of(MobEffects.HEALTH_BOOST, 60, 0),
                    EffectSpec.of(MobEffects.MOVEMENT_SLOWDOWN, 20, 0)), false, false));

    /** Swallowed, no tool required. */
    public static final DeferredItem<SubstanceItem> PILL_EUPHORIN = add("pill_euphorin", () -> new SubstanceItem(
            new Item.Properties().stacksTo(16),
            Substance.NARCOTIC, SubstanceItem.Style.SWALLOW,
            20.0F, 6.0F, 24, 0,
            List.of(EffectSpec.of(MobEffects.MOVEMENT_SPEED, 40, 1),
                    EffectSpec.of(MobEffects.JUMP, 40, 1),
                    EffectSpec.of(MobEffects.REGENERATION, 10, 0),
                    EffectSpec.of(MobEffects.CONFUSION, 5, 0)), false, false));

    /** Half a pill - the tapering tool. */
    public static final DeferredItem<SubstanceItem> PILL_EUPHORIN_HALF = add("pill_euphorin_half", () -> new SubstanceItem(
            new Item.Properties().stacksTo(16),
            Substance.NARCOTIC, SubstanceItem.Style.SWALLOW,
            8.0F, 2.0F, 24, 0,
            List.of(EffectSpec.of(MobEffects.MOVEMENT_SPEED, 20, 0)), false, false));

    /** Diluted serum: the tapering tool for the synthetic line. */
    public static final DeferredItem<SubstanceItem> SERUM_MICRODOSE = add("serum_microdose", () -> new SubstanceItem(
            new Item.Properties().stacksTo(16),
            Substance.NARCOTIC, SubstanceItem.Style.INJECT,
            5.0F, 1.0F, 24, 1,
            List.of(EffectSpec.of(MobEffects.MOVEMENT_SPEED, 10, 0)), false, false));

    /** Injected with a syringe. */
    public static final DeferredItem<SubstanceItem> SERUM_NEUROLITE = add("serum_neurolite", () -> new SubstanceItem(
            new Item.Properties().stacksTo(8).rarity(Rarity.UNCOMMON),
            Substance.NARCOTIC, SubstanceItem.Style.INJECT,
            35.0F, 10.0F, 30, 1,
            List.of(EffectSpec.of(MobEffects.DAMAGE_BOOST, 45, 1),
                    EffectSpec.of(MobEffects.DAMAGE_RESISTANCE, 30, 1),
                    EffectSpec.of(MobEffects.DIG_SPEED, 45, 1),
                    EffectSpec.of(MobEffects.FIRE_RESISTANCE, 20, 0),
                    EffectSpec.of(MobEffects.CONFUSION, 6, 0)), false, false));

    /** Inhaled through a glass tube. */
    public static final DeferredItem<SubstanceItem> VOID_DUST = add("void_dust", () -> new SubstanceItem(
            new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON),
            Substance.NARCOTIC, SubstanceItem.Style.INHALE,
            28.0F, 8.0F, 28, 1,
            List.of(EffectSpec.of(MobEffects.NIGHT_VISION, 60, 0),
                    EffectSpec.of(MobEffects.MOVEMENT_SPEED, 30, 1),
                    EffectSpec.of(MobEffects.SLOW_FALLING, 30, 0),
                    EffectSpec.of(MobEffects.CONFUSION, 10, 0)), false, true));

    /** Smoked, needs the lighter. */
    public static final DeferredItem<SubstanceItem> DREAM_JOINT = add("dream_joint", () -> new SubstanceItem(
            new Item.Properties().stacksTo(16),
            Substance.NARCOTIC, SubstanceItem.Style.SMOKE,
            22.0F, 5.0F, 40, 1,
            List.of(EffectSpec.of(MobEffects.SLOW_FALLING, 40, 0),
                    EffectSpec.of(MobEffects.REGENERATION, 10, 0),
                    EffectSpec.of(MobEffects.NIGHT_VISION, 30, 0),
                    EffectSpec.of(MobEffects.CONFUSION, 8, 0)), true, false));

    /** Inhaled through a glass tube, water themed. */
    public static final DeferredItem<SubstanceItem> INHALER_OZON = add("inhaler_ozon", () -> new SubstanceItem(
            new Item.Properties().stacksTo(8),
            Substance.NARCOTIC, SubstanceItem.Style.INHALE,
            24.0F, 7.0F, 28, 1,
            List.of(EffectSpec.of(MobEffects.DOLPHINS_GRACE, 60, 0),
                    EffectSpec.of(MobEffects.WATER_BREATHING, 60, 0),
                    EffectSpec.of(MobEffects.CONDUIT_POWER, 30, 0),
                    EffectSpec.of(MobEffects.CONFUSION, 5, 0)), false, false));

    /** End game injection: huge buffs, huge dose, very close to an overdose. */
    public static final DeferredItem<SubstanceItem> ELIXIR_TITAN = add("elixir_titan", () -> new SubstanceItem(
            new Item.Properties().stacksTo(4).rarity(Rarity.RARE),
            Substance.NARCOTIC, SubstanceItem.Style.INJECT,
            45.0F, 12.0F, 40, 2,
            List.of(EffectSpec.of(MobEffects.HEALTH_BOOST, 90, 2),
                    EffectSpec.of(MobEffects.ABSORPTION, 90, 1),
                    EffectSpec.of(MobEffects.DAMAGE_BOOST, 45, 1),
                    EffectSpec.of(MobEffects.DAMAGE_RESISTANCE, 30, 1),
                    EffectSpec.of(MobEffects.HUNGER, 30, 0),
                    EffectSpec.of(MobEffects.CONFUSION, 8, 0)), false, false));

    // ------------------------------------------------------------ treatment

    public static final DeferredItem<DetoxItem> DETOX_TONIC =
            add("detox_tonic", () -> new DetoxItem(new Item.Properties().stacksTo(8)));

    // --------------------------------------------------------------- block

    public static final DeferredItem<BlockItem> SYNTH_LAB =
            addBlock("synth_lab", ModBlocks.SYNTH_LAB);

    // ---------------------------------------------------------------- infra

    private ModItems() {
    }

    private static DeferredItem<BlockItem> addBlock(String name, Supplier<? extends Block> block) {
        DeferredItem<BlockItem> item = ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        ORDER.add(item);
        return item;
    }

    private static <I extends Item> DeferredItem<I> add(String name, Supplier<I> supplier) {
        DeferredItem<I> item = ITEMS.register(name, supplier);
        ORDER.add(item);
        return item;
    }

    public static List<DeferredItem<? extends Item>> ordered() {
        return Collections.unmodifiableList(ORDER);
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
