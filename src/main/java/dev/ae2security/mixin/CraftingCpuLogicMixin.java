package dev.ae2security.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.*;
import appeng.crafting.inv.ListCraftingInventory;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.ae2security.security.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicMixin {
    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true)
    private void ae2security$authorize(IGrid grid, ICraftingPlan plan, IActionSource source, ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (source.player().isPresent() && !SecurityAccess.allow(grid, source.player().get(), Permission.CRAFT, false)) {
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
        }
    }

    @WrapOperation(method = "trySubmitJob", at = @At(value = "INVOKE", target =
            "Lappeng/crafting/execution/CraftingCpuHelper;tryExtractInitialItems(Lappeng/api/networking/crafting/ICraftingPlan;Lappeng/api/networking/IGrid;Lappeng/crafting/inv/ListCraftingInventory;Lappeng/api/networking/security/IActionSource;)Lappeng/api/stacks/GenericStack;"))
    private GenericStack ae2security$ingredients(ICraftingPlan plan, IGrid grid, ListCraftingInventory inventory,
            IActionSource source, Operation<GenericStack> original) {
        return original.call(plan, grid, inventory, CraftingActionSource.approved(source, grid));
    }
}
