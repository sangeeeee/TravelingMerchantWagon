package com.sange.tm_wagon.client;

import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Resolve visible world outlines independently of wagon interaction volumes and collision solidity. */
public final class WagonWorldBlockPicking {
    public static HitResult resolve(Entity camera,double blockReach,float partial,HitResult selected) {
        if(!(selected instanceof EntityHitResult entityHit)||!(entityHit.getEntity() instanceof WagonEntity))return selected;
        Vec3 start=camera.getEyePosition(partial),end=start.add(camera.getViewVector(partial).scale(blockReach));
        // OUTLINE includes powder snow and plants even when their collision shape is empty.
        var block=camera.level().clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,camera));
        if(block.getType()!=HitResult.Type.BLOCK||start.distanceToSqr(block.getLocation())>blockReach*blockReach)return selected;
        double distance=start.distanceToSqr(block.getLocation());
        // Test the full ray, not a coarse entity bound or the target block's centre. A visible
        // corner is sufficient, while a wall/floor or another entity in front must still win.
        var obstruction=ProjectileUtil.getEntityHitResult(camera,start,end,new AABB(start,end).inflate(1),
            e->!e.isSpectator()&&e.isPickable(),distance);
        if(obstruction!=null&&start.distanceToSqr(obstruction.getLocation())+1e-8<distance)return selected;
        return block;
    }
    private WagonWorldBlockPicking() {}
}
