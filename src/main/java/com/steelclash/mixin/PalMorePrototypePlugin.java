package com.steelclash.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import net.neoforged.fml.loading.FMLLoader;

/** Applies only the development hook-order bridge for the reviewed PAL More Rotation release. */
public final class PalMorePrototypePlugin implements IMixinConfigPlugin {
    private static final String PROPERTY = "steelclash.palMoreRotationPrototype";
    private static final String MOD_ID = "playeranimationlibrarymorerotation";
    private static final boolean ENABLED = Boolean.getBoolean(PROPERTY) && reviewedAddonLoaded();

    private static boolean reviewedAddonLoaded() {
        return FMLLoader.getLoadingModList().getMods().stream().anyMatch(mod ->
                mod.getModId().equals(MOD_ID)
                        && (mod.getVersion().toString().equals("1.1.0")
                        || mod.getVersion().toString().startsWith("1.1.0+")));
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !mixinClassName.contains("PalMoreItemCaptureMixin")
                && !mixinClassName.contains("PalMoreItemApplyMixin") || ENABLED;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
