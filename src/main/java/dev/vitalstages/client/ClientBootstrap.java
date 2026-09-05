package dev.vitalstages.client;
import dev.vitalstages.VitalStages;
import dev.vitalstages.network.HealthNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
@EventBusSubscriber(modid = VitalStages.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientBootstrap {
    private ClientBootstrap() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) {
        HealthNetwork.installClientReceiver(ClientHealthState::accept);
    }
}
