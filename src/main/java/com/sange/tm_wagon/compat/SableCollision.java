package com.sange.tm_wagon.compat;

import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.OrientedBox;
import com.sange.tm_wagon.physics.WagonPhysics;
import com.sange.tm_wagon.physics.WagonPose;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Reads nearby plot blocks through Sable; the wagon remains an ordinary world entity. */
final class SableCollision implements StructureCollision.Backend {
    /** Immutable broad-phase data, reused by all wheel and movement queries in this scope. */
    private record SurfaceBounds(StructureCollision.Surface surface,AABB swept) {}
    private record Snapshot(Level level,AABB bounds,List<SurfaceBounds> surfaces) {}
    private record Tracking(java.lang.ref.WeakReference<SubLevel> level,Pose3d pose) {}
    private final ThreadLocal<Snapshot> snapshot=new ThreadLocal<>();
    private final Map<WagonEntity,Tracking> tracking=new WeakHashMap<>();
    @Override public StructureCollision.Scope begin(WagonEntity wagon) {
        Snapshot previous=snapshot.get();
        AABB area=wagon.getBoundingBox().inflate(4).expandTowards(0,-2,0);
        snapshot.set(new Snapshot(wagon.level(),area,collect(wagon.level(),area)));
        return ()->{ if(previous==null)snapshot.remove();else snapshot.set(previous); };
    }
    @Override public List<StructureCollision.Surface> surfaces(Level level,AABB area) {
        Snapshot cached=snapshot.get();
        List<SurfaceBounds> all=cached!=null&&cached.level==level&&contains(cached.bounds,area)?cached.surfaces:collect(level,area);
        if(all.isEmpty())return List.of();
        AABB query=area.inflate(1e-6);
        List<StructureCollision.Surface> result=null;
        for(var entry:all)if(entry.swept.intersects(query)) {
            if(result==null)result=new ArrayList<>();
            result.add(entry.surface);
        }
        return result==null?List.of():result;
    }
    private static boolean contains(AABB outer,AABB inner) {
        return outer.minX<=inner.minX&&outer.minY<=inner.minY&&outer.minZ<=inner.minZ&&outer.maxX>=inner.maxX&&outer.maxY>=inner.maxY&&outer.maxZ>=inner.maxZ;
    }
    /** One collector-local frame, shared only when the computed axes match exactly. */
    private static final class BoxFactory {
        private final Pose3dc pose;
        private OrientedBox.Frame frame;
        private BoxFactory(Pose3dc pose) { this.pose=pose; }
        private OrientedBox box(AABB local) {
            Vec3 localCentre=local.getCenter(),centre=pose.transformPosition(localCentre);
            Vec3 x=pose.transformPosition(localCentre.add(1,0,0)).subtract(centre);
            Vec3 y=pose.transformPosition(localCentre.add(0,1,0)).subtract(centre);
            Vec3 z=pose.transformPosition(localCentre.add(0,0,1)).subtract(centre);
            Vec3 half=new Vec3(local.getXsize()*x.length()/2,local.getYsize()*y.length()/2,local.getZsize()*z.length()/2);
            x=x.normalize();y=y.normalize();z=z.normalize();
            if(frame==null||!frame.matches(x,y,z))frame=new OrientedBox.Frame(x,y,z);
            return new OrientedBox(centre,half,frame);
        }
    }
    private static List<SurfaceBounds> collect(Level level,AABB area) {
        var result=new ArrayList<SurfaceBounds>();
        AABB search=area.inflate(2);
        for(SubLevel sub:Sable.HELPER.getAllIntersecting(level,new BoundingBox3d(search))) {
            if(sub.isRemoved()||sub.getPlot().contains(area.getCenter()))continue;
            var pose=sub.logicalPose();var scale=pose.scale();
            if(Math.min(Math.abs(scale.x()),Math.min(Math.abs(scale.y()),Math.abs(scale.z())))<1e-6)continue;
            var local=new BoundingBox3d(search).transformInverse(pose);
            var boxes=new BoxFactory(pose);
            var plot=sub.getPlot().getBoundingBox();
            int x0=Math.max(Mth.floor(local.minX)-1,plot.minX()),x1=Math.min(Mth.floor(local.maxX)+1,plot.maxX());
            int y0=Math.max(Mth.floor(local.minY)-1,plot.minY()),y1=Math.min(Mth.floor(local.maxY)+1,plot.maxY());
            int z0=Math.max(Mth.floor(local.minZ)-1,plot.minZ()),z1=Math.min(Mth.floor(local.maxZ)+1,plot.maxZ());
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++) {
                var chunk=sub.getPlot().getChunk(sub.getPlot().toLocal(new net.minecraft.world.level.ChunkPos(x>>4,z>>4)));
                if(chunk==null)continue;
                for(int y=y0;y<=y1;y++) {
                    BlockPos pos=new BlockPos(x,y,z);var state=chunk.getBlockState(pos);
                    if(state.isAir())continue;
                    boolean forbidden=state.is(BlockTags.FENCES)||state.is(BlockTags.WALLS);
                    for(AABB shape:state.getCollisionShape(level,pos,CollisionContext.empty()).toAabbs()) {
                        AABB localBox=shape.move(pos);OrientedBox world=boxes.box(localBox);
                        Vec3 motion=world.centre().subtract(sub.lastPose().transformPosition(localBox.getCenter()));
                        AABB swept=world.bounds().minmax(world.bounds().move(motion.scale(-1)));
                        if(swept.intersects(area))
                            result.add(new SurfaceBounds(new StructureCollision.Surface(world,forbidden,sub,motion),swept));
                    }
                }
            }
        }
        return result;
    }
    @Override public WagonPose transport(WagonEntity wagon) {
        Tracking t=tracking.get(wagon);WagonPose old=wagon.pose();
        SubLevel sub=t==null?null:t.level.get();
        if(sub==null||sub.isRemoved())return old;
        Pose3dc now=sub.logicalPose();
        Vec3 position=now.transformPosition(t.pose.transformPositionInverse(old.position()));
        Vec3 localForward=t.pose.transformNormalInverse(old.forward());
        Vec3 forward=now.transformNormal(localForward);
        float yaw=(float)Math.toDegrees(Math.atan2(-forward.x,forward.z));
        return new WagonPose(position,yaw,old.pitch(),old.roll());
    }
    @Override public void remember(WagonEntity wagon) {
        Map<SubLevel,Integer> votes=new IdentityHashMap<>();
        for(int i=0;i<4;i++) {
            Vec3 foot=wagon.wheelCentre(i,wagon.pose()).add(0,-WagonPhysics.radius(i),0);
            double best=Double.NEGATIVE_INFINITY;SubLevel selected=null;
            AABB area=new AABB(foot.add(-.12,-.3,-.12),foot.add(.12,.3,.12));
            for(var surface:surfaces(wagon.level(),area)) {
                double top=StructureCollision.top(surface,foot,.3,.3,.10);
                if(!surface.forbidden()&&top>best) { best=top;selected=(SubLevel)surface.owner(); }
            }
            var ground=WagonPhysics.ground(wagon.level(),foot,.3,.3,.10);
            if(selected!=null&&best>=ground.height()-.01)votes.merge(selected,1,Integer::sum);
        }
        SubLevel best=null;int count=1;
        for(var entry:votes.entrySet())if(entry.getValue()>count) { best=entry.getKey();count=entry.getValue(); }
        if(best==null)tracking.remove(wagon);else tracking.put(wagon,new Tracking(new java.lang.ref.WeakReference<>(best),new Pose3d(best.logicalPose())));
    }
    @Override public boolean grounded(Entity entity) {
        if(!(entity instanceof EntityMovementExtension ext))return false;
        var info=ext.sable$getCollisionInfo();
        return info!=null&&info.trackingSubLevel!=null&&info.verticalCollisionBelow;
    }
    @Override public void updateGroundFlags(Entity entity) {
        if(entity instanceof EntityMovementExtension ext) {
            var info=ext.sable$getCollisionInfo();
            if(info!=null) { info.verticalCollision=entity.verticalCollision;info.verticalCollisionBelow=entity.verticalCollisionBelow; }
        }
    }
}
