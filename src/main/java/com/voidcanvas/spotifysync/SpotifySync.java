package com.voidcanvas.spotifysync;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Mod entry point.
 *
 * <p>Spotify Sync is a purely client side mod: every feature lives in the
 * {@code client} package and is wired up through client-only event
 * subscribers, so the jar can safely be dropped on a server without doing
 * anything at all.</p>
 */
@Mod(SpotifySync.MOD_ID)
public final class SpotifySync {
    public static final String MOD_ID = "spotifysync";
    public static final String MOD_NAME = "Spotify Sync";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SpotifySync(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[{}] initialising (client-side only features)", MOD_NAME);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
