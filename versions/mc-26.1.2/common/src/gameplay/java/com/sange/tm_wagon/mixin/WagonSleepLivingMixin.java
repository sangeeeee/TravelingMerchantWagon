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
    @Inject(method="startSleeping",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$startMatSleep(BlockPos bed,CallbackInfo ci) {
        var sleeper=(LivingEntity)(Object)this;var point=StrawMatSleep.sleepingPoint(sleeper);
        if(point==null)return;
        if(sleeper.isPassenger())sleeper.stopRiding();sleeper.setPos(point);sleeper.setPose(net.minecraft.world.entity.Pose.SLEEPING);
        sleeper.setSleepingPos(bed);sleeper.setDeltaMovement(Vec3.ZERO);sleeper.needsSync=true;ci.cancel();
    }
    @Shadow protected int lerpHeadSteps;
    @Inject(method="aiStep",at=@At("HEAD"))
    private void tm_wagon$clearIndependentInterpolation(CallbackInfo ci) {
        var sleeper=(LivingEntity)(Object)this;
        if(sleeper.isSleeping()&&StrawMatSleep.matSleeper(sleeper)) { sleeper.getInterpolation().cancel();StrawMatSleep.follow(sleeper); }
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
    @Inject(method="setPosToBed(Lnet/minecraft/core/BlockPos;)V",at=@At("HEAD"),cancellable=true)
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
            p.getInterpolation().cancel();lerpHeadSteps=0;ci.cancel();
        }
    }
}
