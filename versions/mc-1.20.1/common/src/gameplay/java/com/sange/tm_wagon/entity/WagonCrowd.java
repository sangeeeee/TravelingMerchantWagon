package com.sange.tm_wagon.entity;

import com.sange.tm_wagon.assembly.WagonGeometry;
import com.sange.tm_wagon.physics.WagonPose;
import com.sange.tm_wagon.physics.OrientedBox;
import com.sange.tm_wagon.physics.WagonPhysics;
import java.util.ArrayList;
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

/** Road eligibility is independent of the bounded, cosmetic side-clearing work. */
public final class WagonCrowd {
    private static final ThreadLocal<WagonEntity> EXCLUDED=new ThreadLocal<>();
    // Shared by transport, structure push and driving in the same wagon tick.
    private static final int PUSH_BUDGET=16;
    private final WagonEntity wagon;
    private final Set<Mob> candidates=Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Mob> ordered=new ArrayList<>();
    private static final class Contact {
        int side,seen,pushed=Integer.MIN_VALUE,yieldTick=Integer.MIN_VALUE,retry;
        long yieldTime=Long.MIN_VALUE;
    }
    private final java.util.Map<Mob,Contact> contacts=new IdentityHashMap<>();
    private int remaining=PUSH_BUDGET,cursor,tick;
    private double pushLimit;
    private OrientedBox underside;
    WagonCrowd(WagonEntity wagon) { this.wagon=wagon; }

