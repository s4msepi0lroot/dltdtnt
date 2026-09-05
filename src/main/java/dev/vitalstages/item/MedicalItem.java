package dev.vitalstages.item;

import dev.vitalstages.event.DamageEventHandler;
import dev.vitalstages.event.FractureEffects;
import dev.vitalstages.event.TickHandler;
import dev.vitalstages.health.PlayerHealthData;
import dev.vitalstages.network.HealthNetwork;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Одна серверная проверка для бинта и шины: новые лекарства не обходят правила через другой Item. */
public abstract class MedicalItem extends Item {
    protected MedicalItem(Properties properties) { super(properties); }
    protected abstract boolean treat(PlayerHealthData data);
    protected abstract int cooldownTicks();
    protected abstract String successKey();
    protected abstract String failureKey();
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer p)) return InteractionResultHolder.fail(stack);
        return apply(p, p, stack) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player actor, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player)) return InteractionResult.PASS;
        if (actor.level().isClientSide) return InteractionResult.SUCCESS;
        if (!(actor instanceof ServerPlayer a) || !(target instanceof ServerPlayer p)) return InteractionResult.FAIL;
        return apply(a, p, stack) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
    private boolean apply(ServerPlayer actor, ServerPlayer patient, ItemStack stack) {
        if (!actor.isAlive() || !patient.isAlive() || actor.isSpectator() || patient.isSpectator()
                || stack.isEmpty() || !stack.is(this) || actor.level() != patient.level()
                || actor.distanceToSqr(patient) > 9 || !actor.hasLineOfSight(patient)
                || actor.getCooldowns().isOnCooldown(this)
                || (DamageEventHandler.eligible(actor) && HealthAttachments.get(actor).unconscious())) return false;
        PlayerHealthData d = HealthAttachments.get(patient);
        if (!treat(d)) {
            actor.displayClientMessage(Component.translatable(failureKey()), true); return false;
        }
        if (!actor.getAbilities().instabuild) stack.shrink(1);
        actor.getCooldowns().addCooldown(this, cooldownTicks());
        TickHandler.recomputeBleeding(d); FractureEffects.refresh(patient); HealthNetwork.sync(patient);
        actor.displayClientMessage(Component.translatable(successKey()), true);
        return true;
    }
}
