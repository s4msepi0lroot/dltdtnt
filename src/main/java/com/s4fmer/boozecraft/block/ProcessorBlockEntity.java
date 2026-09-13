package com.s4fmer.boozecraft.block;

import java.util.HashMap;
import java.util.Map;

import com.s4fmer.boozecraft.BoozeConfig;
import com.s4fmer.boozecraft.recipe.ProcessRecipes;
import com.s4fmer.boozecraft.reg.BoozeBlockEntities;
import com.s4fmer.boozecraft.util.BoozeSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Slots 0-3 are inputs, slot 4 is the output. Everything is right click driven,
 * so no custom menu has to be synced - that is what keeps Bukkit hybrids happy.
 */
public class ProcessorBlockEntity extends BlockEntity {

	public static final int INPUT_SLOTS = 4;
	public static final int OUTPUT_SLOT = 4;
	public static final int MAX_PER_SLOT = 16;

	private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
	private int progress;
	private int total;
	private String resultId = "";
	private int resultCount;

	public ProcessorBlockEntity(BlockPos pos, BlockState state) {
		super(BoozeBlockEntities.PROCESSOR.get(), pos, state);
	}

	public NonNullList<ItemStack> items() {
		return this.items;
	}

	private ProcessorType type() {
		if (this.getBlockState().getBlock() instanceof ProcessorBlock block) {
			return block.type();
		}
		return ProcessorType.FERMENTER;
	}

	// ------------------------------------------------------------- interaction

	public boolean insert(ItemStack held) {
		for (int i = 0; i < INPUT_SLOTS; i++) {
			ItemStack slot = this.items.get(i);
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, held) && slot.getCount() < MAX_PER_SLOT) {
				slot.grow(1);
				held.shrink(1);
				this.setChanged();
				return true;
			}
		}
		for (int i = 0; i < INPUT_SLOTS; i++) {
			if (this.items.get(i).isEmpty()) {
				ItemStack single = held.copy();
				single.setCount(1);
				this.items.set(i, single);
				held.shrink(1);
				this.setChanged();
				return true;
			}
		}
		return false;
	}

	/** Takes the output first, then the newest input back. */
	public boolean takeOut(Player player) {
		ItemStack output = this.items.get(OUTPUT_SLOT);
		if (!output.isEmpty()) {
			if (!player.getInventory().add(output.copy())) {
				player.drop(output.copy(), false);
			}
			this.items.set(OUTPUT_SLOT, ItemStack.EMPTY);
			this.setChanged();
			return true;
		}
		for (int i = INPUT_SLOTS - 1; i >= 0; i--) {
			ItemStack slot = this.items.get(i);
			if (!slot.isEmpty()) {
				if (!player.getInventory().add(slot.copy())) {
					player.drop(slot.copy(), false);
				}
				this.items.set(i, ItemStack.EMPTY);
				this.progress = 0;
				this.resultId = "";
				this.setChanged();
				return true;
			}
		}
		return false;
	}

	public void report(Player player) {
		if (this.total > 0 && !this.resultId.isEmpty()) {
			int percent = (int) Math.floor(this.progress * 100.0D / this.total);
			player.displayClientMessage(Component.translatable("msg.boozecraft.progress",
					Component.translatable(this.type().translationKey()), percent), true);
		} else if (this.type() == ProcessorType.STILL && BoozeConfig.STILL_NEEDS_HEAT.get() && !this.hasHeat()) {
			player.displayClientMessage(Component.translatable("msg.boozecraft.no_heat"), true);
		} else {
			player.displayClientMessage(Component.translatable("msg.boozecraft.idle",
					Component.translatable(this.type().translationKey())), true);
		}
	}

	// -------------------------------------------------------------------- logic

	public void serverTick() {
		Level level = this.getLevel();
		if (level == null || level.isClientSide) {
			return;
		}
		if (this.type() == ProcessorType.STILL && BoozeConfig.STILL_NEEDS_HEAT.get() && !this.hasHeat()) {
			return;
		}

		if (this.resultId.isEmpty()) {
			if (level.getGameTime() % 20L != 0L) {
				return;
			}
			ProcessRecipes.Entry match = ProcessRecipes.find(this.type(), this.items, INPUT_SLOTS);
			if (match == null) {
				return;
			}
			ItemStack output = this.items.get(OUTPUT_SLOT);
			Item resultItem = ProcessRecipes.item(match.result);
			if (resultItem == null) {
				return;
			}
			if (!output.isEmpty() && (output.getItem() != resultItem
					|| output.getCount() + match.count > output.getMaxStackSize())) {
				return;
			}
			this.resultId = match.result;
			this.resultCount = match.count;
			this.total = Math.max(20, (int) (match.time / BoozeConfig.PROCESS_SPEED_MULTIPLIER.get()));
			this.progress = 0;
			ProcessRecipes.consume(match, this.items, INPUT_SLOTS);
			this.setChanged();
			return;
		}

		this.progress++;
		if (this.progress % 60 == 0) {
			this.bubble(level);
		}
		if (this.progress < this.total) {
			return;
		}

		Item resultItem = ProcessRecipes.item(this.resultId);
		if (resultItem != null) {
			ItemStack output = this.items.get(OUTPUT_SLOT);
			if (output.isEmpty()) {
				this.items.set(OUTPUT_SLOT, new ItemStack(resultItem, Math.max(1, this.resultCount)));
			} else {
				output.grow(Math.max(1, this.resultCount));
			}
		}
		this.progress = 0;
		this.total = 0;
		this.resultId = "";
		this.resultCount = 0;
		BoozeSounds.play(level, this.getBlockPos(), "block.brewing_stand.brew", 0.7F, 1.0F);
		this.setChanged();
	}

	private void bubble(Level level) {
		BoozeSounds.play(level, this.getBlockPos(), "block.water.ambient", 0.15F, 1.6F);
	}

	private boolean hasHeat() {
		Level level = this.getLevel();
		if (level == null) {
			return false;
		}
		BlockState below = level.getBlockState(this.getBlockPos().below());
		if (below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE) || below.is(Blocks.LAVA)
				|| below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.LAVA_CAULDRON)) {
			return true;
		}
		return below.hasProperty(BlockStateProperties.LIT) && below.getValue(BlockStateProperties.LIT);
	}

	// -------------------------------------------------------------------- nbt

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, this.items, registries);
		tag.putInt("Progress", this.progress);
		tag.putInt("Total", this.total);
		tag.putString("Result", this.resultId);
		tag.putInt("ResultCount", this.resultCount);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		this.items = NonNullList.withSize(5, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(tag, this.items, registries);
		this.progress = tag.getInt("Progress");
		this.total = tag.getInt("Total");
		this.resultId = tag.getString("Result");
		this.resultCount = tag.getInt("ResultCount");
	}

	/** Debug helper used by the docs: how many different item types are inside. */
	public Map<Item, Integer> contents() {
		Map<Item, Integer> map = new HashMap<>();
		for (int i = 0; i < INPUT_SLOTS; i++) {
			ItemStack slot = this.items.get(i);
			if (!slot.isEmpty()) {
				map.merge(slot.getItem(), slot.getCount(), Integer::sum);
			}
		}
		return map;
	}
}
