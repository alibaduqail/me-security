package dev.ae2security.mixin;

import appeng.api.networking.IGrid;
import dev.ae2security.security.AE2wtlibSecurityPatched;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "de.mari_023.ae2wtlib.wct.CraftingTerminalHandler", remap = false)
public abstract class AE2wtlibCraftingTerminalHandlerMixin implements AE2wtlibSecurityPatched {
    @Shadow public abstract boolean inRange();

    @Inject(method = "getTargetGrid", at = @At("HEAD"), cancellable = true)
    private void ae2security$range(CallbackInfoReturnable<IGrid> callback) {
        if (!inRange()) callback.setReturnValue(null);
    }
}
