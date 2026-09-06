package dev.vitalstages.health;

import dev.vitalstages.chat.JsonPhraseCodec;
import net.minecraft.nbt.CompoundTag;
import java.nio.charset.StandardCharsets;

/** Настоящие NBT и Gson из Minecraft classpath. Выполняется отдельной обязательной JavaExec-задачей. */
public final class Mvp3IntegrationTest {
    private static int checks;
    private static void check(boolean v, String m) { checks++; if (!v) throw new AssertionError(m); }
    private static void rejects(String json) {
        boolean rejected = false;
        try { JsonPhraseCodec.parse(json.getBytes(StandardCharsets.UTF_8)); } catch (Exception expected) { rejected = true; }
        check(rejected, "Неподходящий JSON отвергнут");
    }
    public static void main(String[] args) throws Exception {
        var parsed = JsonPhraseCodec.parse("{\"version\":1,\"phrases\":[\"Тихо...\",\"Где я?\"]}".getBytes(StandardCharsets.UTF_8));
        check(parsed.phrases().size() == 2, "Реальный JSON parser");
        check(JsonPhraseCodec.parse("\uFEFF{\"phrases\":[],\"version\":1}".getBytes(StandardCharsets.UTF_8)).phrases().isEmpty(), "BOM и порядок ключей");
        rejects("{\"version\":1,\"phrases\":[{\"text\":\"x\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/op x\"}}]}");
        rejects("{\"version\":1,\"phrases\":[\"/op x\"]}");
        rejects("{\"version\":1,\"phrases\":[\"x\\ny\"]}");
        rejects("{\"version\":1,\"phrases\":[1]}");
        rejects("{\"version\":1,\"phrases\":[null]}");
        rejects("{\"version\":1,\"phrases\":[],\"version\":1}");
        rejects("{\"version\":2,\"phrases\":[]}");
        rejects("{\"version\":\"1\",\"phrases\":[]}");
        rejects("{\"version\":1.5,\"phrases\":[]}");
        rejects("{\"phrases\":[]}");
        rejects("{\"version\":1}");
        rejects("{\"version\":1,\"phrases\":[],\"commands\":[]}");
        rejects("{version:1,phrases:[]}");
        rejects("{\"version\":1,\"phrases\":[],}");
        rejects("{\"version\":1,\"phrases\":[]} {}");
        rejects("{\"version\":1,/* comment */\"phrases\":[]}");
        boolean oversized = false; try { JsonPhraseCodec.parse(new byte[65537]); } catch (IllegalArgumentException expected) { oversized = true; }
        check(oversized, "Байтовый лимит");
        boolean badUtf8 = false; try { JsonPhraseCodec.parse(new byte[]{(byte)0xC3, (byte)0x28}); } catch (java.io.IOException expected) { badUtf8 = true; }
        check(badUtf8, "Невалидный UTF-8 не заменяется молча");
        for (int version : new int[]{1, 2}) {
            CompoundTag legacy = new CompoundTag(); legacy.putInt("version", version); legacy.putFloat("bloodLevel", 31);
            legacy.putBoolean("unconscious", true); legacy.putFloat("consciousness", 0); legacy.putInt("unconsciousTicks", 451);
            PlayerHealthData migrated = new PlayerHealthData(); migrated.deserializeNBT(null, legacy);
            check(migrated.bloodLevel() == 31 && migrated.unconsciousTicks() == 451, "Старый организм сохраняется");
            check(migrated.treatments().activeFlags() == 0 && migrated.treatments().chatAllowed(), "Default новых полей");
        }
        PlayerHealthData d = new PlayerHealthData();
        d.addImpact(Wound.fresh(BodyPart.LEFT_ARM, Wound.Type.CUT, 2), 14, 0);
        check(d.disinfectWorstWound(100), "Обработана настоящая рана");
        check(!d.disinfectWorstWound(100), "Препарат не складывается на одной ране");
        check(d.takePainkiller(200), "Принято обезболивающее");
        check(!d.takePainkiller(200), "Нет двойной дозы");
        d.allowDeliriumChat(false); d.recordSpeech("Где я?", 333);
        var saved = d.serializeNBT(null); check(saved.getInt("version") == 3, "NBT 3");
        PlayerHealthData loaded = new PlayerHealthData(); loaded.deserializeNBT(null, saved);
        check(loaded.treatments().equals(d.treatments()) && loaded.wounds().equals(d.wounds()), "Все лекарства/фразы/раны пережили round-trip");
        var copied = loaded.copy(); copied.tickTreatments();
        check(loaded.treatments().painkillerTicks() == 200 && copied.treatments().painkillerTicks() == 199, "Clone изолирован");
        check(!loaded.newLife().treatments().chatAllowed() && loaded.newLife().treatments().activeFlags() == 0, "Opt-out пережил смерть, эффекты — нет");
        PlayerHealthData donor = new PlayerHealthData();
        check(donor.donateBlood(1, 70, 6000) && donor.bloodLevel() == 80, "Реальное списание 20 у донора");
        check(!donor.donateBlood(1, 70, 6000), "Повторная выдача пакета запрещена");
        check(donor.newLife().treatments().donationCooldownTicks() == 6000, "Донорский cooldown пережил смерть");
        var donorLoaded = new PlayerHealthData(); donorLoaded.deserializeNBT(null, donor.serializeNBT(null));
        check(!donorLoaded.donateBlood(1, 70, 6000), "Перезаход не выдаёт ещё один пакет");
        PlayerHealthData patient = new PlayerHealthData();
        patient.accept(new Physiology.State(25, 80, 0, true, 700, false));
        check(patient.giveAdrenaline(25, 30, 400, 3600), "Адреналин будит после стабилизации");
        check(!patient.unconscious() && patient.bloodLevel() == 25 && patient.health() == 20, "Инъекция не добавляет HP/кровь");
        patient.knockOut();
        check(!patient.giveAdrenaline(25, 30, 400, 3600), "Нельзя продлевать помощь повторной инъекцией");
        check(!patient.takePainkiller(200), "Таблетка бессознательному запрещена");
        check(patient.transfuseBlood() && patient.bloodLevel() == 45, "Одна доза перелита");
        PlayerHealthData critical = new PlayerHealthData(); critical.enterCriticalTrauma();
        check(!critical.giveAdrenaline(25, 30, 400, 3600), "Критическая травма требует восстановить vanilla HP");
        System.out.println("PASS: " + checks + " MVP-3 integration checks (strict Gson, NBT migration, treatments)");
    }
}
