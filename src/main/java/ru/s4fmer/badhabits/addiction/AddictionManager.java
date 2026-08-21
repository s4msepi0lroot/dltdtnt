package ru.s4fmer.badhabits.addiction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import ru.s4fmer.badhabits.BadHabits;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side storage of addiction data.
 *
 * <p>Deliberately does NOT use capabilities / data attachments / NBT patches: a plain JSON file inside the
 * world folder works identically on vanilla NeoForge servers and on hybrid cores (Youer, Mohist, Arclight,
 * Magma), survives death/respawn and dimension changes, and can be edited by admins by hand.</p>
 */
public final class AddictionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, PlayerAddiction> DATA = new ConcurrentHashMap<>();

    private static Path file;
    private static volatile boolean dirty;

    private AddictionManager() {
    }

    public static void attach(MinecraftServer server) {
        DATA.clear();
        dirty = false;
        try {
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("badhabits");
            Files.createDirectories(dir);
            file = dir.resolve("addiction.json");
            if (Files.exists(file)) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                Type type = new TypeToken<Map<String, PlayerAddiction>>() {
                }.getType();
                Map<String, PlayerAddiction> loaded = GSON.fromJson(json, type);
                if (loaded != null) {
                    for (Map.Entry<String, PlayerAddiction> entry : loaded.entrySet()) {
                        try {
                            PlayerAddiction value = entry.getValue();
                            DATA.put(UUID.fromString(entry.getKey()),
                                    value == null ? new PlayerAddiction() : value.normalize());
                        } catch (IllegalArgumentException badUuid) {
                            BadHabits.LOGGER.warn("[BadHabits] skipping bad UUID key in addiction.json: {}", entry.getKey());
                        }
                    }
                }
            }
            BadHabits.LOGGER.info("[BadHabits] addiction data loaded for {} player(s)", DATA.size());
        } catch (Exception e) {
            file = null;
            BadHabits.LOGGER.error("[BadHabits] failed to load addiction data", e);
        }
    }

    public static void detach() {
        saveIfDirty();
        DATA.clear();
        file = null;
    }

    public static PlayerAddiction get(UUID id) {
        return DATA.computeIfAbsent(id, key -> new PlayerAddiction()).normalize();
    }

    public static PlayerAddiction get(Player player) {
        return get(player.getUUID());
    }

    /** Does not create an entry - use it in hot paths such as the tick loop. */
    public static PlayerAddiction getIfPresent(UUID id) {
        PlayerAddiction data = DATA.get(id);
        return data == null ? null : data.normalize();
    }

    public static void clear(UUID id) {
        DATA.remove(id);
        markDirty();
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void saveIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        save();
    }

    public static void save() {
        Path target = file;
        if (target == null) {
            return;
        }
        try {
            Map<String, PlayerAddiction> out = new HashMap<>();
            for (Map.Entry<UUID, PlayerAddiction> entry : DATA.entrySet()) {
                PlayerAddiction value = entry.getValue();
                if (value != null && !value.isEmpty()) {
                    out.put(entry.getKey().toString(), value);
                }
            }
            Files.writeString(target, GSON.toJson(out), StandardCharsets.UTF_8);
        } catch (Exception e) {
            BadHabits.LOGGER.error("[BadHabits] failed to save addiction data", e);
        }
    }
}
