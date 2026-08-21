package ru.s4fmer.badhabits.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory search + manual durability handling.
 *
 * <p>ItemStack#hurtAndBreak has different signatures across 1.21.x builds, so the mod does the damage
 * bookkeeping itself. This keeps the code compiling on any 1.21.1 NeoForge build and behaves the same
 * on hybrid servers.</p>
 */
public final class ToolHelper {
    private ToolHelper() {
    }

    /** Returns the first matching stack (hands are checked first), or ItemStack.EMPTY. */
    public static ItemStack find(Player player, UseTool tool) {
        if (tool == null || tool == UseTool.NONE) {
            return ItemStack.EMPTY;
        }
        Item item = tool.item();
        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack main = player.getMainHandItem();
        if (main.is(item)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.is(item)) {
            return off;
        }

        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean has(Player player, UseTool tool) {
        return tool == null || tool == UseTool.NONE || !find(player, tool).isEmpty();
    }

    /** Spends durability; breaks the tool with the vanilla sound when it runs out. */
    public static void damage(ServerPlayer player, ItemStack tool, int amount) {
        if (tool.isEmpty() || amount <= 0 || player.getAbilities().instabuild) {
            return;
        }
        if (!tool.isDamageableItem()) {
            return;
        }
        int next = tool.getDamageValue() + amount;
        if (next >= tool.getMaxDamage()) {
            tool.shrink(1);
            tool.setDamageValue(0);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 0.9F);
        } else {
            tool.setDamageValue(next);
        }
    }
}
