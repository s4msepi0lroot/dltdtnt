package dev.vitalstages.health;

import java.util.Random;

/** Настоящие проверки production-класса Physiology, без копии формул на другом языке. */
public final class PhysiologyTest {
    private static int assertions;
    private static final Physiology.Rules RULES = new Physiology.Rules(true, false,
            15, 65, 2, 0.25f, 12, 0.45f, 10, 20, 4, 0.35f, 10, 40, 1200);
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.001f, message + ": " + actual + " != " + expected);
    }
    private static Physiology.State state(float blood, float pain, float consciousness, boolean down, int ticks, boolean critical) {
        return new Physiology.State(blood, pain, consciousness, down, ticks, critical);
    }
    private static Physiology.Result step(Physiology.State s, float bleed) {
        return Physiology.step(s, bleed, 37, 20, RULES);
    }
    public static void main(String[] args) {
        Physiology.State s = state(100, 0, 100, false, 0, false);
        for (int i = 0; i < 20; i++) s = step(s, 2).state();
        near(s.blood(), 98, "20 тиков = одна секунда кровопотери");
        for (int i = 0; i < 12000; i++) s = step(s, 0).state();
        near(s.blood(), 98, "Кровь не регенерирует");

        check(step(state(0, 0, 100, false, 0, false), 0).terminal(), "Ноль крови завершает процесс");
        Physiology.Result r = step(state(0.01f, 0, 100, false, 0, false), 10);
        near(r.state().blood(), 0, "Не допускаем отрицательную кровь");
        check(r.terminal(), "Опустошение крови не ждёт таймер");

        s = Physiology.knockOut(state(100, 0, 100, false, 999, true));
        check(s.unconsciousTicks() == 0, "Первый knockout стартует окно");
        for (int i = 0; i < 1199; i++) {
            s = Physiology.knockOut(s); // Повторные летальные удары не дают вечного окна.
            r = step(s, 0); s = r.state();
            check(!r.terminal(), "До 60 секунд ещё можно спасти");
        }
        check(s.unconsciousTicks() == 1199, "Повторный knockout не обнуляет таймер");
        check(step(s, 0).terminal(), "Смерть ровно на 1200-м тике");

        s = Physiology.knockOut(state(100, 0, 100, false, 0, false));
        for (int i = 0; i < 40; i++) s = step(s, 0).state();
        check(s.unconscious(), "Не просыпаемся раньше гистерезиса сознания");
        for (int i = 0; i < 20; i++) s = step(s, 0).state();
        check(!s.unconscious() && s.unconsciousTicks() == 0, "Самовосстановление при стабильном состоянии");

        s = state(26, 50, 0, true, 10, false);
        check(step(s, 1).targetConsciousness() == 0, "Активная потеря поддерживает обморок");
        // Эквивалент перевязки: скорость стала нулевой, кровь и HP не прибавлены.
        for (int i = 0; i < 400; i++) {
            r = step(s, 0); s = r.state(); check(!r.terminal(), "После ранней перевязки сохраняется окно");
        }
        check(!s.unconscious(), "Закрытие раны может позволить очнуться");
        near(s.blood(), 26, "Перевязка не создала кровь");
        s = state(14, 0, 0, true, 0, false);
        for (int i = 0; i < 1200; i++) { r = step(s, 0); s = r.state(); }
        check(r.terminal(), "При слишком малом объёме крови одного бинта недостаточно");

        s = state(100, 0, 0, true, 0, true);
        for (int i = 0; i < 200; i++) s = step(s, 0).state();
        check(s.unconscious() && s.consciousness() == 0, "Критическая травма требует восстановления HP");
        s = state(s.blood(), s.pain(), s.consciousness(), s.unconscious(), s.unconsciousTicks(), false);
        for (int i = 0; i < 80; i++) s = step(s, 0).state();
        check(!s.unconscious(), "После снятия критической травмы возможен выход");

        Physiology.Rules off = new Physiology.Rules(false, false, 15, 65, 2, 0.25f, 12, 0.45f, 10, 20, 4, 0.35f, 10, 40, 1200);
        s = state(0, 0, 0, true, 0, false);
        for (int i = 0; i < 80; i++) {
            r = Physiology.step(s, 100, 37, 20, off); s = r.state(); check(!r.terminal(), "Отключена вся кровяная механика");
        }
        check(!s.unconscious(), "Отключённая система не держит в коме");
        near(s.blood(), 0, "Отключение не перезаписывает сохранённую кровь");
        s = state(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, false, Integer.MAX_VALUE, false);
        check(Float.isFinite(s.blood()) && Float.isFinite(s.pain()) && Float.isFinite(s.consciousness()), "Защита NaN/Infinity");
        check(s.unconsciousTicks() == Physiology.MAX_TIMER, "Ограничение таймера");
        near(Physiology.moveTowards(0.001f, 0, 1), 0, "Достижение точного нуля");

        Random random = new Random(1211);
        for (int i = 0; i < 10000; i++) {
            s = state(random.nextFloat() * 100, random.nextFloat() * 100, random.nextFloat() * 100,
                    random.nextBoolean(), random.nextInt(1300), random.nextBoolean());
            r = Physiology.step(s, random.nextFloat() * 20, random.nextFloat() * 20 + 25, random.nextInt(21), RULES);
            var t = r.state();
            check(t.blood() >= 0 && t.blood() <= s.blood(), "Кровь монотонна и ограничена");
            check(t.consciousness() >= 0 && t.consciousness() <= 100, "Сознание ограничено");
            check(t.pain() >= 0 && t.pain() <= 100, "Боль ограничена");
            check(t.unconsciousTicks() >= 0, "Таймер не переполнен");
        }
        System.out.println("PASS: " + assertions + " checks; deterministic scenarios + 10,000 randomized steps");
    }
}
