package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.physics.WagonCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lithium replaces CollisionGetter's default method with a Level implementation. */
@Mixin(value=Level.class,priority=900)
public abstract class WagonLithiumClearanceMixin {
    @Dynamic("Added by Lithium's entity.collisions.intersection.LevelMixin when enabled")
    @Inject(method="noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z",at=@At("RETURN"),cancellable=true,require=0)
    private void tm_wagon$lithiumClearance(Entity excluded,AABB area,CallbackInfoReturnable<Boolean> callback) {
        if(callback.getReturnValue()&&!WagonCollision.unobstructed((Level)(Object)this,excluded,area))callback.setReturnValue(false);
    }
}
