package dev.vitalstages.event;
import dev.vitalstages.VitalStages;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.PlayerHealthData;
import dev.vitalstages.network.HealthNetwork;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = VitalStages.MOD_ID)
public final class LifecycleHandler {
    private LifecycleHandler() {}
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Death-clone = новая жизнь; возвращение из End = новая сущность со старой медициной.
        // Заменяем целиком, не дописываем раны поверх уже скопированного attachment.
        player.setData(HealthAttachments.HEALTH, event.isWasDeath() ? new PlayerHealthData()
                : HealthAttachments.get(event.getOriginal()).copy());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) { sync(e); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { sync(e); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { sync(e); }
    private static void sync(PlayerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        PlayerHealthData d = HealthAttachments.get(p);
        d.refreshVanillaHealth(p.getHealth(), p.getMaxHealth(), HealthConfig.f(HealthConfig.REVIVE_HEALTH));
        TickHandler.recomputeBleeding(d); HealthNetwork.sync(p);
    }
    // При logout ничего не очищаем. NeoForge сохраняет attachment в player NBT.
    // Офлайн-симуляции нет: reconnect продолжает прежний таймер, а не даёт новое окно.
}
