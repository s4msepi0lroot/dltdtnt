package com.s4fmer.boozecraft.drink;

import java.util.List;

import com.s4fmer.boozecraft.booze.BoozeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Every drinkable item in the mod. */
public class DrinkItem extends Item {

	private final DrinkDef def;

	public DrinkItem(DrinkDef def, Properties properties) {
		super(properties);
		this.def = def;
	}

	public DrinkDef def() {
		return this.def;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return this.def.vessel() == Vessel.NONE ? UseAnim.EAT : UseAnim.DRINK;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 28;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		return ItemUtils.startUsingInstantly(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (!level.isClientSide) {
			if (this.def.nutrition() > 0) {
				entity.addEffect(new MobEffectInstance(MobEffects.SATURATION, 1,
						Math.max(0, this.def.nutrition() - 1), true, false, false));
			}
			if (entity instanceof ServerPlayer serverPlayer) {
				BoozeManager.onDrink(serverPlayer, this.def);
			}
		}

		if (entity instanceof Player player) {
			player.awardStat(Stats.ITEM_USED.get(this));
			ItemStack empty = this.def.vessel().emptyStack();
			if (!empty.isEmpty()) {
				return ItemUtils.createFilledResult(stack, player, empty);
			}
		}

		stack.shrink(1);
		return stack;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		if (this.def.abv() > 0.0D) {
			tooltip.add(Component.translatable("tooltip.boozecraft.abv",
					String.format("%.0f", this.def.abv() * 100.0D)).withStyle(ChatFormatting.DARK_RED));
		}
		if (this.def.caffeine() > 0.0D) {
			tooltip.add(Component.translatable("tooltip.boozecraft.caffeine",
					String.format("%.0f", this.def.caffeine())).withStyle(ChatFormatting.GOLD));
		}
		if (this.def.curesHangover()) {
			tooltip.add(Component.translatable("tooltip.boozecraft.cures").withStyle(ChatFormatting.GREEN));
		}
		if (this.def.addictionRelief() > 0.0D) {
			tooltip.add(Component.translatable("tooltip.boozecraft.relief").withStyle(ChatFormatting.AQUA));
		}
		if (!this.def.effects().isEmpty()) {
			tooltip.add(Component.translatable("tooltip.boozecraft.starter").withStyle(ChatFormatting.DARK_GRAY));
		}
		if (this.def.category().isMixable()) {
			tooltip.add(Component.translatable("tooltip.boozecraft.mixable").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
