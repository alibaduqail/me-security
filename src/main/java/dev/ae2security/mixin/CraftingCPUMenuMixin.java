package dev.ae2security.mixin;

import appeng.menu.AEBaseMenu;
import appeng.menu.me.crafting.CraftingCPUMenu;
import dev.ae2security.security.SecurityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftingCPUMenu.class, remap = false)
public abstract class CraftingCPUMenuMixin {
    @Inject(method = {"cancelCrafting", "toggleScheduling"}, at = @At("HEAD"), cancellable = true)
    private void ae2security$craft(CallbackInfo ci) {
        if (!SecurityAccess.craftMenu((AEBaseMenu) (Object) this)) ci.cancel();
    }
}
