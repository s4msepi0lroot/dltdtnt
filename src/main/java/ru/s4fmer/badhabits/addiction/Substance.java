package ru.s4fmer.badhabits.addiction;

/** Two independent addiction tracks. */
public enum Substance {
    NICOTINE("nicotine"),
    NARCOTIC("narcotic");

    private final String key;

    Substance(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
