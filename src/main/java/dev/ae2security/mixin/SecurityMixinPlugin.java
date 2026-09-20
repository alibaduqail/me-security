package dev.ae2security.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Adds optional compatibility mixins only when their target mod is on the runtime classpath. */
public final class SecurityMixinPlugin implements IMixinConfigPlugin {
    private static final String AE2WTLIB_MAGNET =
            "de/mari_023/ae2wtlib/wct/magnet_card/MagnetHandler.class";

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return SecurityMixinPlugin.class.getClassLoader().getResource(AE2WTLIB_MAGNET) == null
                ? List.of()
                : List.of("AE2wtlibMagnetHandlerMixin");
    }

    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {}
}
