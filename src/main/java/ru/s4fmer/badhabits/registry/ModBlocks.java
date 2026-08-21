package ru.s4fmer.badhabits.registry;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.s4fmer.badhabits.BadHabits;
import ru.s4fmer.badhabits.block.LabBlock;

/** Blocks of the mod. */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BadHabits.MODID);

    public static final DeferredBlock<LabBlock> SYNTH_LAB = BLOCKS.register("synth_lab",
            () -> new LabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 7)));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
