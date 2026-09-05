package dev.vitalstages.registry;
import dev.vitalstages.VitalStages;
import dev.vitalstages.health.HealthView;
import dev.vitalstages.health.PlayerHealthData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

public final class HealthAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VitalStages.MOD_ID);
    public static final Supplier<AttachmentType<PlayerHealthData>> HEALTH = TYPES.register("health",
            () -> AttachmentType.serializable(PlayerHealthData::new).build());
    public static final EntityCapability<HealthView, Void> VIEW =
            EntityCapability.createVoid(VitalStages.id("health"), HealthView.class);
    private HealthAttachments() {}
    public static PlayerHealthData get(Player player) { return player.getData(HEALTH); }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Attachment — сохранение, capability — read-only API над ТЕМ ЖЕ объектом.
        // На клиенте несинхронизированный attachment не выдаётся за правду.
        event.registerEntity(VIEW, EntityType.PLAYER,
                (player, context) -> player.level().isClientSide ? null : get(player));
    }
}
