package ru.s4fmer.badhabits.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import ru.s4fmer.badhabits.BhConfig;

import java.util.List;

/**
 * The lighter. Needed for every smokable item; loses 1-2 durability per cigarette
 * (handled by {@link ToolHelper#damage}). Optionally works as flint and steel.
 */
public class LighterItem extends Item {

    public LighterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!BhConfig.LIGHTER_IGNITES.get()) {
            return InteractionResult.PASS;
        }

        BlockPos target = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(target).isAir()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            level.setBlockAndUpdate(target, Blocks.FIRE.defaultBlockState());
            level.playSound(null, target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D,
                    SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                ToolHelper.damage(serverPlayer, context.getItemInHand(), 2);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("badhabits.tip.lighter").withStyle(ChatFormatting.GRAY));
    }
}
