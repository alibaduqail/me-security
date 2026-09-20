package dev.ae2security.mixin;

import appeng.api.util.AEColor;
import appeng.parts.networking.CablePart;
import dev.ae2security.security.SecurityPlacement;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Recoloring changes grid connections and must use the same checks as cable placement. */
@Mixin(value = CablePart.class, remap = false)
public abstract class CablePartMixin {
    @Inject(method = "changeColor", at = @At("HEAD"), cancellable = true)
    private void ae2security$recolor(AEColor color, Player player, CallbackInfoReturnable<Boolean> callback) {
        if (!SecurityPlacement.allowRecolor((CablePart) (Object) this, color, player)) {
            callback.setReturnValue(false);
        }
    }
}
