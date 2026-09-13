package com.s4fmer.boozecraft.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Used on a bar counter to mix everything standing on it.
 * The actual mixing happens in BarCounterBlockEntity (server side).
 */
public class ShakerItem extends Item {

	public ShakerItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.boozecraft.shaker").withStyle(ChatFormatting.GRAY));
	}
}
