package dev.ae2security.mixin;

import java.util.UUID;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.*;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.ae2security.security.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicMixin {
    @Shadow @Final private CraftingCPUCluster cluster;
    @Shadow public abstract ICraftingLink getLastLink();
    @Shadow public abstract GenericStack getFinalJobOutput();
    @Unique private UUID ae2security$player;
    @Unique private UUID ae2security$terminal;
    @Unique private UUID ae2security$job;

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

    @Inject(method = "trySubmitJob", at = @At("RETURN"))
    private void ae2security$rememberRequester(IGrid grid, ICraftingPlan plan, IActionSource source,
            ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (!cir.getReturnValue().successful()) return;
        ae2security$clearClaim();
        // Linked machine requests deliver to their requester and must not also grant terminal collection.
        if (requester == null && source.player().isPresent()) {
            ae2security$terminal = SecurityAccess.craftingTerminal(grid);
            if (ae2security$terminal != null) {
                ae2security$player = source.player().get().getUUID();
                ae2security$job = getLastLink().getCraftingID();
            }
        }
        cluster.markDirty();
    }

    @Inject(method = "finishJob", at = @At("HEAD"))
    private void ae2security$complete(boolean success, CallbackInfo ci) {
        var link = getLastLink();
        if (success && ae2security$player != null && link != null
                && link.getCraftingID().equals(ae2security$job)
                && ae2security$terminal.equals(SecurityAccess.craftingTerminal(cluster.getGrid()))) {
            SecuritySavedData.get(cluster.getLevel().getServer()).recordCraftedOutput(
                    ae2security$terminal, ae2security$player, getFinalJobOutput());
        }
        ae2security$clearClaim();
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void ae2security$saveClaim(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        tag.remove("ae2security:outputClaim");
        if (ae2security$player == null) return;
        var claim = new CompoundTag();
        claim.putUUID("player", ae2security$player);
        claim.putUUID("terminal", ae2security$terminal);
        claim.putUUID("job", ae2security$job);
        tag.put("ae2security:outputClaim", claim);
    }

    @Inject(method = "readFromNBT", at = @At("HEAD"))
    private void ae2security$resetClaim(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        ae2security$clearClaim();
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void ae2security$loadClaim(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        var claim = tag.getCompound("ae2security:outputClaim");
        var link = getLastLink();
        if (link != null && claim.hasUUID("player") && claim.hasUUID("terminal") && claim.hasUUID("job")
                && link.getCraftingID().equals(claim.getUUID("job"))) {
            ae2security$player = claim.getUUID("player");
            ae2security$terminal = claim.getUUID("terminal");
            ae2security$job = claim.getUUID("job");
        }
    }

    @Unique private void ae2security$clearClaim() {
        ae2security$player = null;
        ae2security$terminal = null;
        ae2security$job = null;
    }
}