    public static WagonEntity excludedWagon() { return EXCLUDED.get(); }
    private static void moveIgnoringWagon(Mob mob,WagonEntity wagon,Vec3 delta) {
        WagonEntity prior=EXCLUDED.get();EXCLUDED.set(wagon);
        try { mob.move(net.minecraft.world.entity.MoverType.SELF,delta); }
        finally { if(prior==null)EXCLUDED.remove();else EXCLUDED.set(prior); }
    }
    public void beginTick() {
        end();tick++;remaining=PUSH_BUDGET;
        contacts.entrySet().removeIf(e->e.getKey().isRemoved()||!e.getKey().isAlive()||e.getValue().seen<tick-10);
    }
    /** Also checked when a previously selected mob ticks, so boarding/sleep wins immediately. */
    private boolean protectedMob(Mob mob) {
        if(!mob.isAlive()||mob.isRemoved()||mob.noPhysics||mob.isSpectator()
            ||mob.isPassengerOfSameVehicle(wagon)||wagon.hasHorse(mob.getUUID())
            ||mob instanceof AbstractHorse horse&&HorseHarness.attached(horse)
            ||com.sange.tm_wagon.cargo.StrawMatSleep.attachedTo(mob,wagon))return true;
        AABB deck=WagonGeometry.partBoxes(wagon.cargoBody()).get(0);
        Vec3 feet=wagon.pose().local(mob.position());
        if(feet.y>=deck.maxY-.1&&feet.x>=deck.minX-.2&&feet.x<=deck.maxX+.2
            &&feet.z>=deck.minZ-.2&&feet.z<=deck.maxZ+.2)return true;
        // A mob may step onto the front footboard between vehicle and mob ticks.
        // Road-height mobs never need this more expensive support query.
        return feet.y>=deck.minY-.05&&WagonSupport.supports(wagon,mob);
    }
    private boolean roadMob(Mob mob) {
        return !protectedMob(mob)&&(underneath(mob)||!WagonSupport.supportedByWagon(mob));
    }
    private boolean underneath(Mob mob) {
        if(underside==null)return false;
        var deck=WagonGeometry.partBoxes(wagon.cargoBody()).get(0);
        return wagon.pose().local(mob.position()).y<deck.minY-.05&&underside.intersects(mob.getBoundingBox());
    }
    public void begin(Vec3 motion) {
        end();double speed=motion.horizontalDistance();
        if(speed<1e-6||wagon.level().isClientSide)return;
        var deck=WagonGeometry.partBoxes(wagon.cargoBody()).get(0);
        AABB chassis=deck;
        for(var box:WagonGeometry.partBoxes(wagon.cargoBody()))if(box.minY<deck.minY)chassis=chassis.minmax(box);
        double halfWidth=wagon.cargoBody().wheelHalfTrack()+.35;
        underside=OrientedBox.at(new AABB(-halfWidth,-.2,chassis.minZ,
            halfWidth,deck.minY-.05,chassis.maxZ),wagon.pose());
        pushLimit=Math.min(.35,Math.max(.08,speed*2.5));
        AABB area=wagon.getBoundingBox().expandTowards(motion).inflate(.3);
        for(int i=0;i<wagon.horseCapacity();i++) {
            var horse=wagon.horse(i);if(horse!=null)area=area.minmax(horse.getBoundingBox().minmax(
                horse.getDimensions(horse.getPose()).makeBoundingBox(wagon.horsePosition(i))).expandTowards(motion).inflate(.3));
        }
        for(Entity entity:wagon.level().getEntities(wagon,area,e->e instanceof Mob&&e.isAlive()))
            if(entity.getRootVehicle() instanceof Mob mob&&!candidates.contains(mob)&&roadMob(mob)) {
                candidates.add(mob);ordered.add(mob);
            }
    }
    public void end() { candidates.clear();ordered.clear();underside=null; }
    public boolean hasCandidates() { return !candidates.isEmpty(); }
    /** Only the current wagon movement uses this eligibility snapshot. */
    public boolean yields(Entity entity) { return candidates.contains(entity.getRootVehicle()); }
    /** Keep the mob's own tick from fighting a moving wall. Never disable world collision.
     * One tick of grace handles either entity tick order; boarding/deck contact takes priority. */
    public boolean yieldingContact(Entity entity) {
        if(!(entity.getRootVehicle() instanceof Mob mob))return false;
        Contact contact=contacts.get(mob);
        return contact!=null&&contact.yieldTick>=tick-1
            &&wagon.level().getGameTime()-contact.yieldTime<=1&&!protectedMob(mob);
    }
    private static double projection(AABB box,Vec3 origin,Vec3 right) {
        return ((box.minX+box.maxX)*.5-origin.x)*right.x+((box.minZ+box.maxZ)*.5-origin.z)*right.z;
    }
    private static double width(AABB box,Vec3 right) {
        return (Math.abs(right.x)*box.getXsize()+Math.abs(right.z)*box.getZsize())/2;
    }
    /** Per-movement data, never per candidate/part pair. */
    private record Sweep(OrientedBox before,OrientedBox after,AABB bounds,Vec3 motion) {}
    private static boolean touches(AABB mob,List<Sweep> sweeps,List<AABB> horses) {
        for(AABB horse:horses)if(horse.intersects(mob))return true;
        for(var sweep:sweeps)if(sweep.bounds.intersects(mob)
            &&(sweep.before.intersects(mob)||sweep.after.intersects(mob)||sweep.before.sweep(mob,sweep.motion)!=null))return true;
        return false;
    }
    private Vec3 outward(Mob mob,Vec3 right,int side,double boundary,WagonPose pose) {
        double x=projection(mob.getBoundingBox(),pose.position(),right);
        double distance=Math.min(pushLimit,Math.max(0,boundary+width(mob.getBoundingBox(),right)+.1-side*x));
        return right.scale(side*distance);
    }
    /** Run after the full movement, never once per physics substep. */
    public void clear(WagonPose previous) {
        if(candidates.isEmpty()||wagon.position().subtract(previous.position()).horizontalDistanceSqr()<1e-12)return;
        // All road candidates yield, even when this tick's physical push budget is spent.
        for(Mob mob:ordered) {
            Contact contact=contacts.computeIfAbsent(mob,ignored->new Contact());
            contact.seen=contact.yieldTick=tick;contact.yieldTime=wagon.level().getGameTime();
        }
        if(remaining==0)return;
        WagonPose current=wagon.pose();var boxes=wagon.colliders();var oldBoxes=wagon.collidersAt(previous);
        var sweeps=new ArrayList<Sweep>(boxes.size());
        var horses=new ArrayList<AABB>(2);
        for(int i=0;i<wagon.horseCapacity();i++) {
            var horse=wagon.horse(i);if(horse!=null)
                horses.add(horse.getBoundingBox().minmax(horse.getDimensions(horse.getPose()).makeBoundingBox(wagon.horsePosition(i))));
        }
        Vec3 right=new WagonPose(current.position(),current.yaw(),0,0).vector(new Vec3(1,0,0));
        double left=0,rightEdge=0;
        for(int i=0;i<boxes.size();i++) {
            var before=oldBoxes.get(i);var after=boxes.get(i);
            sweeps.add(new Sweep(before,after,before.bounds().minmax(after.bounds()),before.centre().subtract(after.centre())));
            double x=projection(after.bounds(),current.position(),right),half=width(after.bounds(),right);
            left=Math.max(left,half-x);rightEdge=Math.max(rightEdge,half+x);
        }
        for(AABB horse:horses) {
            double x=projection(horse,current.position(),right),half=width(horse,right);
            left=Math.max(left,half-x);rightEdge=Math.max(rightEdge,half+x);
        }
        int size=ordered.size(),start=Math.floorMod(cursor,size),visited=0;
        for(;visited<size&&remaining>0;visited++) {
            Mob mob=ordered.get((start+visited)%size);Contact contact=contacts.get(mob);
            if(contact.pushed==tick||contact.retry>tick||protectedMob(mob)
                ||(!underneath(mob)&&!touches(mob.getBoundingBox(),sweeps,horses)))continue;
            double x=projection(mob.getBoundingBox(),current.position(),right);
            int side=contact.side!=0?contact.side:Math.abs(x)<.03?(mob.getId()%2==0?1:-1):x>0?1:-1;
            Vec3 requested=outward(mob,right,side,side>0?rightEdge:left,current);
            // Already at the side boundary is success, not a blocked direction.
            // Entity.move deliberately ignores sub-pixel deltas; never reverse because of that.
            if(requested.horizontalDistanceSqr()<1e-10)continue;
            if(!wagon.level().hasChunkAt(BlockPos.containing(mob.position().add(requested))))continue;
            remaining--;contact.pushed=tick;
            boolean grounded=mob.onGround();Vec3 before=mob.position();
            // Entity.move is the authoritative terrain solver. Its actual result
            // replaces the old preview collision followed by the same full movement again.
            moveIgnoringWagon(mob,wagon,requested);
            double first=mob.position().subtract(before).horizontalDistanceSqr();
            if(first<requested.horizontalDistanceSqr()*.0625) {
                Vec3 other=outward(mob,right,-side,side>0?left:rightEdge,current);
                if(wagon.level().hasChunkAt(BlockPos.containing(mob.position().add(other)))) {
                    Vec3 middle=mob.position();moveIgnoringWagon(mob,wagon,other);
                    if(mob.position().subtract(middle).horizontalDistanceSqr()>first+.0004)side=-side;
                }
            }
            contact.side=side;
            if(mob.position().subtract(before).horizontalDistanceSqr()<1e-10)contact.retry=tick+4;
            if(grounded) {
                var ground=WagonPhysics.ground(wagon.level(),mob.position(),.03,.05,mob.getBbWidth()/2);
                if(ground.present()&&!ground.forbidden())mob.setOnGround(true);
            }
            for(Entity passenger:mob.getPassengers())mob.positionRider(passenger);
            if(mob.position().distanceToSqr(before)>1e-12)mob.hasImpulse=true;
        }
        cursor=(start+visited)%size;
    }
}
