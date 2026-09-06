package dev.vitalstages.event;

import dev.vitalstages.VitalStages;
import dev.vitalstages.chat.DeliriumChatService;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.BodyPart;
import dev.vitalstages.health.PlayerHealthData;
import dev.vitalstages.health.Wound;
import dev.vitalstages.network.HealthNetwork;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** OP level 2 для debug/reload. Только личное включение/отключение реплик доступно всем. */
@EventBusSubscriber(modid = VitalStages.MOD_ID)
public final class DebugCommands {
    private DebugCommands() {}
    @SubscribeEvent public static void register(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
        var target = Commands.argument("player", EntityArgument.player());
        for (BodyPart part : BodyPart.values()) if (part.isLimb()) {
            target.then(Commands.literal(part.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx -> {
                ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                var d = HealthAttachments.get(p);
                d.addImpact(Wound.fresh(part, Wound.Type.FRACTURE, 2), 0, 0);
                FractureEffects.refresh(p); HealthNetwork.sync(p);
                ctx.getSource().sendSuccess(() -> Component.translatable("vitalstages.debug.fracture", p.getDisplayName()), false);
                return 1;
            }));
        }
        var reset = Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
            var fresh = new PlayerHealthData();
            fresh.allowDeliriumChat(HealthAttachments.get(p).treatments().chatAllowed());
            p.setData(HealthAttachments.HEALTH, fresh); p.setHealth(p.getMaxHealth());
            HealthAttachments.get(p).refreshVanillaHealth(p.getHealth(), p.getMaxHealth(), HealthConfig.f(HealthConfig.REVIVE_HEALTH));
            FractureEffects.refresh(p); HealthNetwork.sync(p);
            ctx.getSource().sendSuccess(() -> Component.translatable("vitalstages.debug.reset", p.getDisplayName()), false);
            return 1;
        }));
        var preview = Commands.literal("phrase").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
            boolean sent = DeliriumChatService.trySpeak(EntityArgument.getPlayer(ctx, "player"), true);
            if (!sent) ctx.getSource().sendFailure(Component.translatable("vitalstages.phrases.preview_failed"));
            return sent ? 1 : 0;
        }));
        var syncope = Commands.literal("syncope").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
            var fresh = new PlayerHealthData(); fresh.allowDeliriumChat(HealthAttachments.get(p).treatments().chatAllowed());
            fresh.accept(new dev.vitalstages.health.Physiology.State(25, 80, 0, true, 0, false));
            p.setData(HealthAttachments.HEALTH, fresh); p.setHealth(p.getMaxHealth());
            fresh.refreshVanillaHealth(p.getHealth(), p.getMaxHealth(), HealthConfig.f(HealthConfig.REVIVE_HEALTH));
            TickHandler.recomputeBleeding(fresh); FractureEffects.refresh(p); HealthNetwork.sync(p);
            ctx.getSource().sendSuccess(() -> Component.translatable("vitalstages.debug.syncope", p.getDisplayName()), false);
            return 1;
        }));
        var cut = Commands.literal("cut").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
            HealthAttachments.get(p).addImpact(Wound.fresh(BodyPart.LEFT_ARM, Wound.Type.CUT, 2), 14, 8);
            TickHandler.recomputeBleeding(HealthAttachments.get(p)); HealthNetwork.sync(p);
            ctx.getSource().sendSuccess(() -> Component.translatable("vitalstages.debug.cut", p.getDisplayName()), false);
            return 1;
        }));
        var debug = Commands.literal("debug").requires(s -> s.hasPermission(2))
                .then(Commands.literal("fracture").then(target)).then(reset).then(preview).then(syncope).then(cut);
        var reload = Commands.literal("reloadphrases").requires(s -> s.hasPermission(2)).executes(ctx -> {
            var result = DeliriumChatService.reload();
            if (result.success()) ctx.getSource().sendSuccess(
                    () -> Component.translatable("vitalstages.phrases.reloaded", result.phraseCount()), false);
            else ctx.getSource().sendFailure(Component.translatable("vitalstages.phrases.reload_failed", result.error()));
            return result.success() ? 1 : 0;
        });
        var chat = Commands.literal("deliriumchat");
        for (boolean allowed : new boolean[]{true, false}) {
            chat.then(Commands.literal(allowed ? "on" : "off").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                HealthAttachments.get(p).allowDeliriumChat(allowed);
                ctx.getSource().sendSuccess(() -> Component.translatable(allowed
                        ? "vitalstages.phrases.allowed" : "vitalstages.phrases.disabled"), false);
                return 1;
            }));
        }
        e.getDispatcher().register(Commands.literal("vitalstages").then(debug).then(reload).then(chat));
    }
}
