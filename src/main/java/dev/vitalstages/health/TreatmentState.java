package dev.vitalstages.health;

/** Неизменяемые таймеры: только online game ticks, без System.currentTimeMillis(). */
public record TreatmentState(int painkillerTicks, int adrenalineTicks, int adrenalineCooldownTicks,
        int donationCooldownTicks, int chatCooldownTicks, boolean chatAllowed, String previousPhrase) {
    public TreatmentState {
        painkillerTicks = bound(painkillerTicks); adrenalineTicks = bound(adrenalineTicks);
        adrenalineCooldownTicks = bound(adrenalineCooldownTicks); donationCooldownTicks = bound(donationCooldownTicks);
        chatCooldownTicks = bound(chatCooldownTicks);
        // Это только ключ сравнения; никогда не является источником отправляемого текста.
        if (previousPhrase == null || previousPhrase.length() > 320) previousPhrase = "";
    }
    private static int bound(int v) { return Math.max(0, Math.min(Physiology.MAX_TIMER, v)); }
    private static int down(int v) { return Math.max(0, v - 1); }
    public static TreatmentState fresh() { return new TreatmentState(0, 0, 0, 0, 900, true, ""); }
    public TreatmentState tick() {
        if (painkillerTicks == 0 && adrenalineTicks == 0 && adrenalineCooldownTicks == 0
                && donationCooldownTicks == 0 && chatCooldownTicks == 0) return this;
        return new TreatmentState(down(painkillerTicks), down(adrenalineTicks), down(adrenalineCooldownTicks),
                down(donationCooldownTicks), down(chatCooldownTicks), chatAllowed, previousPhrase);
    }
    public TreatmentState painkiller(int ticks) {
        return new TreatmentState(ticks, adrenalineTicks, adrenalineCooldownTicks, donationCooldownTicks,
                chatCooldownTicks, chatAllowed, previousPhrase);
    }
    public TreatmentState adrenaline(int ticks, int cooldown) {
        return new TreatmentState(painkillerTicks, ticks, Math.max(ticks, cooldown), donationCooldownTicks,
                chatCooldownTicks, chatAllowed, previousPhrase);
    }
    public TreatmentState donation(int cooldown) {
        return new TreatmentState(painkillerTicks, adrenalineTicks, adrenalineCooldownTicks, cooldown,
                chatCooldownTicks, chatAllowed, previousPhrase);
    }
    public TreatmentState allowChat(boolean allowed) {
        return new TreatmentState(painkillerTicks, adrenalineTicks, adrenalineCooldownTicks, donationCooldownTicks,
                chatCooldownTicks, allowed, previousPhrase);
    }
    public TreatmentState afterSpeech(String phrase, int cooldown) {
        return new TreatmentState(painkillerTicks, adrenalineTicks, adrenalineCooldownTicks, donationCooldownTicks,
                cooldown, chatAllowed, phrase);
    }
    public TreatmentState newLife() {
        // Возрождение не сбрасывает opt-out или ограничения донорства/инъекций/реплик.
        return new TreatmentState(0, 0, adrenalineCooldownTicks, donationCooldownTicks,
                chatCooldownTicks, chatAllowed, previousPhrase);
    }
    public int activeFlags() { return (painkillerTicks > 0 ? 1 : 0) | (adrenalineTicks > 0 ? 2 : 0); }
}
