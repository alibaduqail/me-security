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

    /** A crossed-out circle when alerts are off and a tick when they are on. */
    @Override
    protected Icon getIcon() {
        return switch (mode) {
            case OFF -> Icon.INVALID;
            case ALERT -> Icon.VALID;
        };
    }
}
