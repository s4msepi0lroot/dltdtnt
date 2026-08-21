package ru.s4fmer.badhabits.block;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.s4fmer.badhabits.registry.ModItems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Recipes of the Synthesis Lab block. Two inputs in any order, one output, a fixed processing time.
 *
 * <p>Kept as plain Java instead of JSON recipes on purpose: no recipe type / serializer registration and
 * no datapack reload logic, which means fewer moving parts on hybrid servers. The lab is the "bulk"
 * path: worse gating (needs the machine, takes time) but a much better ratio than the crafting table.</p>
 */
public final class LabRecipes {

    /** first + second -> output x count, taking ticks game ticks (20 ticks = 1 second). */
    public record Entry(Supplier<? extends Item> first,
                        Supplier<? extends Item> second,
                        Supplier<? extends Item> output,
                        int count,
                        int ticks) {

        public boolean matches(ItemStack a, ItemStack b) {
            Item x = first.get();
            Item y = second.get();
            return (a.is(x) && b.is(y)) || (a.is(y) && b.is(x));
        }

        public ItemStack resultStack() {
            return new ItemStack(output.get(), count);
        }
    }

    private static final List<Entry> ENTRIES;

    static {
        List<Entry> list = new ArrayList<>();

        // ---- tobacco line -------------------------------------------------
        list.add(new Entry(ModItems.TOBACCO_LEAF, ModItems.ROLLING_PAPER, ModItems.CIG_CLASSIC, 3, 200));
        list.add(new Entry(ModItems.TOBACCO_LEAF, ModItems.CHARCOAL_FILTER, ModItems.CIG_SLIM, 3, 150));

        // ---- fictional synthesis line --------------------------------------
        list.add(new Entry(ModItems.CHEM_REAGENT, () -> Items.CHARCOAL, ModItems.RAW_BATCH, 2, 200));
        list.add(new Entry(ModItems.RAW_BATCH, ModItems.CHEM_REAGENT, ModItems.CRYSTAL_BASE, 2, 300));
        list.add(new Entry(ModItems.CRYSTAL_BASE, () -> Items.SUGAR, ModItems.PILL_EUPHORIN, 4, 240));
        list.add(new Entry(ModItems.CRYSTAL_BASE, () -> Items.HONEYCOMB, ModItems.PILL_CALMEX, 5, 240));
        list.add(new Entry(ModItems.CRYSTAL_BASE, ModItems.VIAL, ModItems.SERUM_NEUROLITE, 2, 300));
        list.add(new Entry(ModItems.CRYSTAL_BASE, () -> Items.ENDER_PEARL, ModItems.VOID_DUST, 4, 260));
        list.add(new Entry(ModItems.CRYSTAL_BASE, () -> Items.PRISMARINE_SHARD, ModItems.INHALER_OZON, 3, 260));

        // ---- tapering + treatment ------------------------------------------
        list.add(new Entry(ModItems.PILL_EUPHORIN, () -> Items.SUGAR, ModItems.PILL_EUPHORIN_HALF, 3, 150));
        list.add(new Entry(ModItems.SERUM_NEUROLITE, ModItems.VIAL, ModItems.SERUM_MICRODOSE, 4, 150));
        list.add(new Entry(ModItems.VIAL, () -> Items.HONEY_BOTTLE, ModItems.DETOX_TONIC, 2, 200));

        ENTRIES = Collections.unmodifiableList(list);
    }

    private LabRecipes() {
    }

    public static List<Entry> all() {
        return ENTRIES;
    }

    /** Returns the matching recipe or null. */
    public static Entry find(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return null;
        }
        for (Entry entry : ENTRIES) {
            if (entry.matches(a, b)) {
                return entry;
            }
        }
        return null;
    }
}
