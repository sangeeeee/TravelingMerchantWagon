package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.client.WagonWorldBlockPicking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class WagonWorldBlockPickingMixin {
    @Inject(method="pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",at=@At("RETURN"),cancellable=true)
    private static void tm_wagon$visibleWorldOutline(Entity camera,double blockReach,double entityReach,float partial,CallbackInfoReturnable<HitResult> callback) {
        callback.setReturnValue(WagonWorldBlockPicking.resolve(camera,blockReach,partial,callback.getReturnValue()));
    }
}
