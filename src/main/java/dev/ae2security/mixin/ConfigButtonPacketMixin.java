package dev.ae2security.mixin;

import appeng.core.network.serverbound.ConfigButtonPacket;
import appeng.menu.AEBaseMenu;
import dev.ae2security.security.SecurityAccess;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The packet cycles any setting of the open menu's target, whatever permission opened that menu. */
@Mixin(value = ConfigButtonPacket.class, remap = false)
public abstract class ConfigButtonPacketMixin {
    @Inject(method = "handleOnServer(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), cancellable = true)
    private void ae2security$configure(ServerPlayer player, CallbackInfo ci) {
        if (player.containerMenu instanceof AEBaseMenu menu && !SecurityAccess.configuring(menu)) ci.cancel();
    }
}
