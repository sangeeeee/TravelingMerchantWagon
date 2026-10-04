package com.sange.tm_wagon.entity;

import com.sange.tm_wagon.assembly.WagonGeometry;
import com.sange.tm_wagon.assembly.WagonPart;
import com.sange.tm_wagon.physics.WagonPose;
import com.sange.tm_wagon.physics.OrientedBox;
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
    private static final ThreadLocal<WagonEntity> EXCLUDED=new ThreadLocal<>();
    // Collision data starts with the main cargo floor. Also protect occupants jumping above it.
    private final WagonEntity wagon;
    private final Set<Mob> candidates=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Mob> handled=Collections.newSetFromMap(new IdentityHashMap<>());
    private record SideChoice(int side,long expires) {}
    private final java.util.Map<java.util.UUID,SideChoice> sides=new java.util.HashMap<>();
    private double pushLimit;
    private OrientedBox underside;
    WagonCrowd(WagonEntity wagon) { this.wagon=wagon; }

    /** Road clearing ignores this wagon only; terrain and other vehicles remain solid. */
    public static WagonEntity excludedWagon() { return EXCLUDED.get(); }
    private static <T> T ignoringWagon(WagonEntity wagon,java.util.function.Supplier<T> action) {
        WagonEntity prior=EXCLUDED.get();EXCLUDED.set(wagon);
        try { return action.get(); }
        finally { if(prior==null)EXCLUDED.remove();else EXCLUDED.set(prior); }
    }
    private static Vec3 allowedMovement(Entity entity,WagonEntity wagon,Vec3 delta) {
        return ignoringWagon(wagon,()->Entity.collideBoundingBox(entity,delta,entity.getBoundingBox(),wagon.level(),
            wagon.level().getEntityCollisions(entity,entity.getBoundingBox().expandTowards(delta))));
    }
    private static void moveIgnoringWagon(Entity entity,WagonEntity wagon,Vec3 delta) {
        ignoringWagon(wagon,()->{entity.move(net.minecraft.world.entity.MoverType.SELF,delta);return null;});
    }

    private boolean roadMob(Mob mob) {
        if(com.sange.tm_wagon.cargo.StrawMatSleep.attachedTo(mob,wagon))return false;
        AABB deck=WagonGeometry.partBoxes(wagon.cargoBody()).getFirst();
        Vec3 feet=wagon.pose().local(mob.position());
        if(feet.y>=deck.maxY-.1&&feet.x>=deck.minX-.2&&feet.x<=deck.maxX+.2
            &&feet.z>=deck.minZ-.2&&feet.z<=deck.maxZ+.2)return false;
        return mob.isAlive()&&!mob.isRemoved()&&!mob.noPhysics&&!mob.isSpectator()
            &&!mob.isPassengerOfSameVehicle(wagon)&&!wagon.hasHorse(mob.getUUID())
            &&!(mob instanceof AbstractHorse horse&&HorseHarness.attached(horse))
            &&(underneath(mob)||!WagonSupport.supportedByWagon(mob));
    }
    private boolean underneath(Mob mob) {
        if(underside==null)return false;
        var deck=WagonGeometry.partBoxes(wagon.cargoBody()).getFirst();
        return wagon.pose().local(mob.position()).y<deck.minY-.05&&underside.intersects(mob.getBoundingBox());
    }
    public void begin(Vec3 motion) {
        end();double speed=motion.horizontalDistance();
        if(speed<1e-6||wagon.level().isClientSide)return;
        var deck=WagonGeometry.partBoxes(wagon.cargoBody()).getFirst();
        AABB chassis=deck;
        for(var box:WagonGeometry.partBoxes(wagon.cargoBody()))if(box.minY<deck.minY)chassis=chassis.minmax(box);
        double halfWidth=wagon.cargoBody().wheelHalfTrack()+.35;
        underside=OrientedBox.at(new AABB(-halfWidth,-.2,chassis.minZ,
            halfWidth,deck.minY-.05,chassis.maxZ),wagon.pose());
        sides.values().removeIf(choice->choice.expires<wagon.level().getGameTime());
        pushLimit=Math.min(.35,Math.max(.08,speed*2.5));
        AABB area=wagon.getBoundingBox().expandTowards(motion).inflate(.3);
        // Steering can put a horse outside the shafts' broad bounds. Include its
        // current and requested footprint in the single road-mob query.
        for(int i=0;i<wagon.horseCapacity();i++) {
            var horse=wagon.horse(i);if(horse!=null)area=area.minmax(horse.getBoundingBox().minmax(
                horse.getDimensions(horse.getPose()).makeBoundingBox(wagon.horsePosition(i))).expandTowards(motion).inflate(.3));
        }
        for(Entity entity:wagon.level().getEntities(wagon,area,e->e instanceof Mob&&e.isAlive()))
            if(entity.getRootVehicle() instanceof Mob mob&&roadMob(mob))candidates.add(mob);
    }
    public void end() { candidates.clear();handled.clear();underside=null; }
    /** Mounted mobs are moved with their root; boats and other wagons remain solid obstacles. */
    public boolean yields(Entity entity) { return candidates.contains(entity.getRootVehicle()); }

    private static double sideExtent(AABB box,Vec3 origin,Vec3 right,int side) {
        return side*box.getCenter().subtract(origin).dot(right)
            +(Math.abs(right.x)*box.getXsize()+Math.abs(right.z)*box.getZsize())/2;
    }
    private static double width(AABB box,Vec3 right) {
        return (Math.abs(right.x)*box.getXsize()+Math.abs(right.z)*box.getZsize())/2;
    }
    private static boolean touches(AABB mob,List<OrientedBox> oldBoxes,List<OrientedBox> boxes,List<AABB> horses) {
        for(int i=0;i<boxes.size();i++) {
            var before=oldBoxes.get(i);var after=boxes.get(i);
            if(before.intersects(mob)||after.intersects(mob)||before.sweep(mob,before.centre().subtract(after.centre()))!=null)return true;
        }
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
        WagonPose current=wagon.pose();var boxes=wagon.colliders();var oldBoxes=wagon.collidersAt(previous);
        var horses=new java.util.ArrayList<AABB>(2);
        for(int i=0;i<wagon.horseCapacity();i++) {
            var horse=wagon.horse(i);if(horse!=null)
                horses.add(horse.getBoundingBox().minmax(horse.getDimensions(horse.getPose()).makeBoundingBox(wagon.horsePosition(i))));
        }
        Vec3 right=new WagonPose(current.position(),current.yaw(),0,0).vector(new Vec3(1,0,0));
        double left=0,rightEdge=0;
        for(var box:boxes) { left=Math.max(left,sideExtent(box.bounds(),current.position(),right,-1));rightEdge=Math.max(rightEdge,sideExtent(box.bounds(),current.position(),right,1)); }
        for(AABB horse:horses) { left=Math.max(left,sideExtent(horse,current.position(),right,-1));rightEdge=Math.max(rightEdge,sideExtent(horse,current.position(),right,1)); }
        for(Mob mob:candidates) {
            if(handled.contains(mob)||(!underneath(mob)&&!touches(mob.getBoundingBox(),oldBoxes,boxes,horses))||!roadMob(mob))continue;
            handled.add(mob);
            double x=mob.getBoundingBox().getCenter().subtract(current.position()).dot(right);
            var choice=sides.get(mob.getUUID());
            int side=choice!=null?choice.side:Math.abs(x)<.03?(mob.getId()%2==0?1:-1):x>0?1:-1;
            Vec3 requested=outward(mob,right,side,side>0?rightEdge:left,current);
            Vec3 allowed=allowedMovement(mob,wagon,requested);
            // Keep the chosen side until it is blocked. Slow acceleration may reach a wall
            // in several short pushes, so even an existing choice must be reconsidered.
            if(allowed.lengthSqr()<requested.lengthSqr()*.0625) {
                Vec3 other=allowedMovement(mob,wagon,outward(mob,right,-side,side>0?left:rightEdge,current));
                if(other.lengthSqr()>allowed.lengthSqr()+.0004) { allowed=other;side=-side; }
            }
            sides.put(mob.getUUID(),new SideChoice(side,wagon.level().getGameTime()+10));
            if(allowed.lengthSqr()<1e-10||!wagon.level().hasChunkAt(BlockPos.containing(mob.position().add(allowed))))continue;
            boolean grounded=mob.onGround();
            moveIgnoringWagon(mob,wagon,allowed);
            if(grounded) {
                var ground=WagonPhysics.ground(wagon.level(),mob.position(),.03,.05,mob.getBbWidth()/2);
                if(ground.present()&&!ground.forbidden())mob.setOnGround(true);
            }
            for(Entity passenger:mob.getPassengers())mob.positionRider(passenger);
            mob.hasImpulse=true;
        }
    }
}
