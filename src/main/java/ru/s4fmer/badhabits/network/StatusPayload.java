package ru.s4fmer.badhabits.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.s4fmer.badhabits.BadHabits;
import ru.s4fmer.badhabits.addiction.Meter;

/**
 * Server -> client status packet for the HUD bars.
 *
 * <p>Only four VarInts, sent once per second per player. Values are packed by hand so the payload stays
 * tiny and the stream codec only needs primitives, which keeps it compatible with hybrid cores.</p>
 *
 * <p>Layout of the packed meter value: {@code (round(addiction * 10) << 3) | withdrawalStage}.</p>
 */
public record StatusPayload(int nicotine, int nicotineDose, int narcotic, int narcoticDose)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StatusPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BadHabits.MODID, "status"));

    public static final StreamCodec<ByteBuf, StatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StatusPayload::nicotine,
            ByteBufCodecs.VAR_INT, StatusPayload::nicotineDose,
            ByteBufCodecs.VAR_INT, StatusPayload::narcotic,
            ByteBufCodecs.VAR_INT, StatusPayload::narcoticDose,
            StatusPayload::new);

    public static StatusPayload of(Meter nicotine, Meter narcotic) {
        return new StatusPayload(
                packMeter(nicotine),
                packDose(nicotine),
                packMeter(narcotic),
                packDose(narcotic));
    }

    public static StatusPayload empty() {
        return new StatusPayload(0, 0, 0, 0);
    }

    private static int packMeter(Meter meter) {
        if (meter == null) {
            return 0;
        }
        int value = Math.max(0, Math.round(meter.addiction * 10.0F));
        int stage = Math.max(0, Math.min(7, meter.stage));
        return (value << 3) | stage;
    }

    private static int packDose(Meter meter) {
        return meter == null ? 0 : Math.max(0, Math.round(meter.dose * 10.0F));
    }

    public static float addictionOf(int packed) {
        return (packed >>> 3) / 10.0F;
    }

    public static int stageOf(int packed) {
        return packed & 7;
    }

    public static float doseOf(int packed) {
        return packed / 10.0F;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
