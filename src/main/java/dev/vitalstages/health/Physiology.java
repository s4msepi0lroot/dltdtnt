package dev.vitalstages.health;

/** Детерминированная модель без зависимостей от Minecraft, сети и системных часов. */
public final class Physiology {
    public static final float DT = 1.0f / 20;
    public static final int MAX_TIMER = 2_000_000;
    private Physiology() {}
    public record State(float blood, float pain, float consciousness, boolean unconscious,
                        int unconsciousTicks, boolean criticalTrauma) {
        public State {
            blood = clamp(blood, 0, 100, 100);
            pain = clamp(pain, 0, 100, 0);
            consciousness = clamp(consciousness, 0, 100, 100);
            unconsciousTicks = Math.max(0, Math.min(MAX_TIMER, unconsciousTicks));
        }
    }
    public record Rules(boolean bloodLoss, boolean temperature, float comaBloodThreshold,
            float bloodPenaltyStart, float bloodPenaltyScale, float painPenaltyScale,
            float activeBleedPenalty, float hungerPenaltyScale, float temperaturePenaltyScale,
            float fallPerSecond, float recoveryPerSecond, float painRecoveryPerSecond,
            float wakeThreshold, int minimumDownTicks, int deathWindowTicks) {}
    public record Result(State state, float targetConsciousness, boolean terminal) {}

    public static Result step(State old, float bleedingRate, float temperature, int foodLevel, Rules r) {
        float bleed = r.bloodLoss ? clamp(bleedingRate, 0, 100, 0) : 0;
        // Вход — единицы/секунду. На каждом игровом тике снимаем 1/20, а не всю скорость.
        float blood = Math.max(0, old.blood - bleed * DT);
        float pain = Math.max(0, old.pain - r.painRecoveryPerSecond * DT);
        // Отключение системы не стирает кровь, но приостанавливает все её последствия.
        float effectiveBlood = r.bloodLoss ? blood : 100;
        float bloodPenalty = Math.max(0, r.bloodPenaltyStart - effectiveBlood) * r.bloodPenaltyScale;
        float tempPenalty = r.temperature
                ? Math.abs(clamp(temperature, 25, 45, 37) - 37) * r.temperaturePenaltyScale : 0;
        float hungerPenalty = (20 - Math.max(0, Math.min(20, foodLevel))) * r.hungerPenaltyScale;
        float target = clamp(100 - bloodPenalty - pain * r.painPenaltyScale
                - bleed * r.activeBleedPenalty - tempPenalty - hungerPenalty, 0, 100, 0);
        if (effectiveBlood <= r.comaBloodThreshold || old.criticalTrauma) target = 0;
        float speed = target < old.consciousness ? r.fallPerSecond : r.recoveryPerSecond;
        float consciousness = moveTowards(old.consciousness, target, speed * DT);
        boolean down = old.unconscious || consciousness <= 0;
        int ticks = down ? Math.min(MAX_TIMER, old.unconsciousTicks + 1) : 0;
        // Гистерезис исключает циклы «очнулся на один тик — снова упал».
        if (down && !old.criticalTrauma && consciousness >= r.wakeThreshold
                && ticks >= r.minimumDownTicks) { down = false; ticks = 0; }
        boolean terminal = (r.bloodLoss && blood <= 0) || (down && ticks >= r.deathWindowTicks);
        return new Result(new State(blood, pain, consciousness, down, ticks, old.criticalTrauma),
                target, terminal);
    }
    public static State knockOut(State old) {
        return new State(old.blood, old.pain, 0, true,
                old.unconscious ? old.unconsciousTicks : 0, old.criticalTrauma);
    }
    public static float clamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    public static float moveTowards(float value, float target, float delta) {
        // Достигает ровно нуля, в отличие от асимптотической lerp.
        return value < target ? Math.min(target, value + Math.max(0, delta))
                : Math.max(target, value - Math.max(0, delta));
    }
}
