package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.compat.WagonShadowCasters;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Include globally rendered assembly roots in Iris's extracted shadow states. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.shadows.ShadowRenderer",remap=false)
public abstract class WagonIrisShadowMixin {
    @Shadow public static Frustum FRUSTUM;
    @Inject(method="extractVisibleBlockEntities",at=@At("TAIL"),remap=false)
    private void tm_wagon$includeAssemblies(@Coerce Object sodium,float tick,Camera camera,
            LevelRenderState states,boolean lightOnly,CallbackInfo callback) {
        var mc=Minecraft.getInstance();if(mc.level==null||lightOnly)return;
        WagonShadowCasters.visit(mc.level,frame->{
            if(states.blockEntityRenderStates.stream().anyMatch(state->state.blockPos.equals(frame.getBlockPos())))return;
            var state=mc.getBlockEntityRenderDispatcher().tryExtractRenderState(frame,tick,null,true,FRUSTUM);
            if(state!=null)states.blockEntityRenderStates.add(state);
        });
    }
}
