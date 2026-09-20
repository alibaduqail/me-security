package dev.ae2security.part;

import java.util.List;

import appeng.items.parts.PartItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class SecurityTerminalItem extends PartItem<SecurityTerminalPart> {
    public SecurityTerminalItem(Item.Properties properties) {
        super(properties, SecurityTerminalPart.class, SecurityTerminalPart::new);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.ae2security.security_terminal"));
    }
}
