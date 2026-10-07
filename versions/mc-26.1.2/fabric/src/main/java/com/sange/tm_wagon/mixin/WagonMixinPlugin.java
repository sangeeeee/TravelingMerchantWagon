package com.sange.tm_wagon.mixin;
import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

/** Optional integration targets are selected without loading mod classes during bootstrap. */
public final class WagonMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String name) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target,String mixin) {
        if(target.startsWith("net.minecraft."))return true;
        if(target.equals("dev.nikrecs.swingthroughgrass.SwingThroughGrassAttackHandler"))return false;
        String mod=target.startsWith("com.github.tartaricacid.touhoulittlemaid.")?"touhou_little_maid":target.startsWith("tschipp.")?"carryon":target.startsWith("com.tiviacz.")?"travelersbackpack":target.startsWith("net.p3pp3rf1y.")?"sophisticatedbackpacks":target.startsWith("net.irisshaders.")?"iris":target.startsWith("dev.nikrecs.")?"swingthroughgrass":null;
        return mod!=null&&net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(mod);
    }
    public void acceptTargets(Set<String> ours,Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
}
