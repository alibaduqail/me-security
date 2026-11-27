package dev.ae2security.client;

import java.util.List;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.IconButton;
import dev.ae2security.menu.SecurityEmitterMenu;
import dev.ae2security.security.EmitterTrigger;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Whose denials trigger the emitter is a button in the screen itself. The redstone output sits in the left
 * toolbar, like the redstone mode of AE2's level emitters.
 */
public final class SecurityEmitterScreen extends AEBaseScreen<SecurityEmitterMenu> {
    private static final String KEY = "gui.ae2security.security_emitter.";
    private final AE2Button triggerButton;
    private final OutputButton outputButton;
    private EmitterTrigger shownTrigger;

    public SecurityEmitterScreen(SecurityEmitterMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        triggerButton = widgets.addButton("trigger", Component.empty(), () -> menu.setTrigger(menu.trigger.next()));
        outputButton = addToLeftToolbar(new OutputButton(button -> menu.setInverted(!menu.inverted)));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (shownTrigger != menu.trigger) {
            shownTrigger = menu.trigger;
            var trigger = KEY + "trigger." + menu.trigger.id();
            triggerButton.setMessage(Component.translatable(KEY + "trigger_line", Component.translatable(trigger)));
            triggerButton.setTooltip(Tooltip.create(Component.translatable(trigger + ".description")));
        }
        outputButton.show(menu.inverted);
    }

    private static final class OutputButton extends IconButton {
        private boolean inverted;

        OutputButton(OnPress onPress) {
            super(onPress);
            setMessage(Component.translatable(KEY + "output"));
        }

        void show(boolean inverted) { this.inverted = inverted; }

        @Override protected Icon getIcon() { return inverted ? Icon.REDSTONE_LOW : Icon.REDSTONE_HIGH; }

        @Override
        public List<Component> getTooltipMessage() {
            var key = KEY + "output." + (inverted ? "inverted" : "normal");
            return List.of(Component.translatable(KEY + "output"),
                    Component.translatable(key).withStyle(ChatFormatting.WHITE),
                    Component.translatable(key + ".description").withStyle(ChatFormatting.GRAY));
        }
    }
}
