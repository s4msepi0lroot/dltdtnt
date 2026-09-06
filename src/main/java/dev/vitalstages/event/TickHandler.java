package dev.vitalstages.event;

import dev.vitalstages.VitalStages;
import dev.vitalstages.chat.DeliriumChatService;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.Physiology;
import dev.vitalstages.health.PlayerHealthData;
import dev.vitalstages.network.HealthNetwork;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = VitalStages.MOD_ID)
public final class TickHandler {
    public static final ResourceKey<DamageType> ORGAN_FAILURE =
            ResourceKey.create(Registries.DAMAGE_TYPE, VitalStages.id("organ_failure"));
    private TickHandler() {}
    public static void recomputeBleeding(PlayerHealthData data) {
        data.recomputeBleeding(HealthConfig.ENABLE_BLOOD_LOSS.get(), HealthConfig.f(HealthConfig.BASE_BLEED_PER_SEVERITY),
                HealthConfig.f(HealthConfig.BLEED_RATE_MULTIPLIER), HealthConfig.f(HealthConfig.MAX_BLEED));
    }
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
        PlayerHealthData data = HealthAttachments.get(player);
        if (!DamageEventHandler.eligible(player)) {
            // Creative/spectator приостанавливают физиологию, но не стирают раны.
            data.updateControlLock(false);
            FractureEffects.refresh(player);
            if (player.tickCount % HealthConfig.SYNC_INTERVAL_TICKS.get() == 0) HealthNetwork.sync(player);
            return;
        }
        data.refreshVanillaHealth(player.getHealth(), player.getMaxHealth(), HealthConfig.f(HealthConfig.REVIVE_HEALTH));
        recomputeBleeding(data);
        data.tickInfection(HealthConfig.ENABLE_INFECTION.get(), HealthConfig.INFECTION_DELAY_SECONDS.get() * 20,
                HealthConfig.f(HealthConfig.INFECTION_HAZARD_PER_SECOND));
        Physiology.Result result = Physiology.step(data.physiologyState(), data.bleedingRate(), data.bodyTemperature(),
                player.getFoodData().getFoodLevel(), HealthConfig.rules(), data.painRelief(HealthConfig.f(HealthConfig.PAIN_RELIEF)),
                data.treatments().adrenalineTicks() > 0 ? HealthConfig.f(HealthConfig.ADRENALINE_BONUS) : 0);
        data.accept(result.state());
        boolean recovered = false;
        if (!result.terminal()) {
            boolean stable = !data.criticalTrauma() && (!HealthConfig.ENABLE_BLOOD_LOSS.get()
                    || data.bloodLevel() > HealthConfig.f(HealthConfig.CRITICAL_BLOOD));
            recovered = data.tickRecovery(stable, HealthConfig.BRUISE_HEAL_SECONDS.get() * 20,
                    HealthConfig.CUT_HEAL_SECONDS.get() * 20, HealthConfig.BURN_HEAL_SECONDS.get() * 20,
                    HealthConfig.FRACTURE_HEAL_SECONDS.get() * 20);
            if (recovered) recomputeBleeding(data);
        }
        FractureEffects.refresh(player);
        boolean controlsChanged = data.updateControlLock(data.unconscious());
        if (data.unconscious()) {
            player.stopUsingItem(); player.setSprinting(false); player.setJumping(false); player.stopFallFlying();
            player.xxa = 0; player.yya = 0; player.zza = 0;
            if (controlsChanged) player.closeContainer();
        }
        if (controlsChanged) player.containerMenu.broadcastFullState();
        if (result.terminal()) {
            if (data.allowTerminalAttempt()) finishDeath(player, data);
        } else data.clearTerminalRetry();
        if (player.isAlive()) {
            if (!result.terminal()) DeliriumChatService.trySpeak(player, false);
            int beforeFlags = data.treatments().activeFlags();
            data.tickTreatments();
            if (controlsChanged || recovered || beforeFlags != data.treatments().activeFlags()
                    || player.tickCount % HealthConfig.SYNC_INTERVAL_TICKS.get() == 0) HealthNetwork.sync(player);
        }
    }
    private static void finishDeath(ServerPlayer player, PlayerHealthData data) {
        var type = player.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ORGAN_FAILURE);
        data.setFinalizing(true);
        try {
            // Обычный hurt -> die -> LivingDeathEvent сохраняет vanilla-дропы/respawn/статистику.
            player.hurt(new DamageSource(type), Float.MAX_VALUE);
        } finally { data.setFinalizing(false); }
    }
}
