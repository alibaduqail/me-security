package dev.ae2security.part;

import java.util.List;
import java.util.UUID;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractDisplayPart;
import dev.ae2security.MESecurity;
import dev.ae2security.menu.SecurityMenu;
import dev.ae2security.security.SecuritySavedData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** A channel-using AE2 terminal part whose policy identity stays on the part, never its dropped item. */
public final class SecurityTerminalPart extends AbstractDisplayPart implements MenuProvider {
    private static final String TERMINAL_TAG = "ae2security:terminal";

    public static final net.minecraft.resources.ResourceLocation MODEL_OFF = MESecurity.id("part/security_terminal_off");
    public static final net.minecraft.resources.ResourceLocation MODEL_ON = MESecurity.id("part/security_terminal_on");
    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    private static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    private UUID terminalId = UUID.randomUUID();
    private boolean awaitingInitialOwner = true;

    public SecurityTerminalPart(IPartItem<?> item) {
        super(item, true);
    }

    public UUID terminalId() {
        return terminalId;
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        if (!awaitingInitialOwner || isClientSide() || !(getLevel() instanceof ServerLevel level)) return;

        var node = getGridNode();
        var ownerId = node == null ? null : node.getOwningPlayerProfileId();
        if (ownerId == null) return;

        var data = SecuritySavedData.get(level.getServer());
        data.create(terminalId, ownerId);
        var owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null) data.rememberPlayer(ownerId, owner.getGameProfile().getName());
        awaitingInitialOwner = false;
        getHost().markForSave();
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 hitPos) {
        if (super.onUseWithoutItem(player, hitPos)) return true;
        if (player instanceof ServerPlayer serverPlayer) {
            var policy = SecuritySavedData.get(serverPlayer.server).policy(terminalId);
            if (policy != null && policy.isOwner(player.getUUID())) {
                serverPlayer.openMenu(this, data -> {
                    data.writeBlockPos(getBlockEntity().getBlockPos());
                    data.writeEnum(getSide());
                });
            } else {
                player.displayClientMessage(Component.translatable("message.ae2security.owner_only"), true);
            }
        }
        return true;
    }

    /** Called by AE2 for a wrench removal and by the cable-bus block when it is physically broken. */
    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        retirePolicy();
        super.addAdditionalDrops(drops, wrenched);
    }

    public void retirePolicy() {
        if (getLevel() instanceof ServerLevel level) {
            SecuritySavedData.get(level.getServer()).retire(terminalId);
        }
    }

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        tag.putUUID(TERMINAL_TAG, terminalId);
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);
        awaitingInitialOwner = false;
        if (tag.hasUUID(TERMINAL_TAG)) terminalId = tag.getUUID(TERMINAL_TAG);
    }

    @Override
    public IPartModel getStaticModels() {
        return selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("item.ae2security.security_terminal");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        if (!(getLevel() instanceof ServerLevel level)) return null;
        var policy = SecuritySavedData.get(level.getServer()).policy(terminalId);
        return policy != null && policy.isOwner(player.getUUID()) ? new SecurityMenu(id, inventory, this) : null;
    }
}
