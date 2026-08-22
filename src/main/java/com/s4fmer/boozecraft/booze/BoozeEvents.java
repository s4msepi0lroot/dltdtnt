package com.s4fmer.boozecraft.booze;

import com.s4fmer.boozecraft.cmd.BoozeCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Game event hooks. Registered on the NeoForge event bus from the main class. */
public class BoozeEvents {

	@SubscribeEvent
	public void onPlayerTick(PlayerTickEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			BoozeManager.tick(player);
			DrunkEvents.tick(player);
		}
	}

	@SubscribeEvent
	public void onServerStarted(ServerStartedEvent event) {
		BoozeManager.load(event.getServer());
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		BoozeManager.shutdown();
	}

	@SubscribeEvent
	public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		BoozeManager.save();
	}

	@SubscribeEvent
	public void onRegisterCommands(RegisterCommandsEvent event) {
		BoozeCommand.register(event.getDispatcher());
	}

	@SubscribeEvent
	public void onUseFinish(LivingEntityUseItemEvent.Finish event) {
		if (event.getEntity() instanceof ServerPlayer player && event.getItem().is(Items.MILK_BUCKET)) {
			BoozeManager.milk(player);
		}
	}
}
