package dev.ae2security.client;

import java.util.List;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import dev.ae2security.menu.SecurityEmitterMenu;
import dev.ae2security.security.EmitterTrigger;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Two toolbar buttons, like AE2's level emitters: whose denials trigger it, and whether the output is inverted. */
public final class SecurityEmitterScreen extends AEBaseScreen<SecurityEmitterMenu> {
    private static final String KEY = "gui.ae2security.security_emitter.";
    private final ToggleButton triggerButton;
    private final ToggleButton outputButton;

    public SecurityEmitterScreen(SecurityEmitterMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        triggerButton = addToLeftToolbar(new ToggleButton(button -> menu.setTrigger(menu.trigger.next())));
        outputButton = addToLeftToolbar(new ToggleButton(button -> menu.setInverted(!menu.inverted)));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        var trigger = KEY + "trigger." + menu.trigger.id();
        var output = KEY + "output." + (menu.inverted ? "inverted" : "normal");
        triggerButton.show(menu.trigger == EmitterTrigger.STRANGERS ? Icon.LOCKED : Icon.VIEW_MODE_ALL,
                Component.translatable(KEY + "trigger"), trigger);
        outputButton.show(menu.inverted ? Icon.REDSTONE_LOW : Icon.REDSTONE_HIGH,
                Component.translatable(KEY + "output"), output);
        setTextContent("trigger", Component.translatable(KEY + "trigger_line", Component.translatable(trigger)));
        setTextContent("output", Component.translatable(KEY + "output_line", Component.translatable(output)));
    }

    private static final class ToggleButton extends IconButton {
        private Icon icon = Icon.INVALID;
        private List<Component> tooltip = List.of();

        ToggleButton(OnPress onPress) {
            super(onPress);
        }

        void show(Icon icon, Component heading, String key) {
            this.icon = icon;
            this.tooltip = List.of(heading, Component.translatable(key).withStyle(ChatFormatting.WHITE),
                    Component.translatable(key + ".description").withStyle(ChatFormatting.GRAY));
            setMessage(heading);
        }

        @Override protected Icon getIcon() { return icon; }

        @Override public List<Component> getTooltipMessage() { return tooltip; }
    }
}
