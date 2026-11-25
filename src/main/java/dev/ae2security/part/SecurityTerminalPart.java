package dev.ae2security.part;

import java.util.List;
import java.util.UUID;

import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractDisplayPart;
import dev.ae2security.MESecurity;
import dev.ae2security.menu.SecurityMenu;
import dev.ae2security.security.ISecurityGrid;
import dev.ae2security.security.SecurityGridService;
import dev.ae2security.security.SecurityPolicy;
import dev.ae2security.security.SecuritySavedData;
import dev.ae2security.security.TerminalIdentity;
import dev.ae2security.security.TerminalLocation;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/** A channel-using AE2 terminal part whose policy identity stays on the part, never its dropped item. */
public final class SecurityTerminalPart extends AbstractDisplayPart implements MenuProvider {
    private static final String TERMINAL_TAG = "ae2security:terminal";
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final net.minecraft.resources.ResourceLocation MODEL_OFF = MESecurity.id("part/security_terminal_off");
    public static final net.minecraft.resources.ResourceLocation MODEL_ON = MESecurity.id("part/security_terminal_on");
    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    private static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    private UUID terminalId = UUID.randomUUID();
    private boolean awaitingInitialOwner = true;
    /** Server tick in which this instance created its policy, or -1. Never saved. */
    private long policyCreatedTick = -1;
    /** Server side, between addToWorld and removeFromWorld. Never saved. */
    private boolean inWorld;

    public SecurityTerminalPart(IPartItem<?> item) {
        super(item, true);
    }

