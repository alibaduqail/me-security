package dev.ae2security.mixin;

import appeng.core.network.ServerboundPacket;
import appeng.menu.AEBaseMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.ae2security.security.SecurityAccess;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Runs inside AE2's enqueued server-thread callback, before any packet handler sees a stale menu. */
@Mixin(value = ServerboundPacket.class, remap = false)
public interface ServerboundPacketMixin {
    @WrapOperation(method = "lambda$handleOnServer$0", at = @At(value = "INVOKE",
            target = "Lappeng/core/network/ServerboundPacket;handleOnServer(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void ae2security$dispatch(ServerboundPacket packet, ServerPlayer player, Operation<Void> original) {
        if (!(player.containerMenu instanceof AEBaseMenu menu) || SecurityAccess.menu(menu)) original.call(packet, player);
    }
}
