package dev.ae2security.part;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.automation.AbstractLevelEmitterPart;
import appeng.parts.automation.EnergyLevelEmitterPart;
import dev.ae2security.MESecurity;
import dev.ae2security.security.EmitterTrigger;
import dev.ae2security.security.SecurityAlarm;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A level-emitter-style part that emits redstone for a few seconds after an action is denied on its
 * network. What the signal drives is up to the player. It grants and blocks nothing itself.
 */
public final class SecurityEmitterPart extends AbstractLevelEmitterPart {
    private static final String TRIGGER_TAG = "ae2security:trigger";

    public static final ResourceLocation MODEL_BASE_OFF = MESecurity.id("part/security_emitter_base_off");
    public static final ResourceLocation MODEL_BASE_ON = MESecurity.id("part/security_emitter_base_on");
    private static final IPartModel MODEL_OFF_OFF = new PartModel(MODEL_BASE_OFF, EnergyLevelEmitterPart.MODEL_STATUS_OFF);
    private static final IPartModel MODEL_OFF_ON = new PartModel(MODEL_BASE_OFF, EnergyLevelEmitterPart.MODEL_STATUS_ON);
    private static final IPartModel MODEL_OFF_HAS_CHANNEL =
            new PartModel(MODEL_BASE_OFF, EnergyLevelEmitterPart.MODEL_STATUS_HAS_CHANNEL);
    private static final IPartModel MODEL_ON_OFF = new PartModel(MODEL_BASE_ON, EnergyLevelEmitterPart.MODEL_STATUS_OFF);
    private static final IPartModel MODEL_ON_ON = new PartModel(MODEL_BASE_ON, EnergyLevelEmitterPart.MODEL_STATUS_ON);
    private static final IPartModel MODEL_ON_HAS_CHANNEL =
            new PartModel(MODEL_BASE_ON, EnergyLevelEmitterPart.MODEL_STATUS_HAS_CHANNEL);

    private EmitterTrigger trigger = EmitterTrigger.DEFAULT;

    public SecurityEmitterPart(IPartItem<?> item) {
        super(item);
    }

    public EmitterTrigger trigger() { return trigger; }

    public void setTrigger(EmitterTrigger trigger) {
        if (trigger == null || trigger == this.trigger) return;
        this.trigger = trigger;
        getHost().markForSave();
    }

    /** Inverted: emits until an action is denied, then goes dark for the pulse. Uses AE2's emitter setting. */
    public boolean inverted() {
        return getConfigManager().getSetting(Settings.REDSTONE_EMITTER) == RedstoneMode.LOW_SIGNAL;
    }

    public void setInverted(boolean inverted) {
        getConfigManager().putSetting(Settings.REDSTONE_EMITTER, inverted ? RedstoneMode.LOW_SIGNAL : RedstoneMode.HIGH_SIGNAL);
    }

    /** Called by the alarm at the end of the server tick in which this emitter was triggered or released. */
    public void alarmChanged() {
        if (!isClientSide() && getHost() != null && getHost().isInWorld()) updateState();
    }

    @Override protected int getUpgradeSlots() { return 0; }

    /** AE2 calls this when a setting changes; there is nothing to watch, only the output to refresh. */
    @Override protected void configureWatchers() { alarmChanged(); }

    @Override protected boolean hasDirectOutput() { return true; }

    @Override protected boolean getDirectOutput() { return SecurityAlarm.pulsing(this) != inverted(); }

    @Override
    public void removeFromWorld() {
        super.removeFromWorld();
        SecurityAlarm.forget(this);
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!isClientSide()) MenuOpener.open(MESecurity.EMITTER_MENU.get(), player, MenuLocators.forPart(this));
        return true;
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        trigger = EmitterTrigger.byId(data.getString(TRIGGER_TAG));
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        if (trigger != EmitterTrigger.DEFAULT) data.putString(TRIGGER_TAG, trigger.id());
    }

    @Override
    public IPartModel getStaticModels() {
        boolean on = isLevelEmitterOn();
        if (isActive() && isPowered()) return on ? MODEL_ON_HAS_CHANNEL : MODEL_OFF_HAS_CHANNEL;
        if (isPowered()) return on ? MODEL_ON_ON : MODEL_OFF_ON;
        return on ? MODEL_ON_OFF : MODEL_OFF_OFF;
    }
}
