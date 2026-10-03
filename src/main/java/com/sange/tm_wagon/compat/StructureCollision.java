package com.sange.tm_wagon.compat;

import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.OrientedBox;
import com.sange.tm_wagon.physics.WagonPose;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** Optional bridge. No Sable types are linked on the normal, dependency-free path. */
public final class StructureCollision {
    public record Surface(OrientedBox box,boolean forbidden,Object owner,Vec3 motion) {}
    public interface Scope extends AutoCloseable { @Override void close(); }
    interface Backend {
        default List<Surface> surfaces(Level level,AABB bounds) { return List.of(); }
        default Scope begin(WagonEntity wagon) { return ()->{}; }
        default WagonPose transport(WagonEntity wagon) { return wagon.pose(); }
        default void remember(WagonEntity wagon) {}
        default boolean grounded(Entity entity) { return false; }
        default void updateGroundFlags(Entity entity) {}
    }
    private static final Backend NONE=new Backend() {};
    private static Backend cached;
    private static final class Loaded { private static final Backend INSTANCE=new SableCollision(); }
    private static Backend backend() {
        if(cached!=null)return cached;
        ModList mods=ModList.get();if(mods==null)return NONE;
        return cached=mods.isLoaded("sable")?Loaded.INSTANCE:NONE;
    }
    public static boolean available() { return backend()!=NONE; }
    public static List<Surface> surfaces(Level level,AABB bounds) { return backend().surfaces(level,bounds); }
    public static Scope begin(WagonEntity wagon) { return backend().begin(wagon); }
    public static WagonPose transport(WagonEntity wagon) { return backend().transport(wagon); }
    public static void remember(WagonEntity wagon) { backend().remember(wagon); }
    public static boolean grounded(Entity entity) { return backend().grounded(entity); }
    public static void updateGroundFlags(Entity entity) { backend().updateGroundFlags(entity); }
    public static boolean clear(Level level,AABB volume) {
        return !available()||clear(level,OrientedBox.of(volume));
    }
    public static boolean clear(Level level,OrientedBox volume) {
        for(Surface s:surfaces(level,volume.bounds()))if(s.box.intersects(volume))return false;
        return true;
    }
    /** A swept probe gives the true rotated surface height, not its enclosing AABB top. */
    public static double top(Surface surface,Vec3 point,double up,double down,double halfWidth) {
        AABB probe=new AABB(point.x-halfWidth,point.y+up,point.z-halfWidth,
            point.x+halfWidth,point.y+up+.001,point.z+halfWidth);
        if(surface.box.intersects(probe))return Double.NEGATIVE_INFINITY;
        double distance=up+down;
        var hit=surface.box.sweep(probe,new Vec3(0,-distance,0));
        return hit!=null&&hit.normal().y>.5?point.y+up-distance*hit.time():Double.NEGATIVE_INFINITY;
    }
    public static Vec3 push(WagonEntity wagon) {
        Vec3 push=Vec3.ZERO;
        for(Surface s:surfaces(wagon.level(),wagon.getBoundingBox().inflate(2))) {
            if(s.motion.lengthSqr()<1e-12)continue;
            Vec3 direction=s.motion.normalize();double distance=0;
            for(var part:wagon.motionCollidersAt(wagon.pose())) {
                if(s.box.intersects(part))distance=Math.max(distance,s.box.escapeDistance(part,direction));
                else {
                    var hit=s.box.move(s.motion.scale(-1)).sweep(part,s.motion.scale(-1));
                    if(hit!=null)distance=Math.max(distance,s.motion.length()*(1-hit.time()));
                }
            }
            double missing=distance-push.dot(direction);
            if(missing>0)push=push.add(direction.scale(missing));
        }
        return push;
    }
    private StructureCollision() {}
}
