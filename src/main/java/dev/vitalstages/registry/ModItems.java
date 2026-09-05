package dev.vitalstages.registry;
import dev.vitalstages.VitalStages;
import dev.vitalstages.item.BandageItem;
import dev.vitalstages.item.SplintItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VitalStages.MOD_ID);
    public static final DeferredItem<BandageItem> BANDAGE = ITEMS.register("bandage",
            () -> new BandageItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<SplintItem> SPLINT = ITEMS.register("splint",
            () -> new SplintItem(new Item.Properties().stacksTo(8)));
    private ModItems() {}
}
