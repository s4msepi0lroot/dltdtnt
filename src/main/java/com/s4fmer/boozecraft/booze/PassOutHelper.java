package com.s4fmer.boozecraft.booze;

import java.lang.reflect.Method;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * Optional "lying on the floor" visual for servers without GSit.
 * Uses reflection so a missing method can never break the build or the game.
 */
public final class PassOutHelper {

	private static boolean resolved;
	private static Method setForcedPose;

	public static void forceLay(Player player, boolean lay) {
		try {
			if (!resolved) {
				resolved = true;
				for (Method m : Player.class.getMethods()) {
					if (m.getName().equals("setForcedPose") && m.getParameterCount() == 1) {
						setForcedPose = m;
						break;
					}
				}
			}
			if (setForcedPose != null) {
				setForcedPose.invoke(player, lay ? Pose.SLEEPING : null);
			}
		} catch (Throwable ignored) {
			// never let a cosmetic fallback break gameplay
		}
	}

	private PassOutHelper() {
	}
}
