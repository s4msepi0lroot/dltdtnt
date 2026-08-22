package com.s4fmer.boozecraft.block;

import com.s4fmer.boozecraft.util.BoozeSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

/** Fermenter / still / aging barrel. No GUI on purpose - right click driven, hybrid safe. */
public class ProcessorBlock extends Block implements EntityBlock {

	private final ProcessorType type;

	public ProcessorBlock(ProcessorType type, Properties properties) {
		super(properties);
		this.type = type;
	}

	public ProcessorType type() {
		return this.type;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ProcessorBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		if (level.isClientSide) {
			return null;
		}
		return (tickLevel, pos, tickState, blockEntity) -> {
			if (blockEntity instanceof ProcessorBlockEntity processor) {
				processor.serverTick();
			}
		};
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide) {
			return ItemInteractionResult.SUCCESS;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof ProcessorBlockEntity processor)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (stack.isEmpty()) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (processor.insert(stack)) {
			BoozeSounds.play(level, pos, "item.bucket.fill", 0.5F, 1.2F);
			processor.report(player);
			return ItemInteractionResult.SUCCESS;
		}
		player.displayClientMessage(Component.translatable("msg.boozecraft.machine_full"), true);
		return ItemInteractionResult.CONSUME;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof ProcessorBlockEntity processor)) {
			return InteractionResult.PASS;
		}
		if (processor.takeOut(player)) {
			BoozeSounds.play(level, pos, "entity.item.pickup", 0.6F, 1.0F);
			return InteractionResult.SUCCESS;
		}
		processor.report(player);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof ProcessorBlockEntity processor) {
				Containers.dropContents(level, pos, processor.items());
			}
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
