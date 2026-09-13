package com.s4fmer.boozecraft.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.s4fmer.boozecraft.BoozeCraft;
import com.s4fmer.boozecraft.block.ProcessorType;
import com.s4fmer.boozecraft.recipe.ProcessRecipes;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** GENERATED - JEI category for the fermenter, the still and the aging barrel. */
public class ProcessCategory implements IRecipeCategory<ProcessRecipes.Entry> {

	public static final RecipeType<ProcessRecipes.Entry> FERMENTING =
			RecipeType.create(BoozeCraft.MODID, "fermenting", ProcessRecipes.Entry.class);
	public static final RecipeType<ProcessRecipes.Entry> DISTILLING =
			RecipeType.create(BoozeCraft.MODID, "distilling", ProcessRecipes.Entry.class);
	public static final RecipeType<ProcessRecipes.Entry> AGING =
			RecipeType.create(BoozeCraft.MODID, "aging", ProcessRecipes.Entry.class);

	private static final int WIDTH = 150;
	private static final int HEIGHT = 44;

	private final ProcessorType machine;
	private final IDrawable icon;
	private final IDrawable slot;

	public ProcessCategory(IGuiHelper helper, ProcessorType machine, Block block) {
		this.machine = machine;
		this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(block));
		this.slot = helper.getSlotDrawable();
	}

	public static RecipeType<ProcessRecipes.Entry> typeOf(ProcessorType machine) {
		if (machine == ProcessorType.STILL) {
			return DISTILLING;
		}
		if (machine == ProcessorType.AGING) {
			return AGING;
		}
		return FERMENTING;
	}

	public static List<ProcessRecipes.Entry> recipesOf(ProcessorType machine) {
		List<ProcessRecipes.Entry> out = new ArrayList<>();
		for (ProcessRecipes.Entry entry : ProcessRecipes.all()) {
			if (entry.type == machine) {
				out.add(entry);
			}
		}
		return out;
	}

	@Override
	public RecipeType<ProcessRecipes.Entry> getRecipeType() {
		return typeOf(this.machine);
	}

	@Override
	public Component getTitle() {
		return Component.translatable(this.machine.translationKey());
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
	public void setRecipe(IRecipeLayoutBuilder builder, ProcessRecipes.Entry recipe, IFocusGroup focuses) {
		int x = 1;
		for (String id : recipe.inputs) {
			Item item = ProcessRecipes.item(id);
			if (item != null) {
				builder.addSlot(RecipeIngredientRole.INPUT, x, 22)
						.setBackground(this.slot, -1, -1)
						.addItemStack(new ItemStack(item));
			}
			x += 19;
		}
		Item result = ProcessRecipes.item(recipe.result);
		if (result != null) {
			builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 21, 22)
					.setBackground(this.slot, -1, -1)
					.addItemStack(new ItemStack(result, recipe.count));
		}
	}

	@Override
	public void draw(ProcessRecipes.Entry recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
		Font font = Minecraft.getInstance().font;
		Component seconds = Component.translatable("gui.boozecraft.seconds", Integer.toString(recipe.time / 20));
		graphics.drawString(font, seconds.getString(), 1, 4, 0x404040, false);
	}
}
