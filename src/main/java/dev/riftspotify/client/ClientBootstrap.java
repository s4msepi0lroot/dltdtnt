package dev.riftspotify.client;

import dev.riftspotify.client.input.ModKeyMappings;
import dev.riftspotify.client.overlay.ClientEvents;
import dev.riftspotify.client.spotify.SpotifyClient;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientBootstrap {
    private static boolean registered;

    private ClientBootstrap() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ModKeyMappings.register();
        SpotifyClient.initialize();
        NeoForge.EVENT_BUS.register(ClientEvents.class);
    }
}
