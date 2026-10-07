package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.cargo.StrawMatSleep;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native maid beds snap to block centres; wagon mats retain their exact local anchor. */
@Pseudo
@Mixin(targets="com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidMiscManager",remap=false)
public abstract class WagonMaidSleepMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid;
    @Inject(method="onMaidSleep",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$followMat(CallbackInfo ci) {
        if(maid.isSleeping()&&StrawMatSleep.matSleeper(maid)) {
            StrawMatSleep.follow(maid);maid.setSilent(true);ci.cancel();
        }
    }
}
