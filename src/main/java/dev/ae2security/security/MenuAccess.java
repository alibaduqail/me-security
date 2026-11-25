package dev.ae2security.security;

import dev.ae2security.MESecurity;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;

/**
 * The permission an AE2 menu needs to open. Pack makers classify menus with the menu-type tags
 * {@code ae2security:owner_menus}, {@code ae2security:hardware_menus} and {@code ae2security:storage_menus};
 * any other AE2-based menu, such as an add-on machine screen, needs Build.
 */
public enum MenuAccess {
    /** Owner only: pattern management, network status and other sensitive screens. */
    OWNER,
    /** Build, judged by the hardware the menu belongs to. */
    HARDWARE,
    /** View; storage transactions are authorized separately. */
    STORAGE,
    /** Craft: choosing an amount for and confirming an autocrafting request. */
    CRAFTING_REQUEST,
    /** View: crafting CPU and job status. */
    STATUS,
    /** An unclassified menu; treated exactly like {@link #HARDWARE}. */
    FALLBACK;

    public static final TagKey<MenuType<?>> OWNER_MENUS = tag("owner_menus");
    public static final TagKey<MenuType<?>> HARDWARE_MENUS = tag("hardware_menus");
    public static final TagKey<MenuType<?>> STORAGE_MENUS = tag("storage_menus");

    private static TagKey<MenuType<?>> tag(String path) {
        return TagKey.create(Registries.MENU, ResourceLocation.fromNamespaceAndPath(MESecurity.ID, path));
    }

    /** Precedence: owner, then hardware, then storage, then AE2's crafting screens, then the Build fallback. */
    public static MenuAccess classify(boolean owner, boolean hardware, boolean storage, boolean craftingRequest,
            boolean status) {
        if (owner) return OWNER;
        if (hardware) return HARDWARE;
        if (storage) return STORAGE;
        if (craftingRequest) return CRAFTING_REQUEST;
        if (status) return STATUS;
        return FALLBACK;
    }

    /**
     * An id without a registered menu type carries no tags and falls back to Build. If a broken datapack left
     * the owner tag unloaded, unclassified menus fail closed to owner-only instead of opening to builders.
     */
    public static MenuAccess of(ResourceLocation type) {
        var holder = BuiltInRegistries.MENU.getOptional(type).map(BuiltInRegistries.MENU::wrapAsHolder).orElse(null);
        boolean ae2 = type.getNamespace().equals("ae2");
        String path = type.getPath();
        var access = classify(is(holder, OWNER_MENUS), is(holder, HARDWARE_MENUS), is(holder, STORAGE_MENUS),
                ae2 && (path.equals("craftamount") || path.equals("craftconfirm")),
                ae2 && (path.equals("craftingcpu") || path.equals("craftingstatus")));
        return access == FALLBACK && BuiltInRegistries.MENU.getTag(OWNER_MENUS).isEmpty() ? OWNER : access;
    }

    private static boolean is(Holder<MenuType<?>> holder, TagKey<MenuType<?>> tag) {
        return holder != null && holder.is(tag);
    }

    public boolean needsBuild() {
        return this == HARDWARE || this == FALLBACK;
    }
}
