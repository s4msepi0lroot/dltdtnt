package com.s4fmer.boozecraft.reg;

import java.util.function.Supplier;

import com.s4fmer.boozecraft.BoozeCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BoozeTabs {

	public static final DeferredRegister<CreativeModeTab> TABS =
			DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BoozeCraft.MODID);

	public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.boozecraft"))
			.icon(() -> new ItemStack(BoozeItems.MUG.get()))
			.displayItems((parameters, output) -> {
				for (DeferredItem<? extends Item> item : BoozeItems.CREATIVE_ORDER) {
					output.accept(item.get());
				}
			})
			.build());

	private BoozeTabs() {
	}
}
