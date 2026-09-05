package dev.vitalstages.health;

/** Прогресс восстановления — игровые онлайн-тики. Смена часов ОС на него не влияет. */
public final class RecoveryClock {
    private RecoveryClock() {}
    public record Step(int ticks, boolean healed) {}
    public static Step advance(int previous, int duration, boolean allowed) {
        int age = Math.max(0, Math.min(Physiology.MAX_TIMER, previous));
        int required = Math.max(1, Math.min(Physiology.MAX_TIMER, duration));
        if (!allowed) return new Step(age, false);
        int next = Math.min(required, age + 1);
        return new Step(next, next >= required);
    }
}
