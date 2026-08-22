package com.s4fmer.boozecraft.client;

import com.s4fmer.boozecraft.BoozeCraft;
import com.s4fmer.boozecraft.reg.BoozeBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client only extras: the items standing on the bar counter and the drunk camera. */
@Mod(value = BoozeCraft.MODID, dist = Dist.CLIENT)
public class BoozeCraftClient {

	public BoozeCraftClient(IEventBus modBus, ModContainer container) {
		modBus.addListener(this::registerRenderers);
		NeoForge.EVENT_BUS.addListener(DrunkCamera::onComputeCameraAngles);
	}

	private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(BoozeBlockEntities.BAR_COUNTER.get(), BarCounterRenderer::new);
	}
}
