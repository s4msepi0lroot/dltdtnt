package com.s4fmer.boozecraft.reg;

import com.s4fmer.boozecraft.BoozeCraft;
import com.s4fmer.boozecraft.block.BarCounterBlockEntity;
import com.s4fmer.boozecraft.block.ProcessorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BoozeBlockEntities {

	public static final DeferredRegister<BlockEntityType<?>> TYPES =
			DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BoozeCraft.MODID);

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessorBlockEntity>> PROCESSOR =
			TYPES.register("processor", () -> BlockEntityType.Builder.of(
					ProcessorBlockEntity::new,
					BoozeBlocks.FERMENTER.get(),
					BoozeBlocks.STILL.get(),
					BoozeBlocks.AGING_BARREL.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BarCounterBlockEntity>> BAR_COUNTER =
			TYPES.register("bar_counter", () -> BlockEntityType.Builder.of(
					BarCounterBlockEntity::new,
					BoozeBlocks.BAR_COUNTER.get()).build(null));

	private BoozeBlockEntities() {
	}
}
