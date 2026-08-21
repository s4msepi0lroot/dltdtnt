package ru.s4fmer.badhabits.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import ru.s4fmer.badhabits.BadHabits;

/**
 * One clientbound payload: the addiction status used by the HUD.
 *
 * <p>The payload is marked optional, so a vanilla-ish client (or a client without the mod) can still
 * join a server running Bad Habits - it simply gets no HUD.</p>
 */
@EventBusSubscriber(modid = BadHabits.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class BhNetwork {

    private BhNetwork() {
    }

    @SubscribeEvent
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(
                StatusPayload.TYPE,
                StatusPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> BhStatusHolder.accept(payload)));
    }
}
