package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.entity.HorseHarness;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class HorseTravelMixin {
    @Inject(method="travel",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$harnessMovement(Vec3 input,CallbackInfo callback) {
        if((Object)this instanceof AbstractHorse horse && HorseHarness.attached(horse)) {
            HorseHarness.recover(horse);
            if(HorseHarness.attached(horse)) {
                horse.setDeltaMovement(Vec3.ZERO);
                if(horse.level().isClientSide) {
                    double dx=horse.getX()-horse.xo,dz=horse.getZ()-horse.zo;
                    horse.walkAnimation.update((float)Math.min(1,Math.sqrt(dx*dx+dz*dz)*4),.4F);
                }
                callback.cancel();
            }
        }
    }
}
