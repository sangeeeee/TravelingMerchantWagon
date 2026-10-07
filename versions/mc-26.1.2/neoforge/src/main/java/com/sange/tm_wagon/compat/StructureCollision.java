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

/** No physical-structure API is available for 26.1.2. Keep the shared solver on its vanilla path. */
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
    private static Backend backend() { return NONE; }
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
        // The wagon pose does not change during this solve. Build its complete
        // colliders once, lazily: stationary structures need none of this work.
        List<OrientedBox> parts=null;
        for(Surface s:surfaces(wagon.level(),wagon.getBoundingBox().inflate(2))) {
            if(s.motion.lengthSqr()<1e-12)continue;
            if(parts==null)parts=wagon.motionCollidersAt(wagon.pose());
            double travel=s.motion.length();
            Vec3 direction=s.motion.normalize(),reverse=s.motion.scale(-1);double distance=0;
            OrientedBox previous=s.box.move(reverse);
            for(var part:parts) {
                if(s.box.intersects(part))distance=Math.max(distance,s.box.escapeDistance(part,direction));
                else {
                    var hit=previous.sweep(part,reverse);
                    if(hit!=null)distance=Math.max(distance,travel*(1-hit.time()));
                }
            }
            double missing=distance-push.dot(direction);
            if(missing>0)push=push.add(direction.scale(missing));
        }
        return push;
    }
    private StructureCollision() {}
}
