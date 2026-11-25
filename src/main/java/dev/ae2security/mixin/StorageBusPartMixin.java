package dev.ae2security.mixin;

import appeng.hooks.ticking.TickHandler;
import appeng.parts.PartAdjacentApi;
import appeng.parts.storagebus.StorageBusPart;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ae2security.security.AccessDecision;
import dev.ae2security.security.SecurityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A storage bus mounts whatever ME storage the faced block offers. An Interface offers its whole network
 * and an ME Chest its cell, so a bus from another network must not mount them without Build there.
 * Returning null lets AE2 fall back to the block's ordinary item/fluid handlers.
 */
@Mixin(value = StorageBusPart.class, remap = false)
public abstract class StorageBusPartMixin {
    @Shadow private void scheduleUpdate() {}

    @WrapOperation(method = "updateTarget", at = @At(value = "INVOKE",
            target = "Lappeng/parts/PartAdjacentApi;find()Ljava/lang/Object;"))
    private Object ae2security$foreignStorage(PartAdjacentApi<?> accessor, Operation<Object> original) {
        Object storage = original.call(accessor);
        if (storage == null) return null;
        var bus = (StorageBusPart) (Object) this;
        var decision = SecurityAccess.adjacentStorage(bus);
        if (decision == AccessDecision.ALLOW) return storage;
        if (decision == AccessDecision.UNRESOLVED) {
            // The faced hardware has not joined a grid yet. AE2 ignores alerts from inside this node's own
            // tick, so look again from the level queue instead of leaving the bus permanently unmounted.
            TickHandler.instance().addCallable(bus.getLevel(), () -> this.scheduleUpdate());
        }
        return null;
    }
}
