package ru.s4fmer.badhabits.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.s4fmer.badhabits.menu.LabMenu;
import ru.s4fmer.badhabits.registry.ModBlockEntities;

/**
 * Synthesis Lab block entity: two input slots, one output slot, a progress bar.
 *
 * <p>Implements the vanilla {@link Container} interface instead of a capability item handler, so hoppers,
 * chest-like automation and hybrid-server plugins can all see the inventory without extra glue code.</p>
 */
public class LabBlockEntity extends BlockEntity implements Container, MenuProvider {

    public static final int SLOT_INPUT_A = 0;
    public static final int SLOT_INPUT_B = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SIZE = 3;
    public static final int DATA_COUNT = 2;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int progress;
    private int maxProgress = 200;

    /** Progress is synced to the open screen through the menu, no block entity packets needed. */
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? LabBlockEntity.this.progress : LabBlockEntity.this.maxProgress;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                LabBlockEntity.this.progress = value;
            } else {
                LabBlockEntity.this.maxProgress = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public LabBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SYNTH_LAB.get(), pos, state);
    }

    // ----------------------------------------------------------------- tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, LabBlockEntity blockEntity) {
        blockEntity.tick(level, pos);
    }

    private void tick(Level level, BlockPos pos) {
        LabRecipes.Entry entry = LabRecipes.find(items.get(SLOT_INPUT_A), items.get(SLOT_INPUT_B));
        if (entry == null || !canOutput(entry)) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            return;
        }

        maxProgress = Math.max(1, entry.ticks());
        progress++;

        if (level instanceof ServerLevel serverLevel && progress % 20 == 0) {
            serverLevel.sendParticles(ParticleTypes.BUBBLE_POP,
                    pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D,
                    3, 0.2D, 0.02D, 0.2D, 0.01D);
        }

        if (progress >= maxProgress) {
            craft(entry);
            progress = 0;
            level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        setChanged();
    }

    private boolean canOutput(LabRecipes.Entry entry) {
        ItemStack result = entry.resultStack();
        ItemStack current = items.get(SLOT_OUTPUT);
        if (current.isEmpty()) {
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(current, result)) {
            return false;
        }
        return current.getCount() + result.getCount() <= current.getMaxStackSize();
    }

    private void craft(LabRecipes.Entry entry) {
        ItemStack result = entry.resultStack();
        items.get(SLOT_INPUT_A).shrink(1);
        items.get(SLOT_INPUT_B).shrink(1);

        ItemStack current = items.get(SLOT_OUTPUT);
        if (current.isEmpty()) {
            items.set(SLOT_OUTPUT, result);
        } else {
            current.grow(result.getCount());
        }
    }

    public int progress() {
        return progress;
    }

    public int maxProgress() {
        return maxProgress;
    }

    // ------------------------------------------------------------ container

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, index, count);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return ContainerHelper.takeItem(items, index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        items.set(index, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return index != SLOT_OUTPUT;
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    // ----------------------------------------------------------------- menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.badhabits.lab");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new LabMenu(containerId, inventory, this, dataAccess);
    }

    // ------------------------------------------------------------------ nbt

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        progress = Math.max(0, tag.getInt("Progress"));
        maxProgress = Math.max(1, tag.getInt("MaxProgress"));
    }
}
