package com.sange.tm_wagon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sange.tm_wagon.cargo.StrawMatSleep;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;

/** A sleeping camera uses the same local anchor and render pose as the visible sleeper. */
@Mixin(Camera.class)
public abstract class WagonSleepCameraMixin {
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;
    @Shadow protected abstract void setPosition(Vec3 point);
    @Shadow protected abstract void setRotation(float yaw,float pitch,float roll);

    @WrapOperation(method="setup",at=@At(value="INVOKE",target="Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void tm_wagon$attachedEye(Camera camera,double x,double y,double z,Operation<Void> original,
            @Local(argsOnly=true) Entity entity,@Local(argsOnly=true) float partial) {
        if(entity instanceof LivingEntity sleeper&&sleeper.isSleeping()) {
            var pose=StrawMatSleep.sleepingPose(sleeper,partial);
            if(pose!=null) {
                Vec3 point=StrawMatSleep.sleepingPoint(sleeper,partial)
                    .add(pose.vector(new Vec3(0,Mth.lerp(partial,eyeHeightOld,eyeHeight),0)));
                // Third-person zoom still runs its ordinary obstruction checks,
                // but starts at the correctly transformed eye position.
                original.call(camera,point.x,point.y,point.z);return;
            }
        }
        original.call(camera,x,y,z);
    }

    @Inject(method="setup",at=@At("RETURN"))
    private void tm_wagon$sleepingView(BlockGetter level,Entity entity,boolean detached,boolean reverse,float partial,CallbackInfo ci) {
        if(detached||!(entity instanceof LivingEntity sleeper)||!sleeper.isSleeping())return;
        var pose=StrawMatSleep.sleepingPose(sleeper,partial);if(pose==null)return;
        // Compose the complete wagon frame with the native north-facing bed view.
        // Convert only at Camera's API boundary so combined slopes keep their roll.
        var orientation=new Quaternionf().rotationY((180-pose.yaw())*Mth.DEG_TO_RAD)
            .rotateZ(pose.roll()).rotateX(pose.pitch()).rotateY(StrawMatSleep.reversed(sleeper)?0:Mth.PI);
        Vector3f angles=orientation.getEulerAnglesYXZ(new Vector3f());
        setRotation(180-angles.y*Mth.RAD_TO_DEG,-angles.x*Mth.RAD_TO_DEG,-angles.z*Mth.RAD_TO_DEG);
        setPosition(StrawMatSleep.sleepingPoint(sleeper,partial)
            .add(pose.vector(new Vec3(0,Mth.lerp(partial,eyeHeightOld,eyeHeight)+.3,0))));
    }
}
