package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.physics.WagonCollision;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Both client movement and server packet validation enter these same methods. */
@Mixin(Entity.class)
public abstract class WagonMovementMixin {
    @Inject(method="collideBoundingBox",at=@At("RETURN"),cancellable=true)
    private static void tm_wagon$orientedContact(Entity entity,Vec3 movement,AABB bounds,Level level,List<VoxelShape> shapes,CallbackInfoReturnable<Vec3> callback) {
        if(!WagonCollision.vanillaOnly())callback.setReturnValue(WagonCollision.resolve(entity,movement,bounds,level,shapes,callback.getReturnValue()));
    }
    @Inject(method="collide",at=@At("RETURN"),cancellable=true)
    private void tm_wagon$step(Vec3 movement,CallbackInfoReturnable<Vec3> callback) {
        callback.setReturnValue(WagonCollision.step((Entity)(Object)this,movement,callback.getReturnValue()));
    }
}
