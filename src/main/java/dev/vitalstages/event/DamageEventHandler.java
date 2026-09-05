package dev.vitalstages.event;

import dev.vitalstages.VitalStages;
import dev.vitalstages.config.HealthConfig;
import dev.vitalstages.health.BodyPart;
import dev.vitalstages.health.PlayerHealthData;
import dev.vitalstages.health.Wound;
import dev.vitalstages.network.HealthNetwork;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(modid = VitalStages.MOD_ID)
public final class DamageEventHandler {
    private DamageEventHandler() {}
    public static boolean eligible(Player p) { return !p.isCreative() && !p.isSpectator(); }
    public static boolean bypassesStages(DamageSource source) {
        // /kill и пустота — намеренные исключения, не бесконечное спасение вне мира.
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void preDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !eligible(player)
                || bypassesStages(event.getSource()) || HealthAttachments.get(player).finalizing()) return;
        float amount = event.getNewDamage();
        if (!Float.isFinite(amount) || amount <= 0) { event.setNewDamage(0); return; }
        // После брони/эффектов, но ДО absorption в NeoForge 1.21.1.
        // Конверсия до жёлтых сердец — явное правило баланса этого MVP.
        event.setNewDamage(amount * HealthConfig.f(HealthConfig.HEALTH_DAMAGE_FRACTION));
    }
    @SubscribeEvent
    public static void postDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !eligible(player)
                || bypassesStages(event.getSource()) || HealthAttachments.get(player).finalizing()) return;
        float actual = event.getNewDamage();
        // Щит, absorption и чужая нулевая модификация не создают «призрачные» раны.
        if (!Float.isFinite(actual) || actual <= 0) return;
        PlayerHealthData data = HealthAttachments.get(player);
        Wound.Type type = woundType(event.getSource(), actual);
        if (type != null) {
            BodyPart part = bodyPart(player, event.getSource());
            float severity = Math.min(Wound.MAX_SEVERITY, Math.max(0.05f, actual / 4));
            data.addImpact(Wound.fresh(part, type, severity), HealthConfig.f(HealthConfig.PAIN_PER_SEVERITY),
                    HealthConfig.f(HealthConfig.SHOCK_PER_SEVERITY));
            // Сильный физический удар может дополнительно сломать руку/ногу.
            // Дополнительный перелом не удваивает уже начисленный бюджет боли/шока.
            if (part.isLimb() && type != Wound.Type.FRACTURE && type != Wound.Type.BURN
                    && HealthConfig.ENABLE_FRACTURES.get()
                    && actual >= HealthConfig.f(HealthConfig.HEAVY_FRACTURE_THRESHOLD)
                    && player.getRandom().nextFloat() < HealthConfig.f(HealthConfig.HEAVY_FRACTURE_CHANCE)) {
                data.addImpact(Wound.fresh(part, Wound.Type.FRACTURE, severity), 0, 0);
            }
            TickHandler.recomputeBleeding(data);
            FractureEffects.refresh(player);
        }
        data.refreshVanillaHealth(player.getHealth(), player.getMaxHealth(), HealthConfig.f(HealthConfig.REVIVE_HEALTH));
        // При летальном ударе дождёмся vanilla-решения о тотеме, затем LivingDeathEvent.
        if (player.getHealth() > 0) HealthNetwork.sync(player);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !eligible(player)) return;
        PlayerHealthData data = HealthAttachments.get(player);
        if (data.finalizing()) {
            // Другая death-механика вправе отменить смерть. Не оставляем HP=0 у живой сущности.
            if (event.isCanceled() && player.getHealth() <= 0)
                player.setHealth(PlayerHealthData.vanillaFloor(player.getMaxHealth()));
            return;
        }
        if (event.isCanceled() || bypassesStages(event.getSource())) return;
        if ((HealthConfig.ENABLE_BLOOD_LOSS.get() && data.bloodLevel() <= 0)
                || (data.unconscious() && data.unconsciousTicks() >= HealthConfig.windowTicks())) return;
        event.setCanceled(true);
        data.enterCriticalTrauma(); // Повторный вызов не сбрасывает таймер.
        player.setHealth(PlayerHealthData.vanillaFloor(player.getMaxHealth()));
        player.stopUsingItem(); HealthNetwork.sync(player);
    }
    private static Wound.Type woundType(DamageSource source, float damage) {
        if (source.is(DamageTypeTags.IS_FIRE)) return Wound.Type.BURN;
        if (source.is(DamageTypeTags.IS_FALL))
            return HealthConfig.ENABLE_FRACTURES.get() && damage >= HealthConfig.f(HealthConfig.FRACTURE_DAMAGE_THRESHOLD)
                    ? Wound.Type.FRACTURE : Wound.Type.BRUISE;
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION)) return Wound.Type.CUT;
        // Асфиксия, голод и магия не равны открытой наружной ране.
        if (source.is(DamageTypes.DROWN) || source.is(DamageTypes.STARVE) || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.FREEZE) || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER)) return null;
        if (source.getEntity() instanceof LivingEntity attacker) {
            boolean sharp = !(attacker instanceof Player) || attacker.getMainHandItem().is(ItemTags.SWORDS)
                    || attacker.getMainHandItem().is(ItemTags.AXES);
            return sharp ? Wound.Type.CUT : Wound.Type.BRUISE;
        }
        return Wound.Type.BRUISE;
    }
    private static BodyPart bodyPart(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.IS_FALL))
            return player.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
        Vec3 position = source.getSourcePosition();
        if (source.is(DamageTypeTags.IS_PROJECTILE) && position != null) {
            // Приближение по высоте снаряда; bone-hitbox пока не реализован.
            double y = position.y - player.getY();
            if (y > player.getBbHeight() * 0.8) return BodyPart.HEAD;
            if (y > player.getBbHeight() * 0.4) return BodyPart.TORSO;
            return player.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
        }
        return switch (player.getRandom().nextInt(10)) {
            case 0 -> BodyPart.HEAD;
            case 1, 2, 3 -> BodyPart.TORSO;
            case 4 -> BodyPart.LEFT_ARM;
            case 5 -> BodyPart.RIGHT_ARM;
            case 6, 7 -> BodyPart.LEFT_LEG;
            default -> BodyPart.RIGHT_LEG;
        };
    }
}
