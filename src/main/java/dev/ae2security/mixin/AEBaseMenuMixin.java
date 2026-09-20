package dev.ae2security.mixin;

import appeng.menu.AEBaseMenu;
import dev.ae2security.security.SecurityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = AEBaseMenu.class, remap = false)
public abstract class AEBaseMenuMixin {
    @Inject(method = {"broadcastChanges", "sendAllDataToRemote", "receiveClientAction", "clicked", "doAction", "swapSlotContents", "setFilter"},
            at = @At("HEAD"), cancellable = true)
    private void ae2security$guard(CallbackInfo ci) {
        if (!SecurityAccess.menu((AEBaseMenu) (Object) this)) ci.cancel();
    }
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void ae2security$quickMove(CallbackInfoReturnable<net.minecraft.world.item.ItemStack> cir) {
        if (!SecurityAccess.menu((AEBaseMenu) (Object) this)) cir.setReturnValue(net.minecraft.world.item.ItemStack.EMPTY);
    }
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void ae2security$valid(CallbackInfoReturnable<Boolean> cir) {
        if (!SecurityAccess.menu((AEBaseMenu) (Object) this)) cir.setReturnValue(false);
    }
}
