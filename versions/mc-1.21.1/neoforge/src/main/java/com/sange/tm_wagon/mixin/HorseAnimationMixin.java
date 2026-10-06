package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.entity.HorseHarness;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Harnessed horses retain vanilla idle behaviour, but use the ridden gait while pulling. */
@Mixin(AbstractHorse.class)
public abstract class HorseAnimationMixin {
    @Inject(method={"setEating","setStanding"},at=@At("HEAD"),cancellable=true)
    private void tm_wagon$preventIdlePoseWhilePulling(boolean enabled,CallbackInfo callback) {
        if(enabled&&HorseHarness.pulling((AbstractHorse)(Object)this))callback.cancel();
    }
    @Inject(method="tick",at={@At("HEAD"),@At("TAIL")})
    private void tm_wagon$forwardDrivingPose(CallbackInfo callback) {
        HorseHarness.updateDrivingPose((AbstractHorse)(Object)this);
    }
    // A horse can start pulling halfway through its eating/rearing animation. Suppress
    // the residual blend immediately on both sides, including delayed flag packets.
    @Inject(method={"getEatAnim","getStandAnim"},at=@At("HEAD"),cancellable=true)
    private void tm_wagon$drivingAnimation(float partialTick,CallbackInfoReturnable<Float> callback) {
        if(HorseHarness.pulling((AbstractHorse)(Object)this))callback.setReturnValue(0F);
    }
}
