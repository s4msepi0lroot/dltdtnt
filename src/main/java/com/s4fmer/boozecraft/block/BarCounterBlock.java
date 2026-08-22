package com.s4fmer.boozecraft.block;

import com.s4fmer.boozecraft.drink.DrinkItem;
import com.s4fmer.boozecraft.item.ShakerItem;
import com.s4fmer.boozecraft.reg.BoozeItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

/** Put up to three drinks or glasses on top, then use the shaker to mix them. */
public class BarCounterBlock extends Block implements EntityBlock {

	public BarCounterBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BarCounterBlockEntity(pos, state);
	}

	public static boolean canPlace(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.getItem() instanceof DrinkItem) {
			return true;
		}
		return stack.is(BoozeItems.GLASS_CUP.get())
				|| stack.is(BoozeItems.MUG.get())
				|| stack.is(BoozeItems.SHOT_GLASS.get())
				|| stack.is(BoozeItems.EMPTY_CAN.get())
				|| stack.is(Items.GLASS_BOTTLE)
				|| stack.is(Items.POTION);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide) {
			return ItemInteractionResult.SUCCESS;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof BarCounterBlockEntity counter)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}

		if (stack.getItem() instanceof ShakerItem) {
			counter.mix(player);
			return ItemInteractionResult.SUCCESS;
		}
		if (canPlace(stack)) {
			if (counter.place(stack)) {
				return ItemInteractionResult.SUCCESS;
			}
			player.displayClientMessage(Component.translatable("msg.boozecraft.counter_full"), true);
			return ItemInteractionResult.CONSUME;
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof BarCounterBlockEntity counter)) {
			return InteractionResult.PASS;
		}
		counter.takeLast(player);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof BarCounterBlockEntity counter) {
				Containers.dropContents(level, pos, counter.items());
			}
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
