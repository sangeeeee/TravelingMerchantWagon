package com.sange.tm_wagon.physics;

import com.sange.tm_wagon.entity.WagonCrowd;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.entity.WagonSpatialIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Continuous wagon contacts; ordinary terrain retains Minecraft's movement solver. */
public final class WagonCollision {
    private static final ThreadLocal<Boolean> VANILLA=ThreadLocal.withInitial(()->false);
    private static final double SKIN=1e-7;
    private static final ThreadLocal<Recording> RECORDING=new ThreadLocal<>();
    /** Only the contacts belonging to the accepted movement may change momentum. */
    public static final class Contacts {
        private final List<Vec3> planes=new ArrayList<>(6);
        private boolean wagon,worldVertical;
        public boolean wagon() { return wagon; }
        public boolean worldVertical() { return worldVertical; }
        public boolean floor() { return planes.stream().anyMatch(n->n.y>.5); }
        public boolean vertical() { return planes.stream().anyMatch(n->Math.abs(n.y)>.5); }
        private void add(Vec3 normal) {
            for(Vec3 plane:planes)if(plane.dot(normal)>1-1e-8)return;
            planes.add(normal);
        }
        private void world(Vec3 requested,Vec3 accepted) {
            if(Math.abs(requested.x-accepted.x)>SKIN)add(new Vec3(requested.x>accepted.x?-1:1,0,0));
            if(Math.abs(requested.y-accepted.y)>SKIN) { add(new Vec3(0,requested.y>accepted.y?-1:1,0));worldVertical=true; }
            if(Math.abs(requested.z-accepted.z)>SKIN)add(new Vec3(0,0,requested.z>accepted.z?-1:1));
        }
        private void append(Contacts other) {
            if(other==null)return;
            for(Vec3 normal:other.planes)add(normal);
            wagon|=other.wagon;worldVertical|=other.worldVertical;
        }
        public Vec3 velocity(Vec3 velocity) {
            // Revisit corner planes because projecting onto one inclined face can
            // introduce a small inward component at an earlier face.
            for(int pass=0;pass<3;pass++) {
                boolean changed=false;
                for(Vec3 normal:planes) {
                    // Native landing callbacks need the incoming vertical speed
                    // to implement slime/bed bounce, and clear it for solid blocks.
                    if(worldVertical&&Math.abs(normal.y)>1-1e-8)continue;
                    double inward=velocity.dot(normal);
                    if(inward<-1e-10) { velocity=velocity.subtract(normal.scale(inward));changed=true; }
                }
                if(!changed)break;
            }
            return velocity;
        }
    }
    public static final class Recording {
        private final Entity entity;
        private final Recording previous;
        private final IdentityHashMap<Vec3,Contacts> results=new IdentityHashMap<>();
        private Recording(Entity entity,Recording previous) { this.entity=entity;this.previous=previous; }
        public Contacts finish(Vec3 result) {
            if(previous==null)RECORDING.remove();else RECORDING.set(previous);
            return results.get(result);
        }
        private void remember(Vec3 result,Contacts contacts) { results.put(result,contacts); }
    }
    public static Recording record(Entity entity,Vec3 movement) {
        if(entity.noPhysics||WagonSpatialIndex.candidates(entity.level(),entity.getBoundingBox()
            .expandTowards(movement).inflate(entity.maxUpStep()+.26)).isEmpty())return null;
        var recording=new Recording(entity,RECORDING.get());RECORDING.set(recording);return recording;
    }
    public static boolean vanillaOnly() { return VANILLA.get(); }
    public static boolean eligible(Entity entity,WagonEntity wagon) {
        return entity!=wagon&&wagon!=WagonCrowd.excludedWagon()&&!wagon.isRemoved()
            &&(entity==null||!entity.isSpectator()&&entity.canCollideWith(wagon)
                &&!(entity instanceof AbstractHorse&&wagon.hasHorse(entity.getUUID())));
    }
    public static List<OrientedBox> nearby(Level level,Entity entity,AABB area) {
        var boxes=new ArrayList<OrientedBox>();
        for(WagonEntity wagon:WagonSpatialIndex.candidates(level,area))if(eligible(entity,wagon))
            for(OrientedBox box:wagon.colliders())if(box.bounds().intersects(area))boxes.add(box);
        return boxes;
    }
    public static boolean unobstructed(Level level,Entity entity,AABB area) {
        for(WagonEntity wagon:WagonSpatialIndex.candidates(level,area))if(eligible(entity,wagon)&&wagon.intersects(area))return false;
        return true;
    }
    private static Vec3 world(Entity entity,Vec3 motion,AABB box,Level level,List<VoxelShape> shapes) {
        boolean prior=VANILLA.get();VANILLA.set(true);
        try { return Entity.collideBoundingBox(entity,motion,box,level,shapes); }
        finally { if(prior)VANILLA.set(true);else VANILLA.remove(); }
    }
    public static Vec3 resolve(Entity entity,Vec3 requested,AABB start,Level level,List<VoxelShape> shapes,Vec3 vanilla) {
        if(vanillaOnly()||(entity!=null&&entity.noPhysics)||requested.lengthSqr()<1e-20)return vanilla;
        var recording=RECORDING.get();
        Contacts contacts=recording!=null&&recording.entity==entity?new Contacts():null;
        var boxes=nearby(level,entity,start.expandTowards(requested).inflate(.26));
        if(boxes.isEmpty()) {
            if(contacts!=null) { contacts.world(requested,vanilla);recording.remember(vanilla,contacts); }
            return vanilla;
        }
        Vec3 moved=Vec3.ZERO;AABB current=start;
        // A moving wall can overlap an unseated occupant. Correct only a small local overlap,
        // with terrain validation; this never transports occupants with the platform.
        for(int iteration=0;iteration<4;iteration++) {
            Vec3 correction=null;
            for(OrientedBox obstacle:boxes) {
                Vec3 candidate=obstacle.penetration(current);
                if(candidate!=null&&candidate.lengthSqr()<.25*.25
                    &&(correction==null||candidate.lengthSqr()<correction.lengthSqr()))correction=candidate;
            }
            if(correction==null)break;
            Vec3 accepted=world(entity,correction,current,level,shapes);
            if(accepted.lengthSqr()<SKIN*SKIN)break;
            if(contacts!=null) { contacts.wagon=true;contacts.add(correction.normalize());contacts.world(correction,accepted); }
            moved=moved.add(accepted);current=current.move(accepted);
        }
        Vec3 remaining=requested;
        for(int iteration=0;iteration<6&&remaining.lengthSqr()>1e-18;iteration++) {
            Vec3 clipped=world(entity,remaining,current,level,shapes);
            if(contacts!=null)contacts.world(remaining,clipped);
            remaining=clipped;
            OrientedBox.Hit first=null;
            for(OrientedBox obstacle:boxes) {
                var hit=obstacle.sweep(current,remaining);
                if(hit!=null&&(first==null||hit.time()<first.time()))first=hit;
            }
            if(first==null) { moved=moved.add(remaining);break; }
            if(contacts!=null) { contacts.wagon=true;contacts.add(first.normal()); }
            Vec3 travel=remaining.scale(first.time());moved=moved.add(travel);current=current.move(travel);
            remaining=remaining.scale(1-first.time());
            double inward=remaining.dot(first.normal());
            if(inward>=-1e-12)break;
            remaining=remaining.subtract(first.normal().scale(inward));
        }
        if(contacts!=null)recording.remember(moved,contacts);
        return moved;
    }
    /** Vanilla step candidates cannot see a rotated box's surfaces. Try one bounded lift. */
    public static Vec3 step(Entity entity,Vec3 requested,Vec3 result) {
        double height=entity.maxUpStep();
        if(height<=0||entity.noPhysics||entity.isPassenger()||requested.horizontalDistanceSqr()<1e-12
            ||result.horizontalDistanceSqr()>=requested.horizontalDistanceSqr()-1e-10
            ||!entity.onGround()&&!(requested.y<0&&result.y>requested.y+1e-7))return result;
        AABB start=entity.getBoundingBox();Level level=entity.level();
        AABB area=start.expandTowards(requested.x,height,requested.z).inflate(SKIN);
        if(nearby(level,entity,area).isEmpty())return result;
        var shapes=level.getEntityCollisions(entity,area);
        Vec3 up=Entity.collideBoundingBox(entity,new Vec3(0,height,0),start,level,shapes);
        if(up.y<=SKIN)return result;
        Vec3 across=Entity.collideBoundingBox(entity,new Vec3(requested.x,0,requested.z),start.move(up),level,shapes);
        if(across.horizontalDistanceSqr()<=result.horizontalDistanceSqr()+1e-9)return result;
        Vec3 down=Entity.collideBoundingBox(entity,new Vec3(0,Math.min(0,requested.y)-up.y,0),start.move(up).move(across),level,shapes);
        Vec3 stepped=up.add(across).add(down);
        if(stepped.y>height+SKIN)return result;
        var recording=RECORDING.get();
        if(recording!=null&&recording.entity==entity) {
            var contacts=new Contacts();contacts.append(recording.results.get(up));
            contacts.append(recording.results.get(across));contacts.append(recording.results.get(down));
            recording.remember(stepped,contacts);
        }
        return stepped;
    }
    private WagonCollision() {}
}
