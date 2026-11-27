package dev.ae2security.mixin;

import appeng.api.inventories.InternalInventory;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.parts.reporting.AbstractTerminalPart;
import dev.ae2security.security.SecurityAlarm;
import dev.ae2security.security.SecurityHardware;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RestrictedInputSlot.class, remap = false)
public abstract class RestrictedInputSlotMixin extends AppEngSlot {
    protected RestrictedInputSlotMixin(InternalInventory inventory, int slot) {
        super(inventory, slot);
    }

    @Inject(method = "isAllowEdit", at = @At("HEAD"), cancellable = true)
    private void ae2security$terminalHardware(CallbackInfoReturnable<Boolean> callback) {
        var menu = getMenu();
        if (menu != null && !menu.isClientSide() && menu.getSlotSemantic(this) == SlotSemantics.VIEW_CELL
                && menu.getTarget() instanceof AbstractTerminalPart part
                // Slot checks also run while shift-clicking other items, so a refusal here raises no alarm.
                && !SecurityAlarm.quietly(() -> SecurityHardware.allowBuild(part, menu.getPlayer()))) {
            callback.setReturnValue(false);
        }
    }
}
