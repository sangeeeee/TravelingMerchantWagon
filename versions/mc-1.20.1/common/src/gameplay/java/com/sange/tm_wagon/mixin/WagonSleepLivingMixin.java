package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.cargo.StrawMatSleep;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class WagonSleepLivingMixin {
    @Shadow protected int lerpSteps;
    @Shadow protected int lerpHeadSteps;

    @Inject(method="lerpTo",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$boundPosition(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport,CallbackInfo ci) {
        var sleeper=(LivingEntity)(Object)this;
        if(sleeper.isSleeping()&&StrawMatSleep.matSleeper(sleeper)) {
            lerpSteps=0;StrawMatSleep.follow(sleeper);ci.cancel();
        }
    }
    @Inject(method="aiStep",at=@At("HEAD"))
    private void tm_wagon$clearIndependentInterpolation(CallbackInfo ci) {
        var sleeper=(LivingEntity)(Object)this;
        // Also discard interpolation queued before the sleep binding arrived.
        if(sleeper.isSleeping()&&StrawMatSleep.matSleeper(sleeper))lerpSteps=0;
    }
    @WrapOperation(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"))
    private void tm_wagon$attachedTravel(LivingEntity sleeper,Vec3 input,Operation<Void> original) {
        if(sleeper.isSleeping()&&StrawMatSleep.matSleeper(sleeper))StrawMatSleep.follow(sleeper);
        else original.call(sleeper,input);
    }
    @Inject(method="tick",at=@At("HEAD"))
    private void tm_wagon$followBeforeTick(CallbackInfo ci) { StrawMatSleep.follow((LivingEntity)(Object)this); }
    @Inject(method="tick",at=@At("TAIL"))
    private void tm_wagon$followAfterTick(CallbackInfo ci) { StrawMatSleep.follow((LivingEntity)(Object)this); }
    @Inject(method="setPosToBed",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$matPosition(BlockPos pos,CallbackInfo ci) {
        if((Object)this instanceof LivingEntity p) { var point=StrawMatSleep.sleepingPoint(p);if(point!=null) { p.setPos(point);ci.cancel(); } }
    }
    @Inject(method="getBedOrientation",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$matDirection(CallbackInfoReturnable<Direction> ci) {
        if((Object)this instanceof LivingEntity p) { var direction=StrawMatSleep.direction(p);if(direction!=null)ci.setReturnValue(direction); }
    }
    @Inject(method="stopSleeping",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$matWake(CallbackInfo ci) {
        if((Object)this instanceof LivingEntity p&&StrawMatSleep.finishWake(p)) {
            lerpSteps=0;lerpHeadSteps=0;ci.cancel();
        }
    }
}
