package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.cargo.StrawMatSleep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class WagonSleepLivingMixin {
    @Inject(method="setPosToBed",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$matPosition(BlockPos pos,CallbackInfo ci) {
        if((Object)this instanceof Player p) { var point=StrawMatSleep.sleepingPoint(p);if(point!=null) { p.setPos(point);ci.cancel(); } }
    }
    @Inject(method="getBedOrientation",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$matDirection(CallbackInfoReturnable<Direction> ci) {
        if((Object)this instanceof Player p) { var direction=StrawMatSleep.direction(p);if(direction!=null)ci.setReturnValue(direction); }
    }
    @Inject(method="stopSleeping",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$matWake(CallbackInfo ci) {
        if((Object)this instanceof Player p&&StrawMatSleep.finishWake(p))ci.cancel();
    }
}
