package dev.vitalstages.event;

import dev.vitalstages.VitalStages;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.FractureProfile;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class FractureEffects {
    private static final ResourceLocation LEGS = VitalStages.id("fractured_legs");
    private static final ResourceLocation ARMS_ATTACK = VitalStages.id("fractured_arms_attack");
    private static final ResourceLocation ARMS_MINING = VitalStages.id("fractured_arms_mining");
    private FractureEffects() {}
    public static void refresh(ServerPlayer p) {
        var d = HealthAttachments.get(p);
        var profile = FractureProfile.calculate(d.fracturedMask(), d.splintedMask(),
                DamageEventHandler.eligible(p) && HealthConfig.ENABLE_FRACTURES.get(),
                HealthConfig.LEG_MOVEMENT_FACTOR.get(), HealthConfig.ARM_ATTACK_FACTOR.get(), HealthConfig.ARM_MINING_FACTOR.get());
        apply(p, Attributes.MOVEMENT_SPEED, LEGS, profile.movement());
        apply(p, Attributes.ATTACK_SPEED, ARMS_ATTACK, profile.attackSpeed());
        apply(p, Attributes.BLOCK_BREAK_SPEED, ARMS_MINING, profile.miningSpeed());
    }
    private static void apply(ServerPlayer p, Holder<Attribute> key, ResourceLocation id, double factor) {
        AttributeInstance attribute = p.getAttribute(key);
        if (attribute == null) return; // Совместимость с нестандартным набором атрибутов.
        AttributeModifier old = attribute.getModifier(id);
        double amount = factor - 1;
        if (Math.abs(amount) < 1e-8) {
            if (old != null) attribute.removeModifier(id);
            return;
        }
        if (old != null && Math.abs(old.amount() - amount) < 1e-8
                && old.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) return;
        if (old != null) attribute.removeModifier(id);
        // Стабильные ID и transient исключают накопление после login/Clone/dimension.
        attribute.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
