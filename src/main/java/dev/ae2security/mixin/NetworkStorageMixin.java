package dev.ae2security.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.me.storage.NetworkStorage;
import dev.ae2security.security.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NetworkStorage.class, remap = false)
public abstract class NetworkStorageMixin implements GridBoundStorage {
    @Unique private IGrid ae2security$grid;
    @Override public void ae2security$bind(IGrid grid) { ae2security$grid = grid; }
    @Override public IGrid ae2security$grid() { return ae2security$grid; }

    @Inject(method = "insert", at = @At("HEAD"), cancellable = true)
    private void ae2security$insert(AEKey key, long amount, Actionable mode, IActionSource source,
            CallbackInfoReturnable<Long> cir) {
        if (!SecurityAccess.transfer(ae2security$grid, source, Permission.INSERT)) cir.setReturnValue(0L);
    }
    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void ae2security$extract(AEKey key, long amount, Actionable mode, IActionSource source,
            CallbackInfoReturnable<Long> cir) {
        if (!SecurityAccess.transfer(ae2security$grid, source, Permission.EXTRACT)) cir.setReturnValue(0L);
    }
}
