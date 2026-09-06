package dev.vitalstages.health;

import dev.vitalstages.chat.PhraseBook;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Компилирует и исполняет настоящую чистую production-модель, без копирования формул в Python. */
public final class Mvp3CoreTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable r) {
        boolean bad = false; try { r.run(); } catch (IllegalArgumentException expected) { bad = true; }
        check(bad, "Некорректная фраза отвергнута");
    }
    private static Physiology.Rules rules() {
        return new Physiology.Rules(true, false, 15, 65, 2, .25f, 12, .45f, 10, 20, 4, .35f, 10, 40, 1200);
    }
    public static void main(String[] args) {
        var fresh = TreatmentState.fresh();
        var t = fresh.painkiller(2).adrenaline(3, 10).donation(12).afterSpeech("Тихо...", 20).allowChat(false);
        check(fresh.painkillerTicks() == 0 && fresh.chatAllowed(), "Старый record не мутировал");
        check(t.activeFlags() == 3, "Два активных эффекта");
        var next = t.tick();
        check(next.painkillerTicks() == 1 && next.adrenalineTicks() == 2 && next.adrenalineCooldownTicks() == 9, "Таймеры тикают вместе");
        var life = t.newLife();
        check(life.activeFlags() == 0 && life.adrenalineCooldownTicks() == 10 && life.donationCooldownTicks() == 12, "Новая жизнь: эффекты очищены, ограничения сохранены");
        check(!life.chatAllowed() && life.previousPhrase().equals("Тихо...") && life.chatCooldownTicks() == 20, "Opt-out и антиспам переживают смерть");
        for (int i = 0; i < 25; i++) t = t.tick();
        check(t.activeFlags() == 0 && t.chatCooldownTicks() == 0 && t.donationCooldownTicks() == 0, "Нет отрицательных таймеров");
        check(new TreatmentState(-1, Integer.MAX_VALUE, -1, -1, -1, true, null).adrenalineTicks() == Physiology.MAX_TIMER, "Ограничение повреждённых таймеров");
        var wound = new Wound(UUID.randomUUID(), BodyPart.LEFT_ARM, Wound.Type.CUT, 2, false, false, 1500, .7f, 123);
        var sterile = wound.disinfect(100);
        check(sterile.infectionRisk() == 0 && sterile.infectionTimer() == 0 && sterile.antisepticTicks() == 100, "Антисептик очищает риск");
        check(sterile.id().equals(wound.id()) && sterile.healingTicks() == 123 && sterile.isOpenCut(), "Не лечит/не перевязывает/не пересоздаёт рану");
        for (int i = 0; i < 100; i++) {
            sterile = sterile.tickInfection(true, 0, 1);
            check(sterile.infectionRisk() == 0 && sterile.infectionTimer() == 0, "Ровно 100 защищённых тиков");
        }
        check(sterile.antisepticTicks() == 0 && sterile.tickInfection(true, 0, 1).infectionRisk() > 0, "После истечения защита прекращается");
        check(wound.disinfect(100).bandage().withHealingTicks(333).antisepticTicks() == 100, "Другие лечения сохраняют антисептик");
        check(wound.disinfect(100).tickInfection(false, 0, 1).antisepticTicks() == 99, "Выключенная инфекция не замораживает препарат");
        check(wound.disinfect(100).mergeImpact(wound).antisepticTicks() == 0, "Повторная травма загрязняет заново");
        check(!Wound.fresh(BodyPart.HEAD, Wound.Type.BRUISE, 1).needsAntiseptic(), "Ушиб не является целью");
        var healthy = new Physiology.State(70, 0, 100, false, 0, false);
        check(MedicalRules.canDonate(healthy, 0, .75f, 0, 70), "Граничный здоровый донор");
        check(!MedicalRules.canDonate(healthy, .1f, 1, 0, 70), "Нет донорства при кровотечении");
        check(!MedicalRules.canDonate(healthy, 0, .74f, 0, 70), "Нет донорства при низком HP");
        check(!MedicalRules.canDonate(healthy, 0, 1, 1, 70), "Сохранённый cooldown блокирует повтор");
        check(!MedicalRules.canDonate(healthy, 0, Float.NaN, 0, 70), "NaN не открывает донорство");
        var down = new Physiology.State(25, 80, 0, true, 900, false);
        check(MedicalRules.canAdrenaline(down, 0, 0, 25), "Стабилизированный пациент допускается");
        check(!MedicalRules.canAdrenaline(down, .01f, 0, 25), "Адреналин требует остановить кровь");
        check(!MedicalRules.canAdrenaline(down, 0, 1, 25), "Cooldown принадлежит пациенту");
        check(!MedicalRules.canAdrenaline(new Physiology.State(25, 80, 0, true, 900, true), 0, 0, 25), "Критическая травма не обходится");
        check(!MedicalRules.canAdrenaline(new Physiology.State(24.9f, 80, 0, true, 900, false), 0, 0, 25), "Сначала кровь");
        check(MedicalRules.transfusedBlood(95) == 100 && MedicalRules.transfusedBlood(25) == 45, "Кровь ограничена 100");
        var state = new Physiology.State(40, 80, 40, false, 0, false);
        var raw = Physiology.step(state, 0, 37, 20, rules());
        var masked = Physiology.step(state, 0, 37, 20, rules(), 35, 0);
        check(raw.state().pain() == masked.state().pain() && raw.state().blood() == masked.state().blood(), "Обезболивание не создаёт HP/кровь и не стирает боль");
        check(Math.abs(masked.targetConsciousness() - raw.targetConsciousness() - 8.75f) < .001, "Снимается только штраф от 35 боли");
        check(Physiology.step(new Physiology.State(100, 0, 0, true, 1199, true), 0, 37, 20, rules(), 100, 100).terminal(), "Препараты не продлевают критическое окно");
        check(Physiology.step(new Physiology.State(0, 0, 0, true, 1, false), 0, 37, 20, rules(), 100, 100).terminal(), "При крови 0 лекарства не делают бессмертным");
        var book = new PhraseBook(List.of("  Первая  ", "Вторая", "Первая", "Третья"));
        check(book.phrases().equals(List.of("Первая", "Вторая", "Третья")), "Trim/dedup без перестановки");
        for (String previous : book.phrases()) for (int pick = 0; pick < 2; pick++) {
            int value = pick;
            check(!book.choose(previous, bound -> value).orElseThrow().equals(previous), "Нет непосредственного повтора при наличии выбора");
        }
        check(PhraseBook.empty().choose("", bound -> 0).isEmpty(), "Пустой файл молчит");
        check(new PhraseBook(List.of("Одна")).choose("Одна", bound -> 0).orElseThrow().equals("Одна"), "Одна строка работает с cooldown");
        check(new PhraseBook(List.of("Ночь 🌙")).phrases().size() == 1, "Валидный Unicode разрешён");
        rejects(() -> new PhraseBook(List.of("/op name")));
        rejects(() -> new PhraseBook(List.of("  /say test")));
        rejects(() -> new PhraseBook(List.of("Первая\n<Другой> Подделка")));
        rejects(() -> new PhraseBook(List.of("\u00A7cцвет")));
        rejects(() -> new PhraseBook(List.of("скрыто\u202E")));
        rejects(() -> new PhraseBook(List.of("\uD800")));
        rejects(() -> new PhraseBook(List.of(" ")));
        rejects(() -> new PhraseBook(List.of("x".repeat(161))));
        rejects(() -> new PhraseBook(java.util.Collections.nCopies(129, "x")));
        boolean immutable = false; try { book.phrases().add("нет"); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "Кэш списка immutable");
        Random rng = new Random(303);
        for (int i = 0; i < 10000; i++) {
            float blood = rng.nextFloat() * 100, pain = rng.nextFloat() * 100, c = rng.nextFloat() * 100;
            boolean critical = rng.nextBoolean();
            var old = new Physiology.State(blood, pain, c, critical, 700, critical);
            float bleed = rng.nextFloat() * 3;
            var ordinary = Physiology.step(old, bleed, 37, 20, rules());
            var medicated = Physiology.step(old, bleed, 37, 20, rules(), rng.nextFloat() * 100, rng.nextFloat() * 100);
            check(ordinary.state().blood() == medicated.state().blood(), "Фармакология не меняет кровопотерю");
            check(ordinary.state().pain() == medicated.state().pain(), "Фармакология не переписывает raw pain");
            check(medicated.state().consciousness() >= 0 && medicated.state().consciousness() <= 100, "Границы сознания");
            if (critical || medicated.state().blood() <= 15) check(medicated.targetConsciousness() == 0, "Критические условия сильнее стимуляторов");
            check(MedicalRules.transfusedBlood(blood) >= blood && MedicalRules.transfusedBlood(blood) <= 100, "Переливание ограничено");
        }
        System.out.println("PASS: " + checks + " MVP-3 core checks (medicine, timing, literal phrases, randomized regression)");
    }
}
