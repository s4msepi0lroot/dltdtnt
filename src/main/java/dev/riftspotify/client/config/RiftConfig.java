package dev.riftspotify.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RiftConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Minecraft.getInstance().gameDirectory.toPath().resolve("config/rift-spotify.json");
    private static RiftConfig instance;

    public String clientId = "";
    public boolean showMiniPlayer = true;
    public boolean showHudLyrics = false;
    public boolean showWorldLyrics = false;
    public boolean autoFetchLyrics = true;
    public int refreshSeconds = 5;

    public static synchronized RiftConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // The overlay stays usable if config persistence is unavailable.
        }
    }

    private static RiftConfig load() {
        try {
            if (Files.exists(FILE)) {
                RiftConfig config = GSON.fromJson(Files.readString(FILE), RiftConfig.class);
                if (config != null) return config;
            }
        } catch (Exception ignored) {}
        return new RiftConfig();
    }
}
