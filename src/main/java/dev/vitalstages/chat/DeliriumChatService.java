package dev.vitalstages.chat;

import com.mojang.logging.LogUtils;
import dev.vitalstages.VitalStages;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.event.DamageEventHandler;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Работает на сервере. Не вызывает sendChat, performCommand, chat-подписи или C2S-команды. */
@EventBusSubscriber(modid = VitalStages.MOD_ID)
public final class DeliriumChatService {
    private static volatile PhraseBook book = PhraseBook.empty();
    private static long nextGlobalTick;
    private DeliriumChatService() {}
    public record ReloadResult(boolean success, int phraseCount, String error) {}
    private static Path path() { return FMLPaths.CONFIGDIR.get().resolve(VitalStages.MOD_ID).resolve("delirium_phrases.json"); }
    @SubscribeEvent public static void start(ServerStartedEvent e) {
        nextGlobalTick = 0; book = PhraseBook.empty();
        ReloadResult result = reload();
        if (!result.success()) LogUtils.getLogger().warn("Vital Stages phrase JSON: {}", result.error());
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { book = PhraseBook.empty(); nextGlobalTick = 0; }
    public static ReloadResult reload() {
        try {
            Path p = path(); Files.createDirectories(p.getParent());
            try {
                Files.writeString(p, "{\n  \"version\": 1,\n  \"phrases\": []\n}\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (FileAlreadyExistsException ignored) { /* Никогда не затираем пользовательский файл. */ }
            byte[] bytes;
            // Ограничение при самом чтении защищает и от изменения размера файла между проверкой и чтением.
            try (var in = Files.newInputStream(p)) { bytes = in.readNBytes(JsonPhraseCodec.MAX_FILE_BYTES + 1); }
            PhraseBook next = JsonPhraseCodec.parse(bytes);
            book = next; // Только после полной проверки; при ошибке остаётся прежний исправный набор.
            return new ReloadResult(true, next.phrases().size(), "");
        } catch (IOException | IllegalArgumentException | IllegalStateException | SecurityException ex) {
            return new ReloadResult(false, book.phrases().size(), ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }
    public static boolean trySpeak(ServerPlayer speaker, boolean debugPreview) {
        PhraseBook snapshot = book;
        if (snapshot.phrases().isEmpty() || !speaker.isAlive() || !DamageEventHandler.eligible(speaker)
                || !HealthConfig.ENABLE_DELIRIUM.get() || !HealthConfig.CHAT_ENABLED.get()) return false;
        var d = HealthAttachments.get(speaker);
        if (!d.treatments().chatAllowed() || d.unconscious() || d.consciousness() <= 0) return false;
        var server = speaker.getServer(); if (server == null) return false;
        long now = server.overworld().getGameTime();
        if (!debugPreview && (d.consciousness() > HealthConfig.f(HealthConfig.CHAT_MAX_CONSCIOUSNESS)
                || d.treatments().chatCooldownTicks() > 0 || now < nextGlobalTick)) return false;
        String phrase = snapshot.choose(d.treatments().previousPhrase(), speaker.getRandom()::nextInt).orElseThrow();
        // Обычный вид <Ник> текст, без [Бред]. Оба аргумента — literal: ни ник, ни JSON не создают click/hover actions.
        // Это серверный system message, а не утверждение о наличии подлинной player-chat подписи.
        Component message = Component.translatable("chat.type.text",
                Component.literal(speaker.getGameProfile().getName()), Component.literal(phrase));
        double radius = HealthConfig.CHAT_RADIUS.get();
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer.level() == speaker.level() && (radius == 0 || viewer.distanceToSqr(speaker) <= radius * radius))
                viewer.sendSystemMessage(message);
        }
        int min = HealthConfig.CHAT_MIN_SECONDS.get(), max = Math.max(min, HealthConfig.CHAT_MAX_SECONDS.get());
        d.recordSpeech(phrase, (min + speaker.getRandom().nextInt(max - min + 1)) * 20);
        nextGlobalTick = now + 20; // Не более одной автоматической реплики в секунду на сервер.
        return true;
    }
}
