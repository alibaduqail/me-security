package dev.ae2security.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.blockentity.networking.CableBusBlockEntity;
import dev.ae2security.security.SecurityBreak;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

@Mixin(value = CableBusBlockEntity.class, remap = false)
public abstract class CableBusBlockEntityMixin {
    @Inject(method = "disassembleWithWrench", at = @At("HEAD"), cancellable = true)
    private void ae2security$authorizePartRemoval(Player player, Level level, BlockHitResult hit,
            ItemStack wrench, CallbackInfoReturnable<InteractionResult> callback) {
        if (!SecurityBreak.allowWrench((CableBusBlockEntity) (Object) this, player, hit)) {
            callback.setReturnValue(InteractionResult.FAIL);
        }
    }
}
