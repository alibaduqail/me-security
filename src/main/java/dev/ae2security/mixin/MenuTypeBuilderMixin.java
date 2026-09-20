package dev.ae2security.mixin;

import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.locator.MenuHostLocator;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.ae2security.security.SecurityAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MenuTypeBuilder.class, remap = false)
public abstract class MenuTypeBuilderMixin {
    @Shadow private ResourceLocation id;

    @WrapOperation(method = "open", at = @At(value = "INVOKE",
            target = "Lappeng/menu/locator/MenuHostLocator;locate(Lnet/minecraft/world/entity/player/Player;Ljava/lang/Class;)Ljava/lang/Object;"))
    private Object ae2security$authorizeOpen(MenuHostLocator locator, Player player, Class<?> hostType,
            Operation<Object> original) {
        Object host = original.call(locator, player, hostType);
        return host != null && SecurityAccess.opening(player, host, id) ? host : null;
    }
}
