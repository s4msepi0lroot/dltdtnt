package ru.s4fmer.badhabits.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import ru.s4fmer.badhabits.BhConfig;
import ru.s4fmer.badhabits.addiction.AddictionLogic;
import ru.s4fmer.badhabits.addiction.AddictionManager;
import ru.s4fmer.badhabits.addiction.Meter;
import ru.s4fmer.badhabits.addiction.PlayerAddiction;
import ru.s4fmer.badhabits.addiction.Substance;
import ru.s4fmer.badhabits.util.Msg;

import java.util.List;

/**
 * Detox tonic: cuts addiction on both tracks and leaves a tiny "bridge" dose so the player does not
 * fall straight into withdrawal. Tastes awful - hence the side effects.
 */
public class DetoxItem extends Item {

    public DetoxItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 40;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        if (living instanceof ServerPlayer player) {
            float cut = BhConfig.DETOX_REDUCTION.get().floatValue();
            PlayerAddiction data = AddictionManager.get(player);
            for (Substance substance : Substance.values()) {
                Meter meter = data.meter(substance);
                if (meter.addiction <= 0.0F) {
                    continue;
                }
                meter.addiction = Math.max(0.0F, meter.addiction - cut);
                meter.dose = Math.max(meter.dose, 2.0F);
                meter.lastDose = Math.max(1.0F, meter.lastDose * 0.5F);
                meter.withdrawalSeconds = 0;
                meter.stage = 0;
            }
            AddictionManager.markDirty();

            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 300, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0, false, true));

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8F, 1.0F);
            Msg.bar(player, Msg.tr("badhabits.msg.detox", AddictionLogic.fmt(cut)).withStyle(ChatFormatting.GREEN));
        }

        if (living instanceof Player player && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("badhabits.tip.detox").withStyle(ChatFormatting.GREEN));
    }
}
