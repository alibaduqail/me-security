package dev.ae2security.mixin;

import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.crafting.*;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.implementations.PatternAccessTermMenu;
import dev.ae2security.security.SecurityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** These overrides send data before, or continue after, their super call. Guard their entry points. */
@Mixin(value = {MEStorageMenu.class, CraftConfirmMenu.class, CraftingCPUMenu.class,
        CraftingStatusMenu.class, PatternAccessTermMenu.class, PatternEncodingTermMenu.class}, remap = false)
public abstract class MenuBroadcastMixin {
    @Inject(method = "broadcastChanges", at = @At("HEAD"), cancellable = true)
    private void ae2security$beforeSync(CallbackInfo ci) {
        if (!SecurityAccess.menu((AEBaseMenu) (Object) this)) ci.cancel();
    }
}
