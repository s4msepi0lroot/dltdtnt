package dev.vitalstages.network;
import dev.vitalstages.VitalStages;
import dev.vitalstages.health.Physiology;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Owner-only S2C. Полные списки ран для другого игрока без необходимости не рассылаются. */
public record HealthSyncPayload(UUID playerId, ResourceLocation dimension, float health, float blood, float bleed,
        float consciousness, float pain, float temperature, int stage, int downTicks, int deathWindowTicks,
        int packedLimbs, boolean enabled, boolean delirium, float intensity) implements CustomPacketPayload {
    public static final Type<HealthSyncPayload> TYPE = new Type<>(VitalStages.id("health_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HealthSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public HealthSyncPayload decode(RegistryFriendlyByteBuf b) {
            return new HealthSyncPayload(b.readUUID(), b.readResourceLocation(), b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                    b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readFloat());
        }
        @Override public void encode(RegistryFriendlyByteBuf b, HealthSyncPayload p) {
            b.writeUUID(p.playerId); b.writeResourceLocation(p.dimension); b.writeFloat(p.health);
            b.writeFloat(p.blood); b.writeFloat(p.bleed); b.writeFloat(p.consciousness); b.writeFloat(p.pain);
            b.writeFloat(p.temperature); b.writeVarInt(p.stage); b.writeVarInt(p.downTicks);
            b.writeVarInt(p.deathWindowTicks); b.writeVarInt(p.packedLimbs);
            b.writeBoolean(p.enabled); b.writeBoolean(p.delirium); b.writeFloat(p.intensity);
        }
    };
    public HealthSyncPayload {
        health = Physiology.clamp(health, 0, 1024, 0); blood = Physiology.clamp(blood, 0, 100, 100);
        bleed = Physiology.clamp(bleed, 0, 100, 0); consciousness = Physiology.clamp(consciousness, 0, 100, 100);
        pain = Physiology.clamp(pain, 0, 100, 0); temperature = Physiology.clamp(temperature, 25, 45, 37);
        stage = Math.max(0, Math.min(2, stage)); downTicks = Math.max(0, Math.min(Physiology.MAX_TIMER, downTicks));
        deathWindowTicks = Math.max(100, Math.min(72000, deathWindowTicks)); packedLimbs &= 0xFFF;
        intensity = Physiology.clamp(intensity, 0, 3, 0);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
