package dev.vitalstages.network;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.event.DamageEventHandler;
import dev.vitalstages.health.PlayerHealthData;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.Objects;
import java.util.function.Consumer;

public final class HealthNetwork {
    // Общий класс НЕ ссылается на Minecraft/client классы: dedicated server загружается отдельно.
    private static volatile Consumer<HealthSyncPayload> clientReceiver = payload -> {};
    private HealthNetwork() {}
    public static void installClientReceiver(Consumer<HealthSyncPayload> receiver) { clientReceiver = Objects.requireNonNull(receiver); }
    public static void register(RegisterPayloadHandlersEvent e) {
        // По умолчанию registrar выполняет handler на MAIN thread; дополнительный enqueueWork не нужен.
        e.registrar("3").playToClient(HealthSyncPayload.TYPE, HealthSyncPayload.STREAM_CODEC,
                (payload, context) -> clientReceiver.accept(payload));
    }
    public static void sync(ServerPlayer p) {
        PlayerHealthData d = HealthAttachments.get(p);
        PacketDistributor.sendToPlayer(p, new HealthSyncPayload(p.getUUID(), p.level().dimension().location(),
                d.health(), d.bloodLevel(), d.bleedingRate(), d.consciousness(), d.effectivePain(HealthConfig.f(HealthConfig.PAIN_RELIEF)), d.bodyTemperature(),
                d.stage(HealthConfig.f(HealthConfig.PRESYNCOPE_THRESHOLD)).ordinal(), d.unconsciousTicks(),
                HealthConfig.windowTicks(), d.packedLimbStatus(), d.splintedMask(), d.openCutMask(), d.treatments().activeFlags(), DamageEventHandler.eligible(p),
                HealthConfig.ENABLE_DELIRIUM.get(), HealthConfig.f(HealthConfig.DELIRIUM_INTENSITY)));
    }
}
