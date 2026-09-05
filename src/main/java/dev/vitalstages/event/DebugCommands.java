package dev.vitalstages.event;

import dev.vitalstages.VitalStages;
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

/** Только OP level 2. Нужны для повторяемого ручного QA, не доступны обычному клиенту. */
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
            p.setData(HealthAttachments.HEALTH, new PlayerHealthData()); p.setHealth(p.getMaxHealth());
            HealthAttachments.get(p).refreshVanillaHealth(p.getHealth(), p.getMaxHealth(), HealthConfig.f(HealthConfig.REVIVE_HEALTH));
            FractureEffects.refresh(p); HealthNetwork.sync(p);
            ctx.getSource().sendSuccess(() -> Component.translatable("vitalstages.debug.reset", p.getDisplayName()), false);
            return 1;
        }));
        var debug = Commands.literal("debug").then(Commands.literal("fracture").then(target)).then(reset);
        e.getDispatcher().register(Commands.literal("vitalstages").requires(s -> s.hasPermission(2)).then(debug));
    }
}
