package dev.vitalstages.item;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.event.DamageEventHandler;
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

public final class BandageItem extends Item {
    public BandageItem(Properties p) { super(p); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer p)) return InteractionResultHolder.fail(stack);
        return apply(p, p, stack) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player)) return InteractionResult.PASS;
        if (user.level().isClientSide) return InteractionResult.SUCCESS;
        if (!(user instanceof ServerPlayer actor) || !(target instanceof ServerPlayer patient)) return InteractionResult.FAIL;
        return apply(actor, patient, stack) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
    private boolean apply(ServerPlayer actor, ServerPlayer patient, ItemStack stack) {
        // Проверки повторяются на сервере: клиент не выбирает кровь, рану или стоимость лечения.
        if (!actor.isAlive() || !patient.isAlive() || actor.isSpectator() || patient.isSpectator()
                || stack.isEmpty() || actor.level() != patient.level() || actor.distanceToSqr(patient) > 9
                || !actor.hasLineOfSight(patient) || actor.getCooldowns().isOnCooldown(this)
                || (DamageEventHandler.eligible(actor) && HealthAttachments.get(actor).unconscious())) return false;
        PlayerHealthData d = HealthAttachments.get(patient);
        if (!d.bandageWorstOpenCut()) {
            actor.displayClientMessage(Component.translatable("vitalstages.no_open_cut"), true); return false;
        }
        TickHandler.recomputeBleeding(d);
        // Сознание вернёт TickHandler, только если состояние действительно стало жизнеспособным.
        if (!actor.getAbilities().instabuild) stack.shrink(1);
        actor.getCooldowns().addCooldown(this, HealthConfig.BANDAGE_COOLDOWN_TICKS.get());
        actor.displayClientMessage(Component.translatable("vitalstages.bandaged"), true);
        HealthNetwork.sync(patient); return true;
    }
}
