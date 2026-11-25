package dev.ae2security.mixin;

import appeng.hooks.WrenchHook;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ae2security.security.SecurityPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = WrenchHook.class, remap = false)
public abstract class WrenchHookMixin {
    @WrapOperation(method = "onPlayerUseBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private static boolean ae2security$rotate(Level level, BlockPos pos, BlockState state, Operation<Boolean> original,
            @Local(argsOnly = true) Player player) {
        return SecurityPlacement.allowStateChange(level, pos, state, player) && original.call(level, pos, state);
    }
}
