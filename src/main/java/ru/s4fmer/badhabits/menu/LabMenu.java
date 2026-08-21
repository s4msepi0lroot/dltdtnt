package ru.s4fmer.badhabits.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.s4fmer.badhabits.registry.ModMenus;

/** Menu of the Synthesis Lab: 2 inputs + 1 output + the player inventory. */
public class LabMenu extends AbstractContainerMenu {

    public static final int CONTAINER_SLOTS = 3;
    public static final int DATA_COUNT = 2;
    public static final int ARROW_WIDTH = 24;

    private final Container container;
    private final ContainerData data;

    /** Client side constructor (used by the MenuType factory). */
    public LabMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SLOTS), new SimpleContainerData(DATA_COUNT));
    }

    /** Server side constructor. */
    public LabMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.SYNTH_LAB.get(), containerId);
        checkContainerSize(container, CONTAINER_SLOTS);
        checkContainerDataCount(data, DATA_COUNT);
        this.container = container;
        this.data = data;
        container.startOpen(playerInventory.player);

        this.addSlot(new Slot(container, 0, 44, 17));
        this.addSlot(new Slot(container, 1, 44, 53));
        this.addSlot(new Slot(container, 2, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        this.addDataSlots(data);
    }

    /** Width in pixels of the progress arrow. */
    public int progressPixels() {
        int progress = this.data.get(0);
        int max = Math.max(1, this.data.get(1));
        if (progress <= 0) {
            return 0;
        }
        return Math.min(ARROW_WIDTH, progress * ARROW_WIDTH / max);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index < CONTAINER_SLOTS) {
            if (!this.moveItemStackTo(stack, CONTAINER_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, CONTAINER_SLOTS - 1, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
