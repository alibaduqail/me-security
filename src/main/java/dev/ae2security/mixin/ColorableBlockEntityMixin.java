package dev.ae2security.mixin;

import appeng.api.util.AEColor;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.blockentity.crafting.CraftingMonitorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.ae2security.security.SecurityHardware;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {MEChestBlockEntity.class, CraftingMonitorBlockEntity.class}, remap = false)
public abstract class ColorableBlockEntityMixin {
    @Inject(method = "recolourBlock", at = @At("HEAD"), cancellable = true)
    private void ae2security$paint(Direction side, AEColor color, Player player, CallbackInfoReturnable<Boolean> callback) {
        if (player != null && !SecurityHardware.allowBuild((BlockEntity) (Object) this, player)) {
            callback.setReturnValue(false);
        }
    }
}
