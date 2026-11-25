package dev.ae2security.mixin;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.parts.p2p.P2PTunnelPart;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ae2security.security.SecurityHardware;
import dev.ae2security.security.SecurityPlacement;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = P2PTunnelPart.class, remap = false)
public abstract class P2PTunnelPartMixin {
    @Inject(method = "onUseItemOn", at = @At("HEAD"), cancellable = true)
    private void ae2security$configure(ItemStack stack, Player player, InteractionHand hand, Vec3 pos,
            CallbackInfoReturnable<Boolean> callback) {
        if (!SecurityHardware.allowBuild((IPart) this, player)) callback.setReturnValue(true);
    }

    @WrapOperation(method = "onUseItemOn", at = @At(value = "INVOKE", target =
            "Lappeng/api/parts/IPartHost;replacePart(Lappeng/api/parts/IPartItem;Lnet/minecraft/core/Direction;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lappeng/api/parts/IPart;"),
            require = 2, allow = 2)
    private IPart ae2security$replace(IPartHost host, IPartItem<?> item, Direction side, Player player,
            InteractionHand hand, Operation<IPart> original) {
        return SecurityPlacement.allowReplacement(host, item, side, player)
                ? original.call(host, item, side, player, hand) : null;
    }
}
