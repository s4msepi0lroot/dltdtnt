package ru.s4fmer.badhabits.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import ru.s4fmer.badhabits.BhConfig;

/** Cough sound / particles / chat prefix. */
public final class CoughHelper {
    private CoughHelper() {
    }

    /** Text used as the chat prefix and as the action bar hint. */
    public static MutableComponent text(boolean heavy) {
        String custom = heavy ? BhConfig.COUGH_TEXT_HEAVY.get() : BhConfig.COUGH_TEXT_LIGHT.get();
        MutableComponent component;
        if (custom == null || custom.trim().isEmpty()) {
            component = Component.translatable(heavy ? "badhabits.cough.heavy" : "badhabits.cough.light");
        } else {
            component = Component.literal(custom);
        }
        return component.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    }

    /** Sound + smoke puff, visible to everyone nearby. */
    public static void ambience(ServerPlayer player, boolean heavy) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_HURT, SoundSource.PLAYERS, heavy ? 0.7F : 0.4F, heavy ? 0.7F : 1.15F);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                player.getX(), player.getEyeY() - 0.1D, player.getZ(),
                heavy ? 10 : 4, 0.2D, 0.1D, 0.2D, 0.01D);
    }

    /** Full cough: sound, particles, action bar text and (when heavy) a dizzy spell. */
    public static void cough(ServerPlayer player, boolean heavy) {
        ambience(player, heavy);
        Msg.bar(player, text(heavy));
        if (heavy) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, true, false));
        }
    }
}
