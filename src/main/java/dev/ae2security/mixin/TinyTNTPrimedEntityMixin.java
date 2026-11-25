package dev.ae2security.mixin;

import appeng.entity.TinyTNTPrimedEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ae2security.security.SecurityHardware;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Tiny TNT breaks blocks in its own loop, after posting an explosion event that lists no blocks. */
@Mixin(value = TinyTNTPrimedEntity.class, remap = false)
public abstract class TinyTNTPrimedEntityMixin {
    @WrapOperation(method = "explode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;getExplosionResistance(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;)F"))
    private float ae2security$protect(Block block, BlockState state, BlockGetter level, BlockPos pos,
            Explosion explosion, Operation<Float> original) {
        // An unbeatable resistance leaves the block, its drops and its block entity untouched.
        if (level instanceof Level world && SecurityHardware.protectedBlock(world, pos)) return Float.MAX_VALUE;
        return original.call(block, state, level, pos, explosion);
    }
}
