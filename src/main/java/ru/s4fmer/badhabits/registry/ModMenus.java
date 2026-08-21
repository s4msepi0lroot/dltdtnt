package ru.s4fmer.badhabits.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.s4fmer.badhabits.BadHabits;
import ru.s4fmer.badhabits.menu.LabMenu;

/** Menu (container) types. */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, BadHabits.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<LabMenu>> SYNTH_LAB =
            MENUS.register("synth_lab",
                    () -> IMenuTypeExtension.create((containerId, inventory, buffer) -> new LabMenu(containerId, inventory)));

    private ModMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
