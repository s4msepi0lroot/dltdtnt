package com.s4fmer.boozecraft.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.s4fmer.boozecraft.BoozeCraft;
import com.s4fmer.boozecraft.recipe.MixRecipes;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** GENERATED - JEI category for bar counter mixing. */
public class MixCategory implements IRecipeCategory<MixRecipes.Entry> {

	public static final RecipeType<MixRecipes.Entry> TYPE =
			RecipeType.create(BoozeCraft.MODID, "mixing", MixRecipes.Entry.class);

	private static final int WIDTH = 150;
	private static final int HEIGHT = 44;

	private final IDrawable icon;
	private final IDrawable slot;

	public MixCategory(IGuiHelper helper, Block counter) {
		this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(counter));
		this.slot = helper.getSlotDrawable();
	}

	public static List<MixRecipes.Entry> recipes() {
		return new ArrayList<>(MixRecipes.all());
	}

	@Override
	public RecipeType<MixRecipes.Entry> getRecipeType() {
		return TYPE;
	}

	@Override
	public Component getTitle() {
		return Component.translatable("gui.boozecraft.category.mixing");
	}

	@Override
	public IDrawable getIcon() {
		return this.icon;
	}

	@Override
	public int getWidth() {
		return WIDTH;
	}

	@Override
	public int getHeight() {
		return HEIGHT;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, MixRecipes.Entry recipe, IFocusGroup focuses) {
		int x = 1;
		for (String id : recipe.inputs) {
			Item item = MixRecipes.item(id);
			if (item != null) {
				builder.addSlot(RecipeIngredientRole.INPUT, x, 14)
						.setBackground(this.slot, -1, -1)
						.addItemStack(new ItemStack(item));
			}
			x += 19;
		}
		Item shaker = MixRecipes.item("boozecraft:shaker");
		if (shaker != null) {
			builder.addSlot(RecipeIngredientRole.CATALYST, x + 6, 14)
					.setBackground(this.slot, -1, -1)
					.addItemStack(new ItemStack(shaker));
		}
		Item result = MixRecipes.item(recipe.result);
		if (result != null) {
			builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 21, 14)
					.setBackground(this.slot, -1, -1)
					.addItemStack(new ItemStack(result, recipe.count));
		}
	}
}
