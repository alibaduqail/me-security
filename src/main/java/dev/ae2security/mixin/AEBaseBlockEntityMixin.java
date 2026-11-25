package dev.ae2security.mixin;

import appeng.blockentity.AEBaseBlockEntity;
import dev.ae2security.security.SecurityBreak;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AEBaseBlockEntity.class, remap = false)
public abstract class AEBaseBlockEntityMixin {
    @Inject(method = "disassembleWithWrench", at = @At("HEAD"), cancellable = true)
    private void ae2security$wrench(Player player, Level level, BlockHitResult hit, ItemStack wrench,
            CallbackInfoReturnable<InteractionResult> callback) {
        if (!SecurityBreak.allowBlockBreak((AEBaseBlockEntity) (Object) this, player)) {
            callback.setReturnValue(InteractionResult.FAIL);
        }
    }
}
