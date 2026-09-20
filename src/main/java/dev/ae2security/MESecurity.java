package dev.ae2security;

import appeng.api.networking.GridServices;
import appeng.api.parts.PartModels;
import dev.ae2security.gametest.SecurityGameTests;
import dev.ae2security.menu.SecurityMenu;
import dev.ae2security.network.SecurityPayloads;
import dev.ae2security.part.SecurityTerminalItem;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.*;

@Mod(MESecurity.ID)
public final class MESecurity {
    public static final String ID = "ae2security";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);

    public static final DeferredItem<SecurityTerminalItem> TERMINAL_ITEM = ITEMS.register("security_terminal", () ->
            new SecurityTerminalItem(new Item.Properties()));
    public static final DeferredHolder<MenuType<?>, MenuType<SecurityMenu>> SECURITY_MENU = MENUS.register("security_terminal", () ->
            IMenuTypeExtension.create((id, inventory, data) ->
                    new SecurityMenu(id, inventory, data.readBlockPos(), data.readEnum(net.minecraft.core.Direction.class))));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2security"))
                    .icon(() -> TERMINAL_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(TERMINAL_ITEM.get()))
                    .build());

    public MESecurity(IEventBus bus) {
        ITEMS.register(bus);
        MENUS.register(bus);
        CREATIVE_TABS.register(bus);
        PartModels.registerModels(SecurityTerminalPart.MODEL_OFF, SecurityTerminalPart.MODEL_ON);
        bus.addListener(this::setup);
        bus.addListener(SecurityPayloads::register);
        bus.addListener(this::gameTests);
        NeoForge.EVENT_BUS.addListener(this::login);
        NeoForge.EVENT_BUS.addListener(this::blockBreak);
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }
    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> GridServices.register(ISecurityGrid.class, SecurityGridService.class));
    }
    private void gameTests(RegisterGameTestsEvent event) { event.register(SecurityGameTests.class); }
    private void login(PlayerEvent.PlayerLoggedInEvent event) {
        var player = event.getEntity();
        if (player.getServer() != null) SecuritySavedData.get(player.getServer()).rememberPlayer(player.getUUID(), player.getGameProfile().getName());
    }
    private void blockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() == null) return;
        var blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (blockEntity instanceof appeng.blockentity.networking.CableBusBlockEntity cableBus
                && !SecurityBreak.allowBlockBreak(cableBus, event.getPlayer())) {
            event.setCanceled(true);
        }
    }
}
