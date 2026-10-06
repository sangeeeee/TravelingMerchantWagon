package com.sange.tm_wagon.mixin;

import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sange.tm_wagon.physics.WagonCollision;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;

/** Lithium routes movement probes around Entity.collideBoundingBox. Keep its fast
 * world solver and apply our narrow phase to that accepted movement as well. */
@Mixin(value=Entity.class,priority=900)
public abstract class WagonLithiumMovementMixin {
    @Dynamic("Added by Lithium's entity.collisions.movement.EntityMixin when enabled")
    @WrapMethod(method="lithium$CollideMovement",require=0,remap=false)
    private static Vec3 tm_wagon$lithiumContact(Entity entity,Vec3 movement,AABB bounds,Level level,
            List<VoxelShape> shapes,LocalBooleanRef postponed,Operation<Vec3> original) {
        // Lithium reassigns bounds as it clips each axis; retain the initial box.
        Vec3 result=original.call(entity,movement,bounds,level,shapes,postponed);
        // A null ref means the normal collideBoundingBox entry already handles it.
        return postponed!=null&&!WagonCollision.vanillaOnly()
            ?WagonCollision.resolve(entity,movement,bounds,level,shapes,result):result;
    }
}
