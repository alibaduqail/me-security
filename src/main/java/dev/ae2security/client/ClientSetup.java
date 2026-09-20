package dev.ae2security.client;

import appeng.api.util.AEColor;
import dev.ae2security.MESecurity;
import net.minecraft.util.FastColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = MESecurity.ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(MESecurity.SECURITY_MENU.get(), SecurityScreen::new);
    }

    @SubscribeEvent public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> FastColor.ARGB32.opaque(
                AEColor.TRANSPARENT.getVariantByTintIndex(tintIndex)), MESecurity.TERMINAL_ITEM.get());
    }
}