    public UUID terminalId() {
        return terminalId;
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        if (isClientSide() || !(getLevel() instanceof ServerLevel level)) return;
        inWorld = true;
        SecuritySavedData.get(level.getServer()).markInWorld(terminalId, location());
        if (!awaitingInitialOwner) {
            recoverMissingPolicy(level);
            reconcileLocation(level);
            return;
        }

        var node = getGridNode();
        var ownerId = node == null ? null : node.getOwningPlayerProfileId();
        var data = SecuritySavedData.get(level.getServer());
        if (ownerId == null) {
            // Nobody can ever own this terminal, and a policy-less identity on the grid would deny everyone.
            data.retire(terminalId);
            awaitingInitialOwner = false;
            getHost().markForSave();
            LOGGER.warn("ME Security Terminal {} at {} in {} was placed without an owning player, so it protects "
                    + "nothing and its identity was retired.", terminalId, getBlockEntity().getBlockPos(),
                    level.dimension().location());
            return;
        }
        data.create(terminalId, ownerId);
        data.setLocation(terminalId, location());
        policyCreatedTick = level.getServer().getTickCount();
        var owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null) data.rememberPlayer(ownerId, owner.getGameProfile().getName());
        awaitingInitialOwner = false;
        getHost().markForSave();
    }

    /**
     * The part is saved with its chunk, the policy with the world data, and the two are written at
     * different times. A terminal loaded without a policy or a tombstone would otherwise deny everyone
     * forever, including the only player allowed to remove it.
     */
    private void recoverMissingPolicy(ServerLevel level) {
        var data = SecuritySavedData.get(level.getServer());
        if (data.policy(terminalId) != null || data.retired(terminalId)) return;

        var position = getBlockEntity().getBlockPos();
        var dimension = level.dimension().location();
        var node = getGridNode();
        var ownerId = node == null ? null : node.getOwningPlayerProfileId();
        if (ownerId == null) {
            LOGGER.warn("ME Security Terminal {} at {} in {} has no saved policy and no recorded placer. "
                    + "It stays locked until an administrator runs /mesecurity retire {} or removes the block.", terminalId, position, dimension, terminalId);
            return;
        }
        data.create(terminalId, ownerId);
        data.setLocation(terminalId, location());
        var owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null) data.rememberPlayer(ownerId, owner.getGameProfile().getName());
        LOGGER.warn("ME Security Terminal {} at {} in {} had no saved policy, so one was recreated for the player "
                + "who placed it ({}). Trusted players and permissions were not recovered, and an ownership "
                + "transfer made before the policy was lost has reverted to that placer.",
                terminalId, position, dimension, ownerId);
    }

    private TerminalLocation location() {
        return new TerminalLocation(getLevel().dimension().location().toString(), getBlockEntity().getBlockPos(), getSide());
    }

    /**
     * Block-entity copies (WorldEdit, creative pick-block, structure templates, /clone) duplicate the
     * identity saved on this part, while movers (Spatial IO, contraptions) vacate the old location before
     * the part is added here. A copy receives its own identity with the original's owner and trust; a move
     * updates the canonical location. Nothing is decided while the recorded location is unloaded.
     */
    private void reconcileLocation(ServerLevel level) {
        var server = level.getServer();
        var data = SecuritySavedData.get(server);
        var policy = data.policy(terminalId);
        if (policy == null) return;
        var here = location();
        var recorded = data.location(terminalId);
        var probe = recorded == null || recorded.equals(here) ? Probe.NONE : probe(server, recorded);
        switch (TerminalIdentity.classify(recorded, here, probe.loaded(), probe.holds(terminalId, this))) {
            case CLONE -> fork(policy, recorded);
            case AMBIGUOUS -> LOGGER.debug("ME Security Terminal {} at {} cannot be checked against its recorded "
                    + "location {}, which is not loaded; it keeps its identity for now.", terminalId, here, recorded);
            case RECORD, MOVE -> {
                data.setLocation(terminalId, here);
                forkCopiesInWorld(server, policy, here);
            }
            case SAME -> forkCopiesInWorld(server, policy, here);
        }
    }

    /**
     * A copy added while this canonical instance was unloaded could not be recognized then. Now that both
     * are in the world, every other instance receives its own identity.
     */
    private void forkCopiesInWorld(MinecraftServer server, SecurityPolicy policy, TerminalLocation here) {
        var data = SecuritySavedData.get(server);
        for (var other : data.inWorldLocations(terminalId)) {
            if (other.equals(here)) continue;
            var probe = probe(server, other);
            if (probe.holds(terminalId, this)) probe.terminal().fork(policy, here);
            else data.markOutOfWorld(terminalId, other);
        }
    }

    /** Gives this copied instance a fresh identity whose policy copies the original's owner and trust. */
    private void fork(SecurityPolicy source, TerminalLocation original) {
        if (!(getLevel() instanceof ServerLevel level)) return;
        var data = SecuritySavedData.get(level.getServer());
        var copied = terminalId;
        var here = location();
        terminalId = UUID.randomUUID();
        data.create(terminalId, new SecurityPolicy(source.owner(), source.trusted(), 0));
        data.setLocation(terminalId, here);
        if (inWorld) {
            data.markOutOfWorld(copied, here);
            data.markInWorld(terminalId, here);
        }
        getHost().markForSave();
        var node = getGridNode();
        var grid = node == null ? null : node.getGrid();
        if (grid != null && grid.getService(ISecurityGrid.class) instanceof SecurityGridService service) {
            service.disownCopiedIdentity(copied);
        }
        LOGGER.info("ME Security Terminal at {} is a copy of terminal {} at {}. It now has its own identity {} "
                + "with the same owner and trusted players.", here, copied, original, terminalId);
    }

    private record Probe(boolean loaded, SecurityTerminalPart terminal) {
        static final Probe NONE = new Probe(false, null);

        boolean holds(UUID id, SecurityTerminalPart self) {
            return terminal != null && terminal != self && id.equals(terminal.terminalId());
        }
    }

    /** What is at a location right now. Never loads a chunk or creates a block entity. */
    private static Probe probe(MinecraftServer server, TerminalLocation location) {
        var dimension = ResourceLocation.tryParse(location.dimension());
        var level = dimension == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) return Probe.NONE;
        var pos = location.pos();
        var chunk = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunk == null) return Probe.NONE;
        var blockEntity = chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
        var part = blockEntity instanceof IPartHost host && !blockEntity.isRemoved() ? host.getPart(location.side()) : null;
        return new Probe(true, part instanceof SecurityTerminalPart terminal ? terminal : null);
    }

    /**
     * AE2 rolls back an obstructed placement by removing the part it just added, without collecting
     * drops, while the cable bus stays in the world. Retire the policy this instance created in the same
     * server tick, or the grid keeps a terminal identity that no part carries. A chunk unload marks the
     * host as out of the world first, and a reloaded instance never creates a policy here, so neither
     * can retire anything.
     */
    @Override
    public void removeFromWorld() {
        if (policyCreatedTick >= 0 && getLevel() instanceof ServerLevel level
                && level.getServer().getTickCount() == policyCreatedTick
                && getHost() != null && getHost().isInWorld()) {
            retirePolicy();
        }
        policyCreatedTick = -1;
        if (inWorld && getLevel() instanceof ServerLevel level) {
            SecuritySavedData.get(level.getServer()).markOutOfWorld(terminalId, location());
        }
        inWorld = false;
        super.removeFromWorld();
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

    /**
     * Only the canonical instance retires the identity. Removing a copy while the canonical location still
     * holds the identity, or is unloaded and cannot be checked, leaves the policy to the instance that may
     * still protect another network; the removed part is simply dropped.
     */
    public void retirePolicy() {
        if (!(getLevel() instanceof ServerLevel level)) return;
        var data = SecuritySavedData.get(level.getServer());
        var here = location();
        var recorded = data.location(terminalId);
        var probe = recorded == null || recorded.equals(here) ? Probe.NONE : probe(level.getServer(), recorded);
        if (TerminalIdentity.shouldRetire(recorded, here, probe.loaded(), probe.holds(terminalId, this))) {
            data.retire(terminalId);
        } else {
            LOGGER.info("Removed a copy of ME Security Terminal {} at {}; the policy stays with the terminal "
                    + "recorded at {}.", terminalId, here, recorded);
        }
    }

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        tag.putUUID(TERMINAL_TAG, terminalId);
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        var previous = terminalId;
        super.readFromNBT(tag, registries);
        awaitingInitialOwner = false;
        if (tag.hasUUID(TERMINAL_TAG)) terminalId = tag.getUUID(TERMINAL_TAG);
        // AE2 reloads a live part in place when its block entity is given new data, for example when a
        // copied region is pasted over an existing cable bus. The adopted identity is checked once the
        // load has finished, as addToWorld checks a part that arrives with saved data.
        if (inWorld && !terminalId.equals(previous) && getLevel() instanceof ServerLevel level) {
            var data = SecuritySavedData.get(level.getServer());
            var here = location();
            data.markOutOfWorld(previous, here);
            data.markInWorld(terminalId, here);
            var adopted = terminalId;
            level.getServer().tell(new TickTask(level.getServer().getTickCount(), () -> {
                if (inWorld && adopted.equals(terminalId)) reconcileLocation(level);
            }));
        }
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
