package dev.vitalstages;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.config.HudConfig;
import dev.vitalstages.network.HealthNetwork;
import dev.vitalstages.registry.HealthAttachments;
import dev.vitalstages.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(VitalStages.MOD_ID)
public final class VitalStages {
    public static final String MOD_ID = "vitalstages";
    public VitalStages(IEventBus modBus, ModContainer container) {
        HealthAttachments.TYPES.register(modBus); ModItems.ITEMS.register(modBus);
        modBus.addListener(HealthAttachments::registerCapabilities);
        modBus.addListener(HealthNetwork::register);
        container.registerConfig(ModConfig.Type.SERVER, HealthConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, HudConfig.SPEC);
    }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }
}
