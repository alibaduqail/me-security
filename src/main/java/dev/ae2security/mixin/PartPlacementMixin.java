package dev.ae2security.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.parts.PartPlacement;
import dev.ae2security.security.SecurityPlacement;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

@Mixin(value = PartPlacement.class, remap = false)
public abstract class PartPlacementMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private static void ae2security$authorizePlacement(UseOnContext context,
            CallbackInfoReturnable<InteractionResult> callback) {
        if (!SecurityPlacement.allow(context)) {
            SecurityPlacement.resyncDeniedPlacement(context);
            callback.setReturnValue(InteractionResult.FAIL);
        }
    }
}
