package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.physics.WagonCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CollisionGetter.class)
public interface WagonClearanceMixin {
    @Inject(method="noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z",at=@At("RETURN"),cancellable=true)
    private void tm_wagon$clearance(Entity excluded,AABB area,CallbackInfoReturnable<Boolean> callback) {
        if(callback.getReturnValue()&&(Object)this instanceof Level level&&!WagonCollision.unobstructed(level,excluded,area))callback.setReturnValue(false);
    }
}
