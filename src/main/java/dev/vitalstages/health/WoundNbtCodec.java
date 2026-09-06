package dev.vitalstages.health;

import net.minecraft.nbt.CompoundTag;
import java.util.Optional;
import java.util.UUID;

/** Сохраняет все старые имена ключей. В MVP-1 healingTicks отсутствовал: его default равен 0. */
public final class WoundNbtCodec {
    private WoundNbtCodec() {}
    public static CompoundTag write(Wound w) {
        CompoundTag n = new CompoundTag();
        n.putUUID("id", w.id()); n.putString("bodyPart", w.bodyPart().name()); n.putString("type", w.type().name());
        n.putFloat("severity", w.severity()); n.putBoolean("isBandaged", w.isBandaged());
        n.putBoolean("isSplinted", w.isSplinted()); n.putInt("infectionTimer", w.infectionTimer());
        n.putFloat("infectionRisk", w.infectionRisk()); n.putInt("healingTicks", w.healingTicks());
        n.putInt("antisepticTicks", w.antisepticTicks());
        return n;
    }
    public static Optional<Wound> read(CompoundTag n) {
        try {
            return Optional.of(new Wound(n.hasUUID("id") ? n.getUUID("id") : UUID.randomUUID(),
                    BodyPart.valueOf(n.getString("bodyPart")), Wound.Type.valueOf(n.getString("type")),
                    n.getFloat("severity"), n.getBoolean("isBandaged"), n.getBoolean("isSplinted"),
                    n.getInt("infectionTimer"), n.getFloat("infectionRisk"), n.getInt("healingTicks"), n.getInt("antisepticTicks")));
        } catch (IllegalArgumentException ex) { return Optional.empty(); }
    }
}
