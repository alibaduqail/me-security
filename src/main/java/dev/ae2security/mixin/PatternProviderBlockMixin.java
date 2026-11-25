package dev.ae2security.mixin;

import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.util.InteractionUtil;
import appeng.util.Platform;
import dev.ae2security.security.SecurityPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PatternProviderBlock.class, remap = false)
public abstract class PatternProviderBlockMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void ae2security$facing(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> callback) {
        if (!InteractionUtil.canWrenchRotate(stack)) return;
        Direction old = state.getValue(PatternProviderBlock.PUSH_DIRECTION).getDirection();
        Direction face = hit.getDirection();
        PushDirection next = old == face.getOpposite() ? PushDirection.fromDirection(face)
                : old == face ? PushDirection.ALL : old == null ? PushDirection.fromDirection(face.getOpposite())
                : PushDirection.fromDirection(Platform.rotateAround(old, face));
        if (!SecurityPlacement.allowStateChange(level, pos, state.setValue(PatternProviderBlock.PUSH_DIRECTION, next), player)) {
            callback.setReturnValue(ItemInteractionResult.FAIL);
        }
    }
}
