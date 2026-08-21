package ru.s4fmer.badhabits.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.s4fmer.badhabits.BadHabits;
import ru.s4fmer.badhabits.block.LabBlockEntity;

/** Block entity types. */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BadHabits.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LabBlockEntity>> SYNTH_LAB =
            BLOCK_ENTITIES.register("synth_lab",
                    () -> BlockEntityType.Builder.of(LabBlockEntity::new, ModBlocks.SYNTH_LAB.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
