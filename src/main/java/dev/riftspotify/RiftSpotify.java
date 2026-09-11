package dev.riftspotify;

import dev.riftspotify.client.ClientBootstrap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;

@Mod(value = RiftSpotify.MOD_ID, dist = Dist.CLIENT)
public final class RiftSpotify {
    public static final String MOD_ID = "riftspotify";

    public RiftSpotify(IEventBus modEventBus, ModContainer modContainer) {
        ClientBootstrap.register();
    }
}
