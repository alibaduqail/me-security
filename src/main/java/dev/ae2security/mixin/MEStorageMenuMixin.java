package dev.ae2security.mixin;

import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.MEStorageMenu;
import dev.ae2security.security.SecurityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MEStorageMenu.class, remap = false)
public abstract class MEStorageMenuMixin {
    @Inject(method = "handleInteraction", at = @At("HEAD"), cancellable = true)
    private void ae2security$interact(CallbackInfo ci) {
        if (!SecurityAccess.menu((AEBaseMenu) (Object) this)) ci.cancel();
    }
}
