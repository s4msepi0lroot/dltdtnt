package dev.vitalstages.health;

public final class Mvp2CoreTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void near(double a, double b, String m) { check(Math.abs(a - b) < 1e-6, m + ": " + a + " != " + b); }
    private static FractureProfile profile(int broken, int splinted, boolean on) {
        return FractureProfile.calculate(broken, splinted, on, 0.55, 0.65, 0.60);
    }
    public static void main(String[] args) {
        int legs = BodyPart.LEFT_LEG.bit() | BodyPart.RIGHT_LEG.bit();
        int arms = BodyPart.LEFT_ARM.bit() | BodyPart.RIGHT_ARM.bit();
        near(profile(BodyPart.LEFT_LEG.bit(), 0, true).movement(), 0.55, "Одна нога");
        near(profile(legs, 0, true).movement(), 0.3025, "Две ноги: произведение, не полный паралич");
        near(profile(legs, BodyPart.LEFT_LEG.bit(), true).movement(), 0.55, "Шина снимает один штраф");
        near(profile(legs, legs, true).movement(), 1, "Обе шины возвращают обычный множитель");
        near(profile(arms, 0, true).attackSpeed(), 0.4225, "Две руки: атака");
        near(profile(arms, 0, true).miningSpeed(), 0.36, "Две руки: добыча");
        near(profile(63, 0, false).movement(), 1, "Выключение механики снимает штрафы");
        near(profile(BodyPart.HEAD.bit() | BodyPart.TORSO.bit(), 0, true).attackSpeed(), 1, "Голова/торс не считаются руками");
        for (int f = 0; f < 64; f++) for (int s = 0; s < 64; s++) {
            var p = profile(f, s, true);
            int untreated = f & ~s;
            check(p.untreatedLegs() == Integer.bitCount(untreated & legs), "Маска ног");
            check(p.untreatedArms() == Integer.bitCount(untreated & arms), "Маска рук");
            check(p.movement() > 0 && p.movement() <= 1, "Граница скорости");
            check(p.attackSpeed() > 0 && p.attackSpeed() <= 1, "Граница атаки");
            check(p.miningSpeed() > 0 && p.miningSpeed() <= 1, "Граница добычи");
        }
        Wound fracture = Wound.fresh(BodyPart.LEFT_LEG, Wound.Type.FRACTURE, 2);
        check(fracture.needsSplint(), "Нужна шина");
        check(!fracture.treatmentAllowsHealing(), "Без шины сращивания нет");
        Wound splinted = fracture.splint();
        check(splinted.isSplinted() && splinted.treatmentAllowsHealing(), "Фиксация разрешает сращивание");
        check(!fracture.isSplinted(), "Исходная рана неизменна");
        check(splinted.id().equals(fracture.id()), "ID раны сохраняется");
        check(!splinted.needsSplint(), "Повторная шина не нужна");
        check(splinted.splint() == splinted, "Идемпотентность фиксации");
        int ticks = 0;
        for (int i = 0; i < 12000; i++) {
            var step = RecoveryClock.advance(ticks, 12000, splinted.treatmentAllowsHealing());
            ticks = step.ticks();
            check(step.healed() == (i == 11999), "Ровно десять игровых минут");
        }
        check(RecoveryClock.advance(500, 12000, false).ticks() == 500, "Пауза не обнуляет прогресс");
        check(!RecoveryClock.advance(500, 100, false).healed(), "Нестабильный организм не лечится при смене конфига");
        check(RecoveryClock.advance(Integer.MAX_VALUE, Physiology.MAX_TIMER, true).healed(), "Нет overflow таймера");
        Wound progressing = splinted.withHealingTicks(500);
        Wound newHit = progressing.mergeImpact(Wound.fresh(BodyPart.LEFT_LEG, Wound.Type.FRACTURE, 3));
        check(newHit.healingTicks() == 0 && !newHit.isSplinted(), "Новый удар сбрасывает фиксацию/прогресс");
        near(newHit.severity(), 5, "Тяжесть складывается");
        check(progressing.healingTicks() == 500 && progressing.isSplinted(), "Старый snapshot не мутировал");
        Wound cut = Wound.fresh(BodyPart.RIGHT_ARM, Wound.Type.CUT, 2);
        check(cut.bleedWeight() > 0 && !cut.treatmentAllowsHealing(), "Открытая рана не заживает сама");
        Wound closed = cut.bandage().withHealingTicks(123);
        check(closed.bleedWeight() == 0 && closed.treatmentAllowsHealing(), "Бинт закрывает рану");
        check(closed.tickInfection(true, 0, 1).healingTicks() == 123, "Инфекционный тик не стирает заживление");
        Wound opened = closed.mergeImpact(Wound.fresh(BodyPart.RIGHT_ARM, Wound.Type.CUT, 1));
        check(opened.isOpenCut() && opened.healingTicks() == 0, "Повторное ранение открывает CUT");
        Wound corrupt = new Wound(java.util.UUID.randomUUID(), BodyPart.HEAD, Wound.Type.BURN,
                Float.NaN, false, true, Integer.MAX_VALUE, Float.NaN, Integer.MAX_VALUE);
        check(Float.isFinite(corrupt.severity()) && Float.isFinite(corrupt.infectionRisk()), "NaN защита");
        check(!corrupt.isSplinted(), "Нельзя фиксировать ожог головы шиной");
        check(corrupt.healingTicks() == Physiology.MAX_TIMER, "Ограничение загруженного прогресса");
        boolean rejected = false;
        try { cut.mergeImpact(fracture); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Разные раны не объединяются");
        System.out.println("PASS: " + checks + " MVP-2 checks (fractures, splints, wound recovery)");
    }
}
