package dev.ae2security.mixin;

import dev.ae2security.security.SecurityPlacement;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "placeBlock", at = @At("HEAD"), cancellable = true)
    private void ae2security$place(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> callback) {
        if (!SecurityPlacement.allowStateChange(context.getLevel(), context.getClickedPos(), state, context.getPlayer())) {
            callback.setReturnValue(false);
        }
    }
}
