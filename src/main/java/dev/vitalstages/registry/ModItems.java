package dev.vitalstages.registry;
import dev.vitalstages.VitalStages;
import dev.vitalstages.item.*;
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
    public static final DeferredItem<AntisepticItem> ANTISEPTIC = ITEMS.register("antiseptic",
            () -> new AntisepticItem(new Item.Properties().stacksTo(8)));
    public static final DeferredItem<PainkillerItem> PAINKILLER = ITEMS.register("painkiller",
            () -> new PainkillerItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<AdrenalineItem> ADRENALINE = ITEMS.register("adrenaline",
            () -> new AdrenalineItem(new Item.Properties().stacksTo(8)));
    public static final DeferredItem<BloodBagItem> BLOOD_BAG = ITEMS.register("blood_bag",
            () -> new BloodBagItem(new Item.Properties().stacksTo(8)));
    public static final DeferredItem<EmptyBloodBagItem> EMPTY_BLOOD_BAG = ITEMS.register("empty_blood_bag",
            () -> new EmptyBloodBagItem(new Item.Properties().stacksTo(16)));
    private ModItems() {}
}
