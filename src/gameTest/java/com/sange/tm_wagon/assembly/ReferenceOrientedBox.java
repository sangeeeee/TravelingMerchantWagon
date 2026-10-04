package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.physics.WagonPose;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Frozen pre-allocation-optimization solver, used only as a differential-test oracle. */
final class ReferenceOrientedBox {
    private static final double EPS=1e-7;
    private static final Vec3[] WORLD={new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)};
    private record Axis(Vec3 normal,double radius) {}
    public record Hit(double time,Vec3 normal) {}
    private final Vec3 centre,half;
    private final Vec3[] axes;
    private final Axis[] aabbAxes;
    private final AABB bounds;
    private final boolean aligned;

    public ReferenceOrientedBox(Vec3 centre,Vec3 half,Vec3 x,Vec3 y,Vec3 z) {
        this.centre=centre;this.half=half;axes=new Vec3[]{x,y,z};
        double rx=radius(WORLD[0]),ry=radius(WORLD[1]),rz=radius(WORLD[2]);
        bounds=new AABB(centre.x-rx,centre.y-ry,centre.z-rz,centre.x+rx,centre.y+ry,centre.z+rz);
        aligned=java.util.Arrays.stream(axes).allMatch(a->Math.max(Math.abs(a.x),Math.max(Math.abs(a.y),Math.abs(a.z)))>1-1e-10);
        aabbAxes=aligned?new Axis[]{new Axis(WORLD[0],rx),new Axis(WORLD[1],ry),new Axis(WORLD[2],rz)}:separatingAxes(WORLD);
    }
    public static ReferenceOrientedBox of(AABB box) {
        return new ReferenceOrientedBox(box.getCenter(),new Vec3(box.getXsize()/2,box.getYsize()/2,box.getZsize()/2),WORLD[0],WORLD[1],WORLD[2]);
    }
    public static ReferenceOrientedBox at(AABB local,WagonPose pose) {
        return new ReferenceOrientedBox(pose.point(local.getCenter()),new Vec3(local.getXsize()/2,local.getYsize()/2,local.getZsize()/2),
            pose.vector(WORLD[0]),pose.vector(WORLD[1]),pose.vector(WORLD[2]));
    }
    public AABB bounds() { return bounds; }
    public Vec3 centre() { return centre; }
    private ReferenceOrientedBox(ReferenceOrientedBox original,Vec3 delta) {
        centre=original.centre.add(delta);half=original.half;axes=original.axes;
        aabbAxes=original.aabbAxes;bounds=original.bounds.move(delta);aligned=original.aligned;
    }
    public ReferenceOrientedBox move(Vec3 delta) { return new ReferenceOrientedBox(this,delta); }
    public Vec3 closestPoint(Vec3 point) {
        Vec3 relative=point.subtract(centre),result=centre;
        double[] extent={half.x,half.y,half.z};
        for(int i=0;i<3;i++)result=result.add(axes[i].scale(Math.clamp(relative.dot(axes[i]),-extent[i],extent[i])));
        return result;
    }
    private double radius(Vec3 axis) {
        return half.x*Math.abs(axes[0].dot(axis))+half.y*Math.abs(axes[1].dot(axis))+half.z*Math.abs(axes[2].dot(axis));
    }
    private static double aabbRadius(AABB box,Vec3 axis) {
        return (box.getXsize()*Math.abs(axis.x)+box.getYsize()*Math.abs(axis.y)+box.getZsize()*Math.abs(axis.z))/2;
    }
    private Axis[] separatingAxes(Vec3[] other) {
        var list=new ArrayList<Axis>(15);
        for(Vec3 axis:axes)addAxis(list,axis);
        for(Vec3 axis:other)addAxis(list,axis);
        for(Vec3 a:axes)for(Vec3 b:other)addAxis(list,a.cross(b));
        return list.toArray(Axis[]::new);
    }
    private void addAxis(List<Axis> list,Vec3 vector) {
        double length=vector.length();if(length<1e-8)return;
        Vec3 normal=vector.scale(1/length);
        for(Axis axis:list)if(Math.abs(axis.normal.dot(normal))>1-1e-10)return;
        list.add(new Axis(normal,radius(normal)));
    }
    public boolean intersects(AABB box) {
        if(!bounds.intersects(box))return false;
        Vec3 relative=box.getCenter().subtract(centre);
        for(Axis axis:aabbAxes)if(Math.abs(relative.dot(axis.normal))>=axis.radius+aabbRadius(box,axis.normal)-EPS)return false;
        return true;
    }
    public boolean intersects(ReferenceOrientedBox box) {
        if(!bounds.intersects(box.bounds))return false;
        if(aligned)return box.intersects(bounds);
        Vec3 relative=box.centre.subtract(centre);
        for(Axis axis:box.aligned?aabbAxes:separatingAxes(box.axes))
            if(Math.abs(relative.dot(axis.normal))>=axis.radius+box.radius(axis.normal)-EPS)return false;
        return true;
    }
    /** Shortest correction for an existing overlap; null includes mere face contact. */
    public Vec3 penetration(AABB box) {
        if(!bounds.intersects(box))return null;
        Vec3 relative=box.getCenter().subtract(centre),normal=null;double minimum=Double.POSITIVE_INFINITY;
        for(Axis axis:aabbAxes) {
            double signed=relative.dot(axis.normal),overlap=axis.radius+aabbRadius(box,axis.normal)-Math.abs(signed);
            if(overlap<=EPS)return null;
            if(overlap<minimum) { minimum=overlap;normal=axis.normal.scale(signed<0?-1:1); }
        }
        return normal.scale(minimum+EPS);
    }
    public Hit sweep(AABB moving,Vec3 motion) {
        if(!bounds.intersects(moving.expandTowards(motion).inflate(EPS)))return null;
        return sweep(moving.getCenter().subtract(centre),motion,aabbAxes,a->aabbRadius(moving,a));
    }
    /** Sweep another rigid box relative to this stationary box, without changing either orientation. */
    public Hit sweep(ReferenceOrientedBox moving,Vec3 motion) {
        if(!bounds.intersects(moving.bounds.expandTowards(motion).inflate(EPS)))return null;
        if(aligned) {
            Hit reversed=moving.sweep(bounds,motion.scale(-1));
            return reversed==null?null:new Hit(reversed.time,reversed.normal.scale(-1));
        }
        return sweep(moving.centre.subtract(centre),motion,moving.aligned?aabbAxes:separatingAxes(moving.axes),moving::radius);
    }
    private Hit sweep(Vec3 relative,Vec3 motion,Axis[] tests,java.util.function.ToDoubleFunction<Vec3> movingRadius) {
        double enter=Double.NEGATIVE_INFINITY,exit=Double.POSITIVE_INFINITY,minimum=Double.POSITIVE_INFINITY;
        Vec3 contact=null,escape=null;
        for(Axis axis:tests) {
            Vec3 n=axis.normal;double r=axis.radius+movingRadius.applyAsDouble(n),s=relative.dot(n),v=motion.dot(n);
            double depth=r-Math.abs(s);
            if(depth<minimum) { minimum=depth;escape=n.scale(s<0?-1:1); }
            if(Math.abs(v)<1e-12) { if(Math.abs(s)>=r-EPS)return null;continue; }
            double a=(-r-s)/v,b=(r-s)/v;
            double first=Math.min(a,b),last=Math.max(a,b);
            if(first>enter) { enter=first;contact=n.scale(v>0?-1:1); }
            exit=Math.min(exit,last);
            if(enter>exit+EPS)return null;
        }
        // Existing overlaps must permit escape and tangent travel, rather than freezing the entity.
        if(minimum>EPS)return motion.dot(escape)<-EPS?new Hit(0,escape):null;
        if(enter<-EPS&&minimum>=-EPS&&minimum<=EPS&&motion.dot(escape)<-EPS)return new Hit(0,escape);
        if(contact==null||enter<-EPS||enter>1||exit<=EPS||motion.dot(contact)>=-EPS)return null;
        return new Hit(Math.max(0,enter),contact);
    }
    /** Minimum positive translation along a given direction that clears an overlap. */
    public double escapeDistance(ReferenceOrientedBox other,Vec3 direction) {
        if(!intersects(other))return 0;
        Vec3 relative=other.centre.subtract(centre);double distance=Double.POSITIVE_INFINITY;
        for(Axis axis:aligned?other.aabbAxes:other.aligned?aabbAxes:separatingAxes(other.axes)) {
            double v=direction.dot(axis.normal);if(Math.abs(v)<1e-12)continue;
            double s=relative.dot(axis.normal),r=radius(axis.normal)+other.radius(axis.normal);
            distance=Math.min(distance,(v>0?r-s:-r-s)/v);
        }
        return distance+EPS;
    }
}
