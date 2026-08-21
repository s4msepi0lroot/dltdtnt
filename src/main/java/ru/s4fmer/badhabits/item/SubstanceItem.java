package ru.s4fmer.badhabits.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import ru.s4fmer.badhabits.BhConfig;
import ru.s4fmer.badhabits.addiction.AddictionLogic;
import ru.s4fmer.badhabits.addiction.Substance;
import ru.s4fmer.badhabits.util.CoughHelper;
import ru.s4fmer.badhabits.util.Msg;

import java.util.List;

/**
 * Every consumable of the mod (cigarettes and the fictional synthetics) is an instance of this class.
 *
 * <p>Flow: right click -> tool check -> use animation -> {@link #finishUsingItem} applies the dose,
 * spends the tool durability and grants the effects. All gameplay happens on the server; the client only
 * plays the animation, so the item works in multiplayer and on hybrid cores without custom packets.</p>
 */
public class SubstanceItem extends Item {

    /** Method of intake: decides which tool is needed, which animation, particles and sounds are used. */
    public enum Style {
        SMOKE(UseTool.LIGHTER, UseAnim.EAT),
        INHALE(UseTool.GLASS_TUBE, UseAnim.EAT),
        INJECT(UseTool.SYRINGE, UseAnim.DRINK),
        SWALLOW(UseTool.NONE, UseAnim.EAT);

        private final UseTool tool;
        private final UseAnim anim;

        Style(UseTool tool, UseAnim anim) {
            this.tool = tool;
            this.anim = anim;
        }

        public UseTool tool() {
            return tool;
        }

        public UseAnim anim() {
            return anim;
        }

        public ParticleOptions particle() {
            switch (this) {
                case SMOKE:
                    return ParticleTypes.CAMPFIRE_COSY_SMOKE;
                case INHALE:
                    return ParticleTypes.PORTAL;
                case INJECT:
                    return ParticleTypes.CRIT;
                default:
                    return ParticleTypes.EFFECT;
            }
        }

        public SoundEvent startSound() {
            switch (this) {
                case SMOKE:
                    return SoundEvents.FLINTANDSTEEL_USE;
                case INJECT:
                    return SoundEvents.BOTTLE_FILL;
                default:
                    return SoundEvents.GENERIC_EAT;
            }
        }

        public SoundEvent finishSound() {
            switch (this) {
                case SMOKE:
                    return SoundEvents.FIRE_EXTINGUISH;
                case INJECT:
                    return SoundEvents.GENERIC_DRINK;
                default:
                    return SoundEvents.GENERIC_DRINK;
            }
        }
    }

    private final Substance substance;
    private final Style style;
    private final float dose;
    private final float addictionGain;
    private final int useTicks;
    private final int toolDamage;
    private final List<EffectSpec> effects;
    private final boolean canCough;
    private final boolean jitter;

    public SubstanceItem(Properties properties, Substance substance, Style style, float dose, float addictionGain,
                         int useTicks, int toolDamage, List<EffectSpec> effects, boolean canCough, boolean jitter) {
        super(properties);
        this.substance = substance;
        this.style = style;
        this.dose = dose;
        this.addictionGain = addictionGain;
        this.useTicks = useTicks;
        this.toolDamage = toolDamage;
        this.effects = effects;
        this.canCough = canCough;
        this.jitter = jitter;
    }

    public Substance substance() {
        return substance;
    }

    public float dose() {
        return dose;
    }

    // ------------------------------------------------------------- using it

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        UseTool tool = style.tool();

        if (!ToolHelper.has(player, tool)) {
            if (player instanceof ServerPlayer serverPlayer) {
                Msg.bar(serverPlayer, Component.translatable(tool.errorKey()).withStyle(ChatFormatting.RED));
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    style.startSound(), SoundSource.PLAYERS, 0.7F, 1.0F);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return useTicks;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return style.anim();
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingTicks) {
        if (level instanceof ServerLevel serverLevel && remainingTicks % 4 == 0) {
            serverLevel.sendParticles(style.particle(),
                    living.getX(), living.getEyeY() - 0.15D, living.getZ(),
                    3, 0.15D, 0.05D, 0.15D, 0.01D);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        if (!(living instanceof Player player)) {
            return stack;
        }

        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            UseTool tool = style.tool();
            ItemStack toolStack = ToolHelper.find(player, tool);
            if (tool != UseTool.NONE && toolStack.isEmpty()) {
                // The tool was lost mid-animation: nothing happens, the item is not consumed.
                Msg.bar(serverPlayer, Component.translatable(tool.errorKey()).withStyle(ChatFormatting.RED));
                return stack;
            }
            if (!toolStack.isEmpty()) {
                ToolHelper.damage(serverPlayer, toolStack, toolDamage);
            }

            float toleranceFactor = AddictionLogic.consume(serverPlayer, substance, dose, addictionGain);
            EffectSpec.applyAll(effects, serverPlayer, toleranceFactor);

            serverLevel.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    style.finishSound(), SoundSource.PLAYERS, 0.7F, 1.0F);
            serverLevel.sendParticles(style.particle(),
                    serverPlayer.getX(), serverPlayer.getEyeY(), serverPlayer.getZ(),
                    12, 0.25D, 0.15D, 0.25D, 0.02D);

            if (jitter) {
                // "Void Dust": a short reality glitch, teleports the player a couple of blocks away.
                double x = serverPlayer.getX() + (serverPlayer.getRandom().nextDouble() - 0.5D) * 8.0D;
                double y = serverPlayer.getY() + (serverPlayer.getRandom().nextInt(5) - 2);
                double z = serverPlayer.getZ() + (serverPlayer.getRandom().nextDouble() - 0.5D) * 8.0D;
                if (serverPlayer.getRandom().nextFloat() < 0.35F) {
                    serverPlayer.randomTeleport(x, y, z, true);
                }
            }

            if (canCough && serverPlayer.getRandom().nextDouble() < BhConfig.COUGH_ON_SMOKE_CHANCE.get()) {
                CoughHelper.cough(serverPlayer, false);
            }
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }

    // ------------------------------------------------------------- tooltips

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("badhabits.tip.style." + style.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("badhabits.tip.substance." + substance.key())
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("badhabits.tip.dose", AddictionLogic.fmt(dose))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("badhabits.tip.addiction", AddictionLogic.fmt(addictionGain))
                .withStyle(ChatFormatting.GRAY));
        if (style.tool() != UseTool.NONE) {
            tooltip.add(Component.translatable(style.tool().tipKey()).withStyle(ChatFormatting.YELLOW));
        }
        if (effects.isEmpty()) {
            tooltip.add(Component.translatable("badhabits.tip.no_effects").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
