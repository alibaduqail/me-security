package dev.ae2security.mixin;

import appeng.api.networking.IGrid;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ae2security.security.AE2wtlibSecurityPatched;
import dev.ae2security.security.Permission;
import dev.ae2security.security.SecurityAccess;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Prevents AE2WTLib's passive restock sync from disclosing network inventory counts. */
@Pseudo
@Mixin(targets = "de.mari_023.ae2wtlib.wct.magnet_card.MagnetHandler", remap = false)
public abstract class AE2wtlibMagnetHandlerMixin implements AE2wtlibSecurityPatched {
    @WrapOperation(
            method = "sendRestockAble(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lde/mari_023/ae2wtlib/wct/CraftingTerminalHandler;getTargetGrid()Lappeng/api/networking/IGrid;"),
            require = 2,
            allow = 2)
    private static IGrid ae2security$authorizeInventorySync(@Coerce Object handler, Operation<IGrid> original,
            ServerPlayer player) {
        var grid = original.call(handler);
        return grid != null && SecurityAccess.allow(grid, player, Permission.VIEW, false) ? grid : null;
    }
}
