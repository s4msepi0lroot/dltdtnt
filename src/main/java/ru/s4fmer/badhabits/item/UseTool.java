package ru.s4fmer.badhabits.item;

import net.minecraft.world.item.Item;
import ru.s4fmer.badhabits.registry.ModItems;

import java.util.function.Supplier;

/** Tool required to consume a substance ("method of intake"). */
public enum UseTool {
    NONE(null, "", ""),
    LIGHTER(() -> ModItems.LIGHTER.get(), "badhabits.msg.need.lighter", "badhabits.tip.need.lighter"),
    SYRINGE(() -> ModItems.SYRINGE.get(), "badhabits.msg.need.syringe", "badhabits.tip.need.syringe"),
    GLASS_TUBE(() -> ModItems.GLASS_TUBE.get(), "badhabits.msg.need.tube", "badhabits.tip.need.tube");

    private final Supplier<Item> item;
    private final String errorKey;
    private final String tipKey;

    UseTool(Supplier<Item> item, String errorKey, String tipKey) {
        this.item = item;
        this.errorKey = errorKey;
        this.tipKey = tipKey;
    }

    public Item item() {
        return item == null ? null : item.get();
    }

    public String errorKey() {
        return errorKey;
    }

    public String tipKey() {
        return tipKey;
    }
}
