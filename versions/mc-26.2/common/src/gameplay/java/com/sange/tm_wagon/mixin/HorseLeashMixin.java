package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.entity.HorseHarness;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Harness escrow owns its refund; vanilla leash stretch/death drops are suppressed. */
@Mixin(Leashable.class)
public interface HorseLeashMixin {
    @Inject(method="tickLeash",at=@At("HEAD"),cancellable=true)
    private static void tm_wagon$customHarness(net.minecraft.server.level.ServerLevel level,Entity entity,CallbackInfo callback) {
        if(entity instanceof AbstractHorse horse && HorseHarness.attached(horse))callback.cancel();
    }
}
