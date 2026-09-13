package com.s4fmer.boozecraft.compat.jei;

import com.s4fmer.boozecraft.BoozeCraft;
import com.s4fmer.boozecraft.block.ProcessorType;
import com.s4fmer.boozecraft.drink.DrinkItem;
import com.s4fmer.boozecraft.reg.BoozeBlocks;
import com.s4fmer.boozecraft.reg.BoozeItems;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** GENERATED - JEI plugin. EMI shows the same recipes through its JEI compat layer. */
@JeiPlugin
public class BoozeJeiPlugin implements IModPlugin {

	@Override
	public ResourceLocation getPluginUid() {
		return BoozeCraft.id("jei");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		IGuiHelper helper = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(
				new ProcessCategory(helper, ProcessorType.FERMENTER, BoozeBlocks.FERMENTER.get()),
				new ProcessCategory(helper, ProcessorType.STILL, BoozeBlocks.STILL.get()),
				new ProcessCategory(helper, ProcessorType.AGING, BoozeBlocks.AGING_BARREL.get()),
				new MixCategory(helper, BoozeBlocks.BAR_COUNTER.get()));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		for (ProcessorType machine : ProcessorType.values()) {
			registration.addRecipes(ProcessCategory.typeOf(machine), ProcessCategory.recipesOf(machine));
		}
		registration.addRecipes(MixCategory.TYPE, MixCategory.recipes());

		Component drinkInfo = Component.translatable("jei.boozecraft.info.drink");
		for (Item item : BuiltInRegistries.ITEM) {
			if (item instanceof DrinkItem) {
				registration.addItemStackInfo(new ItemStack(item), drinkInfo);
			}
		}

		Component counterInfo = Component.translatable("jei.boozecraft.info.counter");
		registration.addItemStackInfo(new ItemStack(BoozeBlocks.BAR_COUNTER.get()), counterInfo);
		registration.addItemStackInfo(new ItemStack(BoozeItems.SHAKER.get()), counterInfo);

		Component machineInfo = Component.translatable("jei.boozecraft.info.machine");
		registration.addItemStackInfo(new ItemStack(BoozeBlocks.FERMENTER.get()), machineInfo);
		registration.addItemStackInfo(new ItemStack(BoozeBlocks.STILL.get()), machineInfo);
		registration.addItemStackInfo(new ItemStack(BoozeBlocks.AGING_BARREL.get()), machineInfo);
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addRecipeCatalyst(new ItemStack(BoozeBlocks.FERMENTER.get()), ProcessCategory.FERMENTING);
		registration.addRecipeCatalyst(new ItemStack(BoozeBlocks.STILL.get()), ProcessCategory.DISTILLING);
		registration.addRecipeCatalyst(new ItemStack(BoozeBlocks.AGING_BARREL.get()), ProcessCategory.AGING);
		registration.addRecipeCatalyst(new ItemStack(BoozeBlocks.BAR_COUNTER.get()), MixCategory.TYPE);
		registration.addRecipeCatalyst(new ItemStack(BoozeItems.SHAKER.get()), MixCategory.TYPE);
	}
}
