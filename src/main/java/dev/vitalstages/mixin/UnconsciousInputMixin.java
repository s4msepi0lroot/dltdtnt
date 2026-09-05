package dev.vitalstages.mixin;
import dev.vitalstages.event.DamageEventHandler;
import dev.vitalstages.registry.HealthAttachments;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Серверный gate: модифицированный клиент не может просто включить атаки/самолечение. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class UnconsciousInputMixin {
    @Shadow public ServerPlayer player;
    @Unique private boolean vitalstages$locked() {
        // HEAD также исполняется на Netty до PacketUtils: attachment читаем ТОЛЬКО на серверном потоке.
        return player.getServer() != null && player.getServer().isSameThread() && player.isAlive()
                && DamageEventHandler.eligible(player) && HealthAttachments.get(player).unconscious();
    }
    @ModifyVariable(method = "handleMovePlayer", at = @At("HEAD"), argsOnly = true)
    private ServerboundMovePlayerPacket vitalstages$filterMovement(ServerboundMovePlayerPacket packet) {
        if (!vitalstages$locked() || player.isPassenger()) return packet;
        // Не отменяем обработчик целиком: падение и vanilla-проверки продолжают работать.
        // Горизонтальный ввод, прыжок и поворот запрещены; серверные teleport не перехватываются.
        // Вертикальные пакеты остаются под обычными vanilla-проверками; это не anti-cheat.
        return new ServerboundMovePlayerPacket.PosRot(player.getX(), Math.min(player.getY(), packet.getY(player.getY())),
                player.getZ(), player.getYRot(), player.getXRot(), packet.isOnGround());
    }
    @Inject(method = {"handlePlayerAction", "handleInteract", "handleUseItem", "handleUseItemOn",
            "handleContainerButtonClick", "handlePlaceRecipe", "handlePlayerCommand", "handlePlayerInput",
            "handleMoveVehicle", "handlePaddleBoat", "handlePickItem", "handlePlayerAbilities"},
            at = @At("HEAD"), cancellable = true)
    private void vitalstages$blockActions(CallbackInfo ci) { if (vitalstages$locked()) ci.cancel(); }
    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void vitalstages$blockInventory(CallbackInfo ci) {
        if (!vitalstages$locked()) return;
        player.containerMenu.broadcastFullState(); ci.cancel();
    }
    // KeepAlive, чат, команды, teleport-confirm не блокируются: игрок может попросить помощи/выйти.
}
