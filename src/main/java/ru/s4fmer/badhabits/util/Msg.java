package ru.s4fmer.badhabits.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import ru.s4fmer.badhabits.BhConfig;

/** Small helper around translatable components. Everything is sent from the server. */
public final class Msg {
    private Msg() {
    }

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /** Action bar (above the hotbar) - never spams the chat history. */
    public static void bar(ServerPlayer player, Component text) {
        if (BhConfig.STATUS_MESSAGES.get()) {
            player.displayClientMessage(text, true);
        }
    }

    /** Normal chat line, used by commands and important warnings. */
    public static void chat(ServerPlayer player, Component text) {
        player.sendSystemMessage(text);
    }
}
