package ru.s4fmer.badhabits.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.s4fmer.badhabits.BhConfig;
import ru.s4fmer.badhabits.addiction.AddictionLogic;
import ru.s4fmer.badhabits.addiction.AddictionManager;
import ru.s4fmer.badhabits.addiction.Meter;
import ru.s4fmer.badhabits.addiction.PlayerAddiction;
import ru.s4fmer.badhabits.addiction.Substance;
import ru.s4fmer.badhabits.command.BhCommands;
import ru.s4fmer.badhabits.network.StatusPayload;
import ru.s4fmer.badhabits.util.CoughHelper;

/**
 * Server-side glue: persistence, the one-second addiction tick, the chat cough and commands.
 * Nothing here touches client-only classes, so the same jar loads on dedicated, integrated and hybrid servers.
 */
public class BhEvents {

    // ------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        AddictionManager.attach(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        AddictionManager.detach();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BhCommands.register(event.getDispatcher());
    }

    // ----------------------------------------------------------------- tick

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();

        if (tick % 20 == 0) {
            boolean hud = BhConfig.HUD_ENABLED.get();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isAlive() && !player.isSpectator() && !player.isCreative()) {
                    AddictionLogic.tickPlayer(player, 1);
                }
                if (hud) {
                    sendStatus(player);
                }
            }
        }

        if (tick % 1200 == 0) {
            AddictionManager.saveIfDirty();
        }
    }

    /** One tiny packet per second per player: feeds the HUD bars. */
    private static void sendStatus(ServerPlayer player) {
        PlayerAddiction data = AddictionManager.getIfPresent(player.getUUID());
        if (data == null) {
            PacketDistributor.sendToPlayer(player, StatusPayload.empty());
            return;
        }
        PacketDistributor.sendToPlayer(player, StatusPayload.of(
                data.meter(Substance.NICOTINE), data.meter(Substance.NARCOTIC)));
    }

    // -------------------------------------------------------------- players

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerAddiction data = AddictionManager.getIfPresent(player.getUUID());
        if (data == null) {
            return;
        }
        for (Substance substance : Substance.values()) {
            Meter meter = data.meter(substance);
            if (meter.addiction > 0.0F) {
                player.sendSystemMessage(Component.translatable("badhabits.msg.login",
                                Component.translatable("badhabits.substance." + substance.key()),
                                AddictionLogic.fmt(meter.addiction))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        AddictionManager.saveIfDirty();
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (BhConfig.CLEAR_ON_DEATH.get()) {
            AddictionManager.clear(player.getUUID());
            return;
        }
        PlayerAddiction data = AddictionManager.getIfPresent(player.getUUID());
        if (data == null) {
            return;
        }
        // Death flushes the substance out of the body, but the habit stays.
        for (Substance substance : Substance.values()) {
            Meter meter = data.meter(substance);
            meter.dose = 0.0F;
            meter.withdrawalSeconds = 0;
            meter.stage = 0;
        }
        AddictionManager.markDirty();
    }

    // ----------------------------------------------------------------- chat

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (!BhConfig.CHAT_COUGH.get()) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }
        PlayerAddiction data = AddictionManager.getIfPresent(player.getUUID());
        if (data == null) {
            return;
        }
        Meter nicotine = data.meter(Substance.NICOTINE);
        if (nicotine.addiction < 1.0F) {
            return;
        }

        double min = BhConfig.COUGH_CHANCE_MIN.get();
        double max = BhConfig.COUGH_CHANCE_MAX.get();
        double ratio = Math.min(1.0D, nicotine.addiction / BhConfig.ADDICTION_MAX.get());
        double chance = min + (max - min) * ratio;
        if (nicotine.stage >= 2) {
            chance = Math.min(0.95D, chance * 1.8D);
        }
        if (player.getRandom().nextDouble() > chance) {
            return;
        }

        boolean heavy = nicotine.stage >= 2 || nicotine.addiction >= 60.0F;
        MutableComponent prefix = CoughHelper.text(heavy);
        CoughHelper.ambience(player, heavy);

        if (BhConfig.COUGH_REBROADCAST.get()) {
            // Hybrid compatibility mode: build the whole line ourselves.
            MinecraftServer server = player.getServer();
            if (server != null) {
                Component full = Component.literal("<" + player.getGameProfile().getName() + "> ")
                        .append(prefix)
                        .append(Component.literal(" "))
                        .append(event.getMessage());
                event.setCanceled(true);
                server.getPlayerList().broadcastSystemMessage(full, false);
            }
            return;
        }

        event.setMessage(Component.empty()
                .append(prefix)
                .append(Component.literal(" "))
                .append(event.getMessage()));
    }
}
