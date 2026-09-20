package dev.ae2security.mixin;

import appeng.api.storage.*;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.MEStorageMenu;
import dev.ae2security.security.PlayerMenuStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MEStorageMenu.class, remap = false)
public abstract class MenuInventoryMixin {
    @Redirect(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/api/storage/ITerminalHost;Z)V",
            at = @At(value = "INVOKE",
            target = "Lappeng/api/storage/ITerminalHost;getInventory()Lappeng/api/storage/MEStorage;"))
    private MEStorage ae2security$playerInventory(ITerminalHost host) {
        var inventory = host.getInventory();
        var menu = (AEBaseMenu) (Object) this;
        return inventory == null || menu.isClientSide() ? inventory : new PlayerMenuStorage(inventory, host, menu.getPlayer());
    }
}
