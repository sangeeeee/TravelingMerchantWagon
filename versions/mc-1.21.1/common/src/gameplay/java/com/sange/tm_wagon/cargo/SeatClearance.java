package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.assembly.AssemblyFrameBlockEntity;
import com.sange.tm_wagon.compat.StructureCollision;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.entity.WagonSpatialIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Match vanilla passenger placement; riders remain upright when their wagon tilts. */
public final class SeatClearance {
    public static AABB body(Entity rider,Entity vehicle,Vec3 anchor) {
        // startRiding resets crouching/swimming to standing. Do not admit a rider
        // using a temporarily shorter box which grows immediately after boarding.
        return rider.getDimensions(Pose.STANDING).makeBoundingBox(
            anchor.subtract(rider.getVehicleAttachmentPoint(vehicle==null?rider:vehicle)));
    }
    public static boolean clear(CargoHold hold,Entity rider,Entity vehicle,Vec3 anchor) {
        var level=hold.owner().cargoLevel();
        AABB body=body(rider,vehicle,anchor).deflate(.0001);
        if(!level.getWorldBorder().isWithinBounds(body)||!StructureCollision.clear(level,body))return false;
        // Below the local seat plane, the seated legs intentionally overlap the
        // host. Clip host geometry in wagon coordinates so slopes do not turn
        // the seat surface itself into an obstruction. Fixed seat/rail supports
        // can overlap seated limbs; cargo, covers and roofs must remain clear.
        var pose=hold.owner().cargoPose();double seatY=pose.local(anchor).y+.001;
        var obstacles=new java.util.ArrayList<>(hold.boxes());
        obstacles.addAll(hold.cover().boxes(hold.owner().cargoBody()));
        obstacles.addAll(hold.canopy().boxes(hold.owner().cargoBody()));
        for(AABB local:obstacles)if(local.maxY>seatY) {
            var above=new AABB(local.minX,Math.max(local.minY,seatY),local.minZ,local.maxX,local.maxY,local.maxZ);
            if(com.sange.tm_wagon.physics.OrientedBox.at(above,pose).intersects(body))return false;
        }
        var context=CollisionContext.of(rider);
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(body.minX,body.minY,body.minZ),BlockPos.containing(body.maxX,body.maxY,body.maxZ))) {
            if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos))return false;
            if(AssemblyFrameBlockEntity.find(level,pos)==hold.owner())continue;
            for(AABB box:level.getBlockState(pos).getCollisionShape(level,pos,context).toAabbs())
                if(box.move(pos).intersects(body))return false;
        }
        for(WagonEntity wagon:WagonSpatialIndex.candidates(level,body)) {
            if(wagon==hold.owner())continue;
            for(var box:wagon.colliders())if(box.intersects(body))return false;
        }
        return true;
    }
    private SeatClearance() {}
}
