package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.compat.WagonShadowCasters;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Oculus / Embeddium can omit block entities from the shadow collection on Forge.
 * Submit missing wagon roots to Iris's normal pass; its shader settings still apply. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.shadows.ShadowRenderer",remap=false)
public abstract class WagonIrisShadowMixin {
    @Shadow public static List<BlockEntity> visibleBlockEntities;
    @Shadow public static Frustum FRUSTUM;

    @Inject(method="renderBlockEntities",at=@At("HEAD"),require=0,remap=false)
    private void tm_wagon$includeAssemblies(CallbackInfoReturnable<Integer> callback) {
        var mc=Minecraft.getInstance();
        if(mc.level==null||visibleBlockEntities==null)return;
        WagonShadowCasters.visit(mc.level,frame->{
            if(visibleBlockEntities.contains(frame))return;
            var renderer=mc.getBlockEntityRenderDispatcher().getRenderer(frame);
            if(renderer!=null&&renderer.shouldRender(frame,mc.gameRenderer.getMainCamera().getPosition())
                &&(FRUSTUM==null||FRUSTUM.isVisible(frame.getRenderBoundingBox())))visibleBlockEntities.add(frame);
        });
    }
}
