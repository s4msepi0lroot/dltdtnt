package com.s4fmer.boozecraft.reg;

import com.s4fmer.boozecraft.BoozeCraft;
import com.s4fmer.boozecraft.block.BarCounterBlock;
import com.s4fmer.boozecraft.block.ProcessorBlock;
import com.s4fmer.boozecraft.block.ProcessorType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BoozeBlocks {

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BoozeCraft.MODID);

	public static final DeferredBlock<Block> FERMENTER = BLOCKS.register("fermenter",
			() -> new ProcessorBlock(ProcessorType.FERMENTER,
					BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD)));

	public static final DeferredBlock<Block> STILL = BLOCKS.register("still",
			() -> new ProcessorBlock(ProcessorType.STILL,
					BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.COPPER)));

	public static final DeferredBlock<Block> AGING_BARREL = BLOCKS.register("aging_barrel",
			() -> new ProcessorBlock(ProcessorType.AGING,
					BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD)));

	public static final DeferredBlock<Block> BAR_COUNTER = BLOCKS.register("bar_counter",
			() -> new BarCounterBlock(BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD)));

	private BoozeBlocks() {
	}
}
