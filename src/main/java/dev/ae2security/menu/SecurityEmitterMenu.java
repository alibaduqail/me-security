package dev.ae2security.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import dev.ae2security.MESecurity;
import dev.ae2security.part.SecurityEmitterPart;
import dev.ae2security.security.EmitterTrigger;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Security Emitter's settings. Opened through AE2's menu framework, so who may open it and every
 * later action are checked like any other AE2 menu; the type is tagged owner-only.
 */
public final class SecurityEmitterMenu extends AEBaseMenu {
    private static final String ACTION_TRIGGER = "setTrigger";
    private static final String ACTION_INVERTED = "setInverted";

    @GuiSync(0) public EmitterTrigger trigger = EmitterTrigger.DEFAULT;
    @GuiSync(1) public boolean inverted;
    private final SecurityEmitterPart emitter;

    public SecurityEmitterMenu(int id, Inventory inventory, SecurityEmitterPart emitter) {
        super(MESecurity.EMITTER_MENU.get(), id, inventory, emitter);
        this.emitter = emitter;
        registerClientAction(ACTION_TRIGGER, EmitterTrigger.class, this::setTrigger);
        registerClientAction(ACTION_INVERTED, Boolean.class, this::setInverted);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            trigger = emitter.trigger();
            inverted = emitter.inverted();
        }
        super.broadcastChanges();
    }

    public void setTrigger(EmitterTrigger trigger) {
        if (trigger == null) return;
        if (isClientSide()) {
            this.trigger = trigger;
            sendClientAction(ACTION_TRIGGER, trigger);
        } else {
            emitter.setTrigger(trigger);
        }
    }

    public void setInverted(Boolean inverted) {
        if (inverted == null) return;
        if (isClientSide()) {
            this.inverted = inverted;
            sendClientAction(ACTION_INVERTED, inverted);
        } else {
            emitter.setInverted(inverted);
        }
    }
}
