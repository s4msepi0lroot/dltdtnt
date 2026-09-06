package dev.vitalstages.health;

import net.minecraft.nbt.CompoundTag;

/** Форматы 1/2 не имели этого compound: безопасные defaults, без потери старой физиологии. */
public final class TreatmentNbtCodec {
    private TreatmentNbtCodec() {}
    public static CompoundTag write(TreatmentState s) {
        CompoundTag n = new CompoundTag();
        n.putInt("painkillerTicks", s.painkillerTicks()); n.putInt("adrenalineTicks", s.adrenalineTicks());
        n.putInt("adrenalineCooldownTicks", s.adrenalineCooldownTicks()); n.putInt("donationCooldownTicks", s.donationCooldownTicks());
        n.putInt("chatCooldownTicks", s.chatCooldownTicks()); n.putBoolean("chatAllowed", s.chatAllowed());
        n.putString("previousPhrase", s.previousPhrase()); return n;
    }
    public static TreatmentState read(CompoundTag n) {
        return new TreatmentState(n.getInt("painkillerTicks"), n.getInt("adrenalineTicks"),
                n.getInt("adrenalineCooldownTicks"), n.getInt("donationCooldownTicks"),
                n.contains("chatCooldownTicks") ? n.getInt("chatCooldownTicks") : 900,
                !n.contains("chatAllowed") || n.getBoolean("chatAllowed"), n.getString("previousPhrase"));
    }
}
