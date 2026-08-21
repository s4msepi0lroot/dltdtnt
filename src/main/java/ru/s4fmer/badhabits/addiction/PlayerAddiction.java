package ru.s4fmer.badhabits.addiction;

/** Per-player container for both meters. Serialized with Gson. */
public class PlayerAddiction {
    public Meter nicotine = new Meter();
    public Meter narcotic = new Meter();

    public Meter meter(Substance substance) {
        return substance == Substance.NICOTINE ? nicotine : narcotic;
    }

    /** Gson can leave fields null when the file was hand-edited. */
    public PlayerAddiction normalize() {
        if (nicotine == null) {
            nicotine = new Meter();
        }
        if (narcotic == null) {
            narcotic = new Meter();
        }
        return this;
    }

    public boolean isEmpty() {
        return normalize().nicotine.isEmpty() && narcotic.isEmpty();
    }

    public void reset() {
        normalize();
        nicotine.reset();
        narcotic.reset();
    }
}
