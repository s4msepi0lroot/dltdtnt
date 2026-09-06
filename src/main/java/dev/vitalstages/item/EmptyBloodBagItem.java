package dev.vitalstages.item;

import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.event.TickHandler;
import dev.vitalstages.network.HealthNetwork;
import dev.vitalstages.registry.HealthAttachments;
import dev.vitalstages.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Только добровольное донорство себе в руку. Забрать кровь ПКМ у другого игрока нельзя. */
public final class EmptyBloodBagItem extends Item {
    public EmptyBloodBagItem(Properties p) { super(p); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer p) || !p.isAlive() || p.isSpectator()
                || stack.isEmpty() || !stack.is(this) || p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        var d = HealthAttachments.get(p);
        d.refreshVanillaHealth(p.getHealth(), p.getMaxHealth(), HealthConfig.f(HealthConfig.REVIVE_HEALTH));
        TickHandler.recomputeBleeding(d);
        // Creative — обычный административный источник предметов, не меняющий сохранённую кровь.
        if (!p.getAbilities().instabuild && !d.donateBlood(p.getHealth() / Math.max(0.001f, p.getMaxHealth()),
                HealthConfig.f(HealthConfig.DONATION_MIN_BLOOD), HealthConfig.DONATION_COOLDOWN_SECONDS.get() * 20)) {
            p.displayClientMessage(Component.translatable("vitalstages.donation.failed"), true);
            return InteractionResultHolder.fail(stack);
        }
        ItemStack filled = new ItemStack(ModItems.BLOOD_BAG.get());
        if (!p.getAbilities().instabuild) stack.shrink(1);
        p.getCooldowns().addCooldown(this, HealthConfig.MEDICINE_COOLDOWN_TICKS.get());
        HealthNetwork.sync(p);
        p.displayClientMessage(Component.translatable("vitalstages.donation.success"), true);
        // Последний пустой пакет заменяется результатом; иначе результат в инвентарь или на землю.
        if (stack.isEmpty()) return InteractionResultHolder.consume(filled);
        if (!p.getInventory().add(filled)) p.drop(filled, false);
        return InteractionResultHolder.consume(stack);
    }
}
