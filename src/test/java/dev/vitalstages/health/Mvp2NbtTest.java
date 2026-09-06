package dev.vitalstages.health;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.UUID;

/** Исполняется Gradle с настоящим Minecraft classpath. Никаких NBT-заглушек. */
public final class Mvp2NbtTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("version", 1); legacy.putFloat("bloodLevel", 37);
        legacy.putFloat("consciousness", 0); legacy.putBoolean("unconscious", true);
        legacy.putBoolean("criticalTrauma", true); legacy.putInt("unconsciousTicks", 230);
        CompoundTag oldCut = new CompoundTag();
        UUID woundId = UUID.randomUUID(); oldCut.putUUID("id", woundId);
        oldCut.putString("bodyPart", "LEFT_ARM"); oldCut.putString("type", "CUT"); oldCut.putFloat("severity", 2);
        oldCut.putInt("infectionTimer", 300); oldCut.putFloat("infectionRisk", 0.25f);
        ListTag wounds = new ListTag(); wounds.add(oldCut); legacy.put("wounds", wounds);
        PlayerHealthData data = new PlayerHealthData();
        // provider не используется этим форматом: он хранит только примитивы/enum, без registry references.
        data.deserializeNBT(null, legacy);
        check(data.bloodLevel() == 37 && data.unconsciousTicks() == 230, "MVP-1: кровь и окно сохранены");
        check(data.wounds().size() == 1 && data.wounds().get(0).healingTicks() == 0, "Новый прогресс по умолчанию нулевой");
        check(data.wounds().get(0).id().equals(woundId), "UUID не меняется при миграции");
        data.enterCriticalTrauma(); check(data.unconsciousTicks() == 230, "Миграция не продлевает окно");
        var saved = data.serializeNBT(null); check(saved.getInt("version") == PlayerHealthData.FORMAT_VERSION, "Записываем текущую версию");
        var roundTrip = new PlayerHealthData(); roundTrip.deserializeNBT(null, saved);
        check(roundTrip.physiologyState().equals(data.physiologyState()), "Организм пережил NBT round-trip");
        check(roundTrip.wounds().equals(data.wounds()), "Раны пережили NBT round-trip");
        PlayerHealthData copy = data.copy();
        check(copy.bandageWorstOpenCut(), "Копия получает перевязку");
        check(data.wounds().get(0).isOpenCut(), "Clone не разделяет изменяемый список");
        check(copy.unconsciousTicks() == 230 && data.unconsciousTicks() == 230, "Clone не меняет таймер");
        Wound progressed = Wound.fresh(BodyPart.RIGHT_LEG, Wound.Type.FRACTURE, 2).splint().withHealingTicks(999);
        check(WoundNbtCodec.read(WoundNbtCodec.write(progressed)).orElseThrow().equals(progressed), "Фиксация и прогресс сохраняются");
        PlayerHealthData recovery = new PlayerHealthData();
        recovery.addImpact(Wound.fresh(BodyPart.LEFT_LEG, Wound.Type.FRACTURE, 2), 0, 0);
        check(!recovery.tickRecovery(true, 1, 1, 1, 1), "Без шины перелом остаётся");
        check(recovery.splintWorstFracture(), "Фиксируется одна рана");
        check(!recovery.splintWorstFracture(), "Нет второй нефиксированной раны");
        check(recovery.tickRecovery(true, 1, 1, 1, 1) && recovery.wounds().isEmpty(), "Сросшийся перелом удаляется");
        check(recovery.limbStatus(BodyPart.LEFT_LEG) == LimbStatus.HEALTHY, "Конечность снова здорова");
        PlayerHealthData many = new PlayerHealthData();
        for (int i = 0; i < 3; i++) for (BodyPart p : BodyPart.values()) for (Wound.Type t : Wound.Type.values())
            many.addImpact(Wound.fresh(p, t, 1), 0, 0);
        check(many.wounds().size() == PlayerHealthData.MAX_WOUNDS, "Лимит 24 агрегата");
        var future = saved.copy(); future.putInt("version", PlayerHealthData.FORMAT_VERSION + 1);
        boolean rejected = false;
        try { roundTrip.deserializeNBT(null, future); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Будущий формат не затирается");
        System.out.println("PASS: " + checks + " real-NBT checks (MVP-1 migration, copy, round-trip, recovery)");
    }
}
