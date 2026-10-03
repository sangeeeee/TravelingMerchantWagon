package com.sange.tm_wagon.physics;

import com.sange.tm_wagon.entity.WagonCrowd;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.entity.WagonSpatialIndex;
import java.util.ArrayList;
import java.util.List;
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
        var boxes=nearby(level,entity,start.expandTowards(requested).inflate(.26));
        if(boxes.isEmpty())return vanilla;
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
            moved=moved.add(accepted);current=current.move(accepted);
        }
        Vec3 remaining=requested;
        for(int iteration=0;iteration<6&&remaining.lengthSqr()>1e-18;iteration++) {
            remaining=world(entity,remaining,current,level,shapes);
            OrientedBox.Hit first=null;
            for(OrientedBox obstacle:boxes) {
                var hit=obstacle.sweep(current,remaining);
                if(hit!=null&&(first==null||hit.time()<first.time()))first=hit;
            }
            if(first==null) { moved=moved.add(remaining);break; }
            Vec3 travel=remaining.scale(first.time());moved=moved.add(travel);current=current.move(travel);
            remaining=remaining.scale(1-first.time());
            double inward=remaining.dot(first.normal());
            if(inward>=-1e-12)break;
            remaining=remaining.subtract(first.normal().scale(inward));
        }
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
        return stepped.y<=height+SKIN?stepped:result;
    }
    private WagonCollision() {}
}
