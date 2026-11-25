package dev.ae2security.mixin;

import appeng.block.crafting.AbstractCraftingUnitBlock;
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

@Mixin(value = AbstractCraftingUnitBlock.class, remap = false)
public abstract class CraftingUnitBlockMixin {
    @WrapOperation(method = {"upgrade", "removeUpgrade"}, at = @At(value = "INVOKE",
            target = "Lappeng/block/crafting/AbstractCraftingUnitBlock;transform(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean ae2security$upgrade(AbstractCraftingUnitBlock<?> block, Level level, BlockPos pos, BlockState state,
            Operation<Boolean> original, @Local(argsOnly = true) Player player) {
        return SecurityPlacement.allowStateChange(level, pos, state, player) && original.call(block, level, pos, state);
    }
}
