package com.sange.tm_wagon.entity;

import com.sange.tm_wagon.assembly.WagonGeometry;
import com.sange.tm_wagon.assembly.WagonPart;
import com.sange.tm_wagon.physics.WagonPose;
import com.sange.tm_wagon.physics.WagonPhysics;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One nearby query per moving tick; road mobs yield, rather than acting as solid walls. */
public final class WagonCrowd {
    // Collision data starts with the main cargo floor. Also protect occupants jumping above it.
    private static final AABB DECK=WagonGeometry.partBoxes(WagonPart.CARGO_BODY).getFirst();
    private final WagonEntity wagon;
    private final Set<Mob> candidates=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Mob> handled=Collections.newSetFromMap(new IdentityHashMap<>());
    private record SideChoice(int side,long expires) {}
    private final java.util.Map<java.util.UUID,SideChoice> sides=new java.util.HashMap<>();
    private double pushLimit;
    WagonCrowd(WagonEntity wagon) { this.wagon=wagon; }

    private boolean roadMob(Mob mob) {
        Vec3 feet=wagon.pose().local(mob.position());
        if(feet.y>=DECK.maxY-.1&&feet.x>=DECK.minX-.2&&feet.x<=DECK.maxX+.2
            &&feet.z>=DECK.minZ-.2&&feet.z<=DECK.maxZ+.2)return false;
        return mob.isAlive()&&!mob.isRemoved()&&!mob.noPhysics&&!mob.isSpectator()
            &&!mob.isPassengerOfSameVehicle(wagon)&&!wagon.hasHorse(mob.getUUID())
            &&!(mob instanceof AbstractHorse horse&&HorseHarness.attached(horse))
            &&!wagon.platform().carries(mob)&&!WagonPlatform.supportedByWagon(mob);
    }
    public void begin(Vec3 motion) {
        end();double speed=motion.horizontalDistance();
        if(speed<1e-6||wagon.level().isClientSide)return;
        sides.values().removeIf(choice->choice.expires<wagon.level().getGameTime());
        pushLimit=Math.min(.35,Math.max(.08,speed*2.5));
        AABB area=wagon.getBoundingBox().expandTowards(motion).inflate(.3);
        for(Entity entity:wagon.level().getEntities(wagon,area,e->e instanceof Mob&&e.isAlive()))
            if(entity.getRootVehicle() instanceof Mob mob&&roadMob(mob))candidates.add(mob);
    }
    public void end() { candidates.clear();handled.clear(); }
    /** Mounted mobs are moved with their root; boats and other wagons remain solid obstacles. */
    public boolean yields(Entity entity) { return candidates.contains(entity.getRootVehicle()); }

    private static double sideExtent(AABB box,Vec3 origin,Vec3 right,int side) {
        return side*box.getCenter().subtract(origin).dot(right)
            +(Math.abs(right.x)*box.getXsize()+Math.abs(right.z)*box.getZsize())/2;
    }
    private static double width(AABB box,Vec3 right) {
        return (Math.abs(right.x)*box.getXsize()+Math.abs(right.z)*box.getZsize())/2;
    }
    private static boolean touches(AABB mob,List<AABB> oldBoxes,List<AABB> boxes,List<AABB> horses) {
        for(int i=0;i<boxes.size();i++)if(boxes.get(i).minmax(oldBoxes.get(i)).intersects(mob))return true;
        for(AABB horse:horses)if(horse.intersects(mob))return true;
        return false;
    }
    private Vec3 outward(Mob mob,Vec3 right,int side,double boundary,WagonPose pose) {
        double x=mob.getBoundingBox().getCenter().subtract(pose.position()).dot(right);
        double distance=Math.min(pushLimit,Math.max(0,boundary+width(mob.getBoundingBox(),right)+.1-side*x));
        return right.scale(side*distance);
    }
    public void clear(WagonPose previous) {
        if(candidates.isEmpty()||handled.size()==candidates.size()||wagon.position().subtract(previous.position()).horizontalDistanceSqr()<1e-12)return;
        WagonPose current=wagon.pose();List<AABB> boxes=wagon.collisionBoxes(),oldBoxes=wagon.boxesAt(previous);
        var horses=new java.util.ArrayList<AABB>(2);
        for(int i=0;i<wagon.horseCapacity();i++) {
            var horse=wagon.horse(i);if(horse!=null)
                horses.add(horse.getBoundingBox().minmax(horse.getDimensions(horse.getPose()).makeBoundingBox(wagon.horsePosition(i))));
        }
        Vec3 right=new WagonPose(current.position(),current.yaw(),0,0).vector(new Vec3(1,0,0));
        double left=0,rightEdge=0;
        for(AABB box:boxes) { left=Math.max(left,sideExtent(box,current.position(),right,-1));rightEdge=Math.max(rightEdge,sideExtent(box,current.position(),right,1)); }
        for(AABB horse:horses) { left=Math.max(left,sideExtent(horse,current.position(),right,-1));rightEdge=Math.max(rightEdge,sideExtent(horse,current.position(),right,1)); }
        for(Mob mob:candidates) {
            if(handled.contains(mob)||!touches(mob.getBoundingBox(),oldBoxes,boxes,horses)||!roadMob(mob))continue;
            handled.add(mob);
            double x=mob.getBoundingBox().getCenter().subtract(current.position()).dot(right);
            var choice=sides.get(mob.getUUID());
            int side=choice!=null?choice.side:Math.abs(x)<.03?(mob.getId()%2==0?1:-1):x>0?1:-1;
            Vec3 requested=outward(mob,right,side,side>0?rightEdge:left,current);
            Vec3 allowed=WagonPlatform.allowedMovement(mob,wagon,requested);
            // Prefer the nearest side; use the other side if a wall leaves it more room.
            if(choice==null&&allowed.lengthSqr()<requested.lengthSqr()*.0625) {
                Vec3 other=WagonPlatform.allowedMovement(mob,wagon,outward(mob,right,-side,side>0?left:rightEdge,current));
                if(other.lengthSqr()>allowed.lengthSqr()+.0004) { allowed=other;side=-side; }
            }
            sides.put(mob.getUUID(),new SideChoice(side,wagon.level().getGameTime()+10));
            if(allowed.lengthSqr()<1e-10||!wagon.level().hasChunkAt(BlockPos.containing(mob.position().add(allowed))))continue;
            boolean grounded=mob.onGround();
            WagonPlatform.moveIgnoringWagon(mob,wagon,allowed);
            if(grounded) {
                var ground=WagonPhysics.ground(wagon.level(),mob.position(),.03,.05,mob.getBbWidth()/2);
                if(ground.present()&&!ground.forbidden())mob.setOnGround(true);
            }
            for(Entity passenger:mob.getPassengers())mob.positionRider(passenger);
            mob.hasImpulse=true;
        }
    }
}
