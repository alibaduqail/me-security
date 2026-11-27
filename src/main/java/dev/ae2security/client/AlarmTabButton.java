package dev.ae2security.client;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import dev.ae2security.security.AlarmMode;

/** The left-toolbar button that shows the terminal's alarm mode and switches to the next one. */
final class AlarmTabButton extends IconButton {
    private AlarmMode mode = AlarmMode.DEFAULT;

    AlarmTabButton(OnPress onPress) {
        super(onPress);
    }

    void show(AlarmMode mode) {
        this.mode = mode;
    }

    /** A crossed-out circle for off, an unlit torch for alerts only and a lit one once redstone is on. */
    @Override
    protected Icon getIcon() {
        return switch (mode) {
            case OFF -> Icon.INVALID;
            case ALERT -> Icon.REDSTONE_OFF;
            case REDSTONE -> Icon.REDSTONE_ON;
        };
    }
}
