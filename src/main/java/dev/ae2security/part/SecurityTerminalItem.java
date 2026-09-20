package dev.ae2security.part;

import appeng.items.parts.PartItem;
import net.minecraft.world.item.Item;

public final class SecurityTerminalItem extends PartItem<SecurityTerminalPart> {
    public SecurityTerminalItem(Item.Properties properties) {
        super(properties, SecurityTerminalPart.class, SecurityTerminalPart::new);
    }
}
