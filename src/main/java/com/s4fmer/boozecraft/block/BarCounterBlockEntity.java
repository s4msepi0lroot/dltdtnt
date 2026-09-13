package com.s4fmer.boozecraft.block;

import java.util.ArrayList;
import java.util.List;

import com.s4fmer.boozecraft.recipe.MixRecipes;
import com.s4fmer.boozecraft.reg.BoozeBlockEntities;
import com.s4fmer.boozecraft.util.BoozeSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;

/** Holds three drinks on the counter top and mixes them into a cocktail. */
public class BarCounterBlockEntity extends BlockEntity {

	public static final int SLOTS = 3;

	private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

	public BarCounterBlockEntity(BlockPos pos, BlockState state) {
		super(BoozeBlockEntities.BAR_COUNTER.get(), pos, state);
	}

	public NonNullList<ItemStack> items() {
		return this.items;
	}

	public ItemStack getItem(int index) {
		return index >= 0 && index < SLOTS ? this.items.get(index) : ItemStack.EMPTY;
	}

	public boolean place(ItemStack held) {
		for (int i = 0; i < SLOTS; i++) {
			if (this.items.get(i).isEmpty()) {
				ItemStack single = held.copy();
				single.setCount(1);
				this.items.set(i, single);
				held.shrink(1);
				this.sync();
				if (this.level != null) {
					BoozeSounds.play(this.level, this.getBlockPos(), "block.glass.place", 0.5F, 1.2F);
				}
				return true;
			}
		}
		return false;
	}

	public void takeLast(Player player) {
		for (int i = SLOTS - 1; i >= 0; i--) {
			ItemStack slot = this.items.get(i);
			if (!slot.isEmpty()) {
				if (!player.getInventory().add(slot.copy())) {
					player.drop(slot.copy(), false);
				}
				this.items.set(i, ItemStack.EMPTY);
				this.sync();
				return;
			}
		}
		player.displayClientMessage(Component.translatable("msg.boozecraft.counter_empty"), true);
	}

	/** Mix everything standing on the counter. */
	public void mix(Player player) {
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < SLOTS; i++) {
			ItemStack slot = this.items.get(i);
			if (!slot.isEmpty()) {
				ids.add(BuiltInRegistries.ITEM.getKey(slot.getItem()).toString());
			}
		}
		if (ids.size() < 2) {
			player.displayClientMessage(Component.translatable("msg.boozecraft.need_more"), true);
			return;
		}
		MixRecipes.Entry entry = MixRecipes.find(ids);
		if (entry == null) {
			player.displayClientMessage(Component.translatable("msg.boozecraft.no_recipe"), true);
			return;
		}
		Item result = MixRecipes.item(entry.result);
		if (result == null) {
			return;
		}
		for (int i = 0; i < SLOTS; i++) {
			this.items.set(i, ItemStack.EMPTY);
		}
		this.items.set(0, new ItemStack(result, Math.max(1, entry.count)));
		this.sync();
		if (this.level != null) {
			BoozeSounds.play(this.level, this.getBlockPos(), "block.brewing_stand.brew", 0.8F, 1.4F);
		}
		player.displayClientMessage(Component.translatable("msg.boozecraft.mixed",
				new ItemStack(result).getHoverName()), true);
	}

	private void sync() {
		this.setChanged();
		Level level = this.getLevel();
		if (level != null && !level.isClientSide) {
			BlockState state = this.getBlockState();
			level.sendBlockUpdated(this.getBlockPos(), state, state, Block.UPDATE_ALL);
		}
	}

	// -------------------------------------------------------------------- nbt

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, this.items, registries);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		this.items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(tag, this.items, registries);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveWithoutMetadata(registries);
	}

	@Nullable
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
