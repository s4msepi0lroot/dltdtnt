package ru.s4fmer.badhabits.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import ru.s4fmer.badhabits.addiction.AddictionLogic;
import ru.s4fmer.badhabits.addiction.AddictionManager;
import ru.s4fmer.badhabits.addiction.Meter;
import ru.s4fmer.badhabits.addiction.PlayerAddiction;
import ru.s4fmer.badhabits.addiction.Substance;

/**
 * /badhabits status [player]
 * /badhabits set &lt;player&gt; &lt;nicotine|narcotic&gt; &lt;addiction|dose&gt; &lt;value&gt;
 * /badhabits clear &lt;player&gt;
 *
 * <p>Everything except "status" for yourself requires permission level 2, so it works with
 * vanilla ops and with LuckPerms-style plugins on hybrid cores.</p>
 */
public final class BhCommands {

    private BhCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("badhabits");

        // ---- status ----
        var status = Commands.literal("status")
                .executes(ctx -> status(ctx.getSource(), ctx.getSource().getPlayerOrException()));
        status.then(Commands.argument("target", EntityArgument.player())
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> status(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))));
        root.then(status);

        // ---- clear ----
        var clear = Commands.literal("clear").requires(source -> source.hasPermission(2));
        clear.then(Commands.argument("target", EntityArgument.player()).executes(ctx -> {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            AddictionManager.clear(target.getUUID());
            ctx.getSource().sendSuccess(
                    () -> Component.translatable("badhabits.cmd.cleared", target.getDisplayName())
                            .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }));
        root.then(clear);

        // ---- set ----
        var set = Commands.literal("set").requires(source -> source.hasPermission(2));
        var target = Commands.argument("target", EntityArgument.player());
        for (Substance substance : Substance.values()) {
            var substanceNode = Commands.literal(substance.key());
            substanceNode.then(Commands.literal("addiction")
                    .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 1000.0F))
                            .executes(ctx -> set(ctx, substance, true))));
            substanceNode.then(Commands.literal("dose")
                    .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 1000.0F))
                            .executes(ctx -> set(ctx, substance, false))));
            target.then(substanceNode);
        }
        set.then(target);
        root.then(set);

        dispatcher.register(root);
    }

    private static int status(CommandSourceStack source, ServerPlayer player) {
        PlayerAddiction data = AddictionManager.get(player);
        source.sendSuccess(() -> Component.translatable("badhabits.cmd.status.header", player.getDisplayName())
                .withStyle(ChatFormatting.GOLD), false);
        for (Substance substance : Substance.values()) {
            Meter meter = data.meter(substance);
            source.sendSuccess(() -> Component.translatable("badhabits.cmd.status.line",
                    Component.translatable("badhabits.substance." + substance.key()),
                    AddictionLogic.fmt(meter.addiction),
                    AddictionLogic.fmt(meter.dose),
                    meter.stage,
                    meter.withdrawalSeconds), false);
        }
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, Substance substance, boolean addiction)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        float value = FloatArgumentType.getFloat(ctx, "value");
        Meter meter = AddictionManager.get(target).meter(substance);
        if (addiction) {
            meter.addiction = value;
        } else {
            meter.dose = value;
            if (value > 0.0F) {
                meter.lastDose = value;
            }
        }
        meter.withdrawalSeconds = 0;
        meter.stage = 0;
        AddictionManager.markDirty();

        ctx.getSource().sendSuccess(() -> Component.translatable("badhabits.cmd.set",
                target.getDisplayName(),
                Component.translatable("badhabits.substance." + substance.key()),
                addiction ? "addiction" : "dose",
                AddictionLogic.fmt(value)).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }
}
