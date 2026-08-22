package com.s4fmer.boozecraft.client;

import com.s4fmer.boozecraft.BoozeConfig;
import com.s4fmer.boozecraft.reg.BoozeEffects;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Purely cosmetic camera sway. It reads the vanilla synced mob effects, so it also
 * works when the server is a Bukkit hybrid and no custom packets can be trusted.
 */
public final class DrunkCamera {

	public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		if (!BoozeConfig.CAMERA_SWAY.get()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}

		double intensity = 0.0D;
		if (player.hasEffect(BoozeEffects.TIPSY)) {
			intensity = 0.2D;
		}
		if (player.hasEffect(BoozeEffects.HANGOVER)) {
			intensity = Math.max(intensity, 0.35D);
		}
		if (player.hasEffect(BoozeEffects.WITHDRAWAL)) {
			intensity = Math.max(intensity, 0.4D);
		}
		if (player.hasEffect(BoozeEffects.DRUNK)) {
			intensity = Math.max(intensity, 0.65D);
		}
		if (player.hasEffect(BoozeEffects.HEAVY_DRUNK)) {
			intensity = Math.max(intensity, 1.2D);
		}
		if (player.hasEffect(BoozeEffects.PASSED_OUT)) {
			intensity = 1.6D;
		}
		if (intensity <= 0.0D) {
			return;
		}

		double strength = intensity * BoozeConfig.CAMERA_SWAY_STRENGTH.get();
		double time = Util.getMillis() / 1000.0D;
		event.setRoll(event.getRoll() + (float) (Math.sin(time * 1.7D) * 5.5D * strength));
		event.setYaw(event.getYaw() + (float) (Math.sin(time * 0.9D) * 1.4D * strength));
		event.setPitch(event.getPitch() + (float) (Math.cos(time * 1.3D) * 0.9D * strength));
	}

	private DrunkCamera() {
	}
}
