package dev.ae2security.mixin;

import appeng.parts.automation.ItemPickupStrategy;
import dev.ae2security.security.SecurityHardware;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Annihilation planes destroy blocks without a break event, so protected hardware is exempted here. */
@Mixin(value = ItemPickupStrategy.class, remap = false)
public abstract class ItemPickupStrategyMixin {
    @Inject(method = "canHandleBlock", at = @At("HEAD"), cancellable = true)
    private void ae2security$protectHardware(ServerLevel level, BlockPos pos, BlockState state,
            CallbackInfoReturnable<Boolean> callback) {
        if (state.hasBlockEntity() && SecurityHardware.protectedBlock(level, pos)) callback.setReturnValue(false);
    }
}
