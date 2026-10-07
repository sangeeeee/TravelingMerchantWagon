package com.sange.tm_wagon.mixin;
import com.sange.tm_wagon.client.SleepRenderAttachment;
import com.sange.tm_wagon.cargo.StrawMatSleep;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EntityRenderer.class)
public abstract class WagonSleepExtractMixin {
    @Inject(method="extractRenderState",at=@At("TAIL"))
    private void tm_wagon$extractSleep(Entity entity,EntityRenderState state,float partial,CallbackInfo ci) {
        var data=(SleepRenderAttachment)state;
        var pose=entity instanceof LivingEntity living&&living.isSleeping()&&living.deathTime==0?StrawMatSleep.sleepingPose(living,partial):null;
        if(pose==null) { data.tm_wagon$setSleepPose(null,Vec3.ZERO,0);return; }
        var living=(LivingEntity)entity;
        var offset=StrawMatSleep.sleepingPoint(living,partial).subtract(new Vec3(state.x,state.y,state.z));
        float rotation=living.getBedOrientation().toYRot()-180+(StrawMatSleep.reversed(living)?180:0);
        data.tm_wagon$setSleepPose(pose,offset,rotation);
    }
}
