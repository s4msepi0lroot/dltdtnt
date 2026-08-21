package ru.s4fmer.badhabits.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import ru.s4fmer.badhabits.BadHabits;
import ru.s4fmer.badhabits.registry.ModMenus;

/** Client-only setup: binds the lab menu to its screen. */
@EventBusSubscriber(modid = BadHabits.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BhClientSetup {

    private BhClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SYNTH_LAB.get(), LabScreen::new);
    }
}
