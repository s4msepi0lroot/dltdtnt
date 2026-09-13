package com.s4fmer.boozecraft.drink;

import com.s4fmer.boozecraft.reg.BoozeItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What you hold the drink in - and what you get back after drinking it. */
public enum Vessel {
	BOTTLE,
	CUP,
	MUG,
	CAN,
	SHOT,
	NONE;

	public ItemStack emptyStack() {
		switch (this) {
			case BOTTLE:
				return new ItemStack(Items.GLASS_BOTTLE);
			case CUP:
				return new ItemStack(BoozeItems.GLASS_CUP.get());
			case MUG:
				return new ItemStack(BoozeItems.MUG.get());
			case CAN:
				return new ItemStack(BoozeItems.EMPTY_CAN.get());
			case SHOT:
				return new ItemStack(BoozeItems.SHOT_GLASS.get());
			default:
				return ItemStack.EMPTY;
		}
	}
}
