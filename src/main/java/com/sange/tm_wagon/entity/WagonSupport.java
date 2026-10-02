package com.sange.tm_wagon.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Read-only foot contact for flight checks and road clearing; never moves an occupant. */
public final class WagonSupport {
    private static final double CONTACT_EPSILON=.06;

    public static boolean supports(WagonEntity wagon,Entity entity) {
        if(entity==wagon||entity instanceof WagonEntity||entity.isRemoved()||entity.isSpectator()
            ||entity.noPhysics||entity.isPassenger()||entity.getDeltaMovement().y>.1||wagon.falling()
            ||Math.abs(wagon.pitch())>=Math.toRadians(35)||Math.abs(wagon.roll())>=Math.toRadians(30)
            ||(entity instanceof Player player&&(player.getAbilities().flying||player.isSleeping())))return false;
        AABB feet=entity.getBoundingBox();
        for(AABB box:wagon.collisionBoxes())if(box.maxX>feet.minX+1e-5&&box.minX<feet.maxX-1e-5
            &&box.maxZ>feet.minZ+1e-5&&box.minZ<feet.maxZ-1e-5
            &&Math.abs(box.maxY-feet.minY)<=CONTACT_EPSILON)return true;
        return false;
    }

    /** Query only when checking a particular entity, rather than scanning every moving wagon. */
    public static boolean supportedByWagon(Entity entity) {
        for(WagonEntity wagon:WagonSpatialIndex.candidates(entity.level(),entity.getBoundingBox().inflate(CONTACT_EPSILON)))
            if(supports(wagon,entity))return true;
        return false;
    }

    private WagonSupport() {}
}
