package dev.ae2security.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.parts.IPart;
import appeng.parts.CableBusContainer;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.SecurityAlarm;
import net.minecraft.core.Direction;

/**
 * AE2 lets a part power only the block it faces, which for a terminal is the space in front of its
 * screen. An alarming Security Terminal instead powers every side of its cable-bus block, so the signal
 * can be taken from beside or behind it. Weak power only: it never passes through a solid block.
 */
@Mixin(value = CableBusContainer.class, remap = false)
public abstract class CableBusContainerMixin {
    @Shadow public abstract IPart getPart(Direction side);

    @Inject(method = "isProvidingWeakPower", at = @At("RETURN"), cancellable = true)
    private void ae2security$alarmSignal(Direction side, CallbackInfoReturnable<Integer> callback) {
        if (!SecurityAlarm.anyPulsing() || callback.getReturnValueI() >= 15) return;
        for (var face : Direction.values()) {
            if (getPart(face) instanceof SecurityTerminalPart terminal && SecurityAlarm.pulsing(terminal)) {
                callback.setReturnValue(15);
                return;
            }
        }
    }
}
