package ru.s4fmer.badhabits.network;

/**
 * Last status received from the server. Plain primitives on purpose: this class touches no client-only
 * Minecraft classes, so it can safely be referenced from common code (packet handler) and from the
 * client-only HUD renderer.
 */
public final class BhStatusHolder {
    /** The HUD hides itself if the server stopped sending updates (disconnect, feature disabled, ...). */
    private static final long STALE_MILLIS = 5_000L;

    private static volatile float nicotineAddiction;
    private static volatile float nicotineDose;
    private static volatile int nicotineStage;
    private static volatile float narcoticAddiction;
    private static volatile float narcoticDose;
    private static volatile int narcoticStage;
    private static volatile long updatedAt;

    private BhStatusHolder() {
    }

    public static void accept(StatusPayload payload) {
        nicotineAddiction = StatusPayload.addictionOf(payload.nicotine());
        nicotineStage = StatusPayload.stageOf(payload.nicotine());
        nicotineDose = StatusPayload.doseOf(payload.nicotineDose());
        narcoticAddiction = StatusPayload.addictionOf(payload.narcotic());
        narcoticStage = StatusPayload.stageOf(payload.narcotic());
        narcoticDose = StatusPayload.doseOf(payload.narcoticDose());
        updatedAt = System.currentTimeMillis();
    }

    public static void clear() {
        nicotineAddiction = 0.0F;
        nicotineDose = 0.0F;
        nicotineStage = 0;
        narcoticAddiction = 0.0F;
        narcoticDose = 0.0F;
        narcoticStage = 0;
        updatedAt = 0L;
    }

    public static boolean fresh() {
        long stamp = updatedAt;
        return stamp != 0L && System.currentTimeMillis() - stamp < STALE_MILLIS;
    }

    public static float nicotineAddiction() {
        return nicotineAddiction;
    }

    public static float nicotineDose() {
        return nicotineDose;
    }

    public static int nicotineStage() {
        return nicotineStage;
    }

    public static float narcoticAddiction() {
        return narcoticAddiction;
    }

    public static float narcoticDose() {
        return narcoticDose;
    }

    public static int narcoticStage() {
        return narcoticStage;
    }
}
