package dev.ae2security.mixin;

import appeng.blockentity.AEBaseInvBlockEntity;
import dev.ae2security.security.ProtectedCellHandler;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AEBaseInvBlockEntity.class, remap = false)
public abstract class ExposedInventoryMixin {
    @Inject(method = "getExposedItemHandler", at = @At("RETURN"), cancellable = true)
    private void ae2security$cells(Direction side, CallbackInfoReturnable<IItemHandler> callback) {
        callback.setReturnValue(ProtectedCellHandler.wrap((AEBaseInvBlockEntity) (Object) this,
                side, callback.getReturnValue()));
    }
}
