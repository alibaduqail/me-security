package dev.ae2security.mixin;

import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternAccessTermMenu;
import dev.ae2security.security.SecurityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternAccessTermMenu.class, remap = false)
public abstract class PatternAccessMenuMixin {
    @Inject(method = {"doAction", "quickMovePattern"}, at = @At("HEAD"), cancellable = true)
    private void ae2security$patterns(CallbackInfo ci) {
        if (!SecurityAccess.menu((AEBaseMenu) (Object) this)) ci.cancel();
    }
}
