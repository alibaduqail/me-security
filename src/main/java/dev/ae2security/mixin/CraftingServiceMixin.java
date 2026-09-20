package dev.ae2security.mixin;

import java.util.concurrent.*;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.service.CraftingService;
import dev.ae2security.security.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServiceMixin {
    @Shadow @Final private IGrid grid;

    @Inject(method = "beginCraftingCalculation", at = @At("HEAD"), cancellable = true)
    private void ae2security$calculate(Level level, ICraftingSimulationRequester requester, AEKey key, long count,
            CalculationStrategy strategy, CallbackInfoReturnable<Future<ICraftingPlan>> cir) {
        var source = requester.getActionSource();
        if (source != null && source.player().isPresent()
                && !SecurityAccess.allow(grid, source.player().get(), Permission.CRAFT, false)) {
            var denied = new CompletableFuture<ICraftingPlan>();
            denied.cancel(false);
            cir.setReturnValue(denied);
        }
    }

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true)
    private void ae2security$submit(ICraftingPlan plan, ICraftingRequester requester, ICraftingCPU cpu,
            boolean prioritizePower, IActionSource source, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (source.player().isPresent() && !SecurityAccess.allow(grid, source.player().get(), Permission.CRAFT, false)) {
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
        }
    }
}
