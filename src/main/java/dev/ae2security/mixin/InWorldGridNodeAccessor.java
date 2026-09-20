package dev.ae2security.mixin;

import appeng.me.InWorldGridNode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = InWorldGridNode.class, remap = false)
public interface InWorldGridNodeAccessor {
    @Accessor("location") BlockPos ae2security$location();
}
