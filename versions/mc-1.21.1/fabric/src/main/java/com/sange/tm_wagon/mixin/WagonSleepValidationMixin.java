package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.cargo.StrawMatSleep;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Validate the reserved moving mat, rather than the world block under it. */
@Mixin(value=LivingEntity.class,priority=900)
public abstract class WagonSleepValidationMixin {
    @WrapMethod(method="checkBedExists")
    private boolean tm_wagon$matExists(Operation<Boolean> original) {
        var sleeper=(LivingEntity)(Object)this;
        return StrawMatSleep.matSleeper(sleeper)?StrawMatSleep.validSleep(sleeper):original.call();
    }
}
