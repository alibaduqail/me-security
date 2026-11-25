package dev.ae2security.mixin;

import appeng.api.parts.IPart;
import appeng.parts.reporting.AbstractMonitorPart;
import dev.ae2security.security.SecurityHardware;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Conversion-monitor transfers run in the subclass; these methods only configure the hardware. */
@Mixin(value = AbstractMonitorPart.class, remap = false)
public abstract class MonitorPartMixin {
    @Inject(method = "onUseWithoutItem", at = @At("HEAD"), cancellable = true)
    private void ae2security$lock(Player player, Vec3 pos, CallbackInfoReturnable<Boolean> callback) {
        if (!SecurityHardware.allowBuild((IPart) this, player)) callback.setReturnValue(true);
    }

    @Inject(method = "onUseItemOn", at = @At("HEAD"), cancellable = true)
    private void ae2security$display(ItemStack stack, Player player, InteractionHand hand, Vec3 pos,
            CallbackInfoReturnable<Boolean> callback) {
        if (!SecurityHardware.allowBuild((IPart) this, player)) callback.setReturnValue(true);
    }
}
