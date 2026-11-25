package dev.ae2security.mixin;

import appeng.core.network.serverbound.PartLeftClickPacket;
import dev.ae2security.security.SecurityHardware;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PartLeftClickPacket.class, remap = false)
public abstract class PartLeftClickPacketMixin {
    @Shadow public abstract BlockHitResult hitResult();

    @Inject(method = "handleOnServer", at = @At("HEAD"), cancellable = true)
    private void ae2security$reach(ServerPlayer player, CallbackInfo callback) {
        var pos = hitResult().getBlockPos();
        if (!SecurityHardware.loaded(player.level(), pos) || !player.canInteractWithBlock(pos, 1)) callback.cancel();
    }
}
