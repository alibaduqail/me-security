package dev.ae2security.client;

import dev.ae2security.MESecurity;
import guideme.Guides;
import guideme.GuidesCommon;
import guideme.PageAnchor;
import guideme.indices.ItemIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.LoggerFactory;

/** Development-only preview, enabled by runGuideClient. Normal clients never run it. */
@EventBusSubscriber(modid = MESecurity.ID, value = Dist.CLIENT)
public final class GuidePreview {
    private static boolean opened;
    private GuidePreview() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (opened || !Boolean.getBoolean("ae2security.guidePreview")) return;
        var minecraft = Minecraft.getInstance();
        // GuideME 21.1.1 needs the world's registries and recipes, including for
        // linked AE2 pages with 3D scenes. Never open it from the title screen.
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || minecraft.getOverlay() != null) return;
        opened = true;

        var guide = Guides.getById(ResourceLocation.parse("ae2:guide"));
        var pageId = MESecurity.id("security_terminal.md");
        if (guide == null || !guide.pageExists(pageId)
                || guide.getNavigationTree().getNodeById(pageId) == null
                || !PageAnchor.page(pageId).equals(guide.getIndex(ItemIndex.class).get(MESecurity.id("security_terminal")))) {
            throw new IllegalStateException("Security Terminal guide is missing its page, navigation entry, or item link");
        }
        var page = guide.getPage(pageId);
        if (page == null) throw new IllegalStateException("Security Terminal guide failed to compile");
        LoggerFactory.getLogger(GuidePreview.class).info("Security Terminal GuideME page, navigation and item link loaded. Compiled text:\n{}",
                page.document().getTextContent());
        GuidesCommon.openGuide(minecraft.player, guide.getId(), PageAnchor.page(pageId));
    }
}
