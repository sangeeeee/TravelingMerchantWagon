package com.sange.tm_wagon.physics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Immutable rigid box. World bounds are broad phase only; contacts use separating axes. */
public final class OrientedBox {
    private static final double EPS=1e-7;
    private static final Vec3[] WORLD={new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)};
    /** Share one immutable frame across differently sized parts with the same orientation. */
    public static final class Frame {
        private final Vec3[] axes,normals;
        private final boolean aligned;
        public Frame(Vec3 x,Vec3 y,Vec3 z) {
            axes=new Vec3[]{x,y,z};
            aligned=aligned(x)&&aligned(y)&&aligned(z);
            normals=aligned?WORLD:separatingNormals(axes,WORLD);
        }
        private static boolean aligned(Vec3 axis) {
            return Math.max(Math.abs(axis.x),Math.max(Math.abs(axis.y),Math.abs(axis.z)))>1-1e-10;
        }
        /** Exact equality preserves even the small transform differences at distant coordinates. */
        public boolean matches(Vec3 x,Vec3 y,Vec3 z) {
            return axes[0].equals(x)&&axes[1].equals(y)&&axes[2].equals(z);
        }
    }
    private static final Frame WORLD_FRAME=new Frame(WORLD[0],WORLD[1],WORLD[2]);
    public record Hit(double time,Vec3 normal) {}
    private final Vec3 centre,half;
    private final Frame frame;
    private final double[] aabbRadii;
    private final AABB bounds;

    public OrientedBox(Vec3 centre,Vec3 half,Vec3 x,Vec3 y,Vec3 z) {
        this(centre,half,new Frame(x,y,z));
    }
    public OrientedBox(Vec3 centre,Vec3 half,Frame frame) {
        this.centre=centre;this.half=half;this.frame=frame;
        double rx=radius(WORLD[0]),ry=radius(WORLD[1]),rz=radius(WORLD[2]);
        bounds=new AABB(centre.x-rx,centre.y-ry,centre.z-rz,centre.x+rx,centre.y+ry,centre.z+rz);
        if(frame.aligned)aabbRadii=new double[]{rx,ry,rz};
        else {
            aabbRadii=new double[frame.normals.length];
            for(int i=0;i<aabbRadii.length;i++)aabbRadii[i]=radius(frame.normals[i]);
        }
    }
    public static OrientedBox of(AABB box) {
        return new OrientedBox(box.getCenter(),new Vec3(box.getXsize()/2,box.getYsize()/2,box.getZsize()/2),WORLD_FRAME);
    }
    public static OrientedBox at(AABB local,WagonPose pose) {
        return new OrientedBox(pose.point(local.getCenter()),new Vec3(local.getXsize()/2,local.getYsize()/2,local.getZsize()/2),
            pose.vector(WORLD[0]),pose.vector(WORLD[1]),pose.vector(WORLD[2]));
    }
    public AABB bounds() { return bounds; }
    public Vec3 centre() { return centre; }
    private OrientedBox(OrientedBox original,Vec3 delta) {
        centre=original.centre.add(delta);half=original.half;frame=original.frame;
        aabbRadii=original.aabbRadii;bounds=original.bounds.move(delta);
    }
    public OrientedBox move(Vec3 delta) { return new OrientedBox(this,delta); }
    public Vec3 closestPoint(Vec3 point) {
        double dx=point.x-centre.x,dy=point.y-centre.y,dz=point.z-centre.z;
        double x=centre.x,y=centre.y,z=centre.z;
        for(int i=0;i<3;i++) {
            Vec3 axis=frame.axes[i];double extent=i==0?half.x:i==1?half.y:half.z;
            double distance=net.minecraft.util.Mth.clamp(dot(dx,dy,dz,axis),-extent,extent);
            x+=axis.x*distance;y+=axis.y*distance;z+=axis.z*distance;
        }
        return new Vec3(x,y,z);
    }
    private static double dot(double x,double y,double z,Vec3 axis) { return x*axis.x+y*axis.y+z*axis.z; }
    private double radius(Vec3 axis) {
        return half.x*Math.abs(frame.axes[0].dot(axis))+half.y*Math.abs(frame.axes[1].dot(axis))+half.z*Math.abs(frame.axes[2].dot(axis));
    }
    private static double aabbRadius(AABB box,Vec3 axis) {
        return (box.getXsize()*Math.abs(axis.x)+box.getYsize()*Math.abs(axis.y)+box.getZsize()*Math.abs(axis.z))/2;
    }
    private static Vec3[] separatingNormals(Vec3[] axes,Vec3[] other) {
        var list=new ArrayList<Vec3>(15);
        for(Vec3 a:axes)addNormal(list,a.x,a.y,a.z);
        for(Vec3 a:other)addNormal(list,a.x,a.y,a.z);
        for(Vec3 a:axes)for(Vec3 b:other)
            addNormal(list,a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x);
        return list.toArray(Vec3[]::new);
    }
    private static void addNormal(List<Vec3> list,double x,double y,double z) {
        double length=Math.sqrt(x*x+y*y+z*z);if(length<1e-8)return;
        double inverse=1/length;x*=inverse;y*=inverse;z*=inverse;
        // Indexed access avoids creating an iterator for every candidate axis.
        for(int i=0;i<list.size();i++)if(Math.abs(dot(x,y,z,list.get(i)))>1-1e-10)return;
        list.add(new Vec3(x,y,z));
    }
    public boolean intersects(AABB box) {
        if(!bounds.intersects(box))return false;
        double x=Mth.lerp(.5,box.minX,box.maxX)-centre.x,y=Mth.lerp(.5,box.minY,box.maxY)-centre.y,z=Mth.lerp(.5,box.minZ,box.maxZ)-centre.z;
        for(int i=0;i<frame.normals.length;i++) {
            Vec3 normal=frame.normals[i];
            if(Math.abs(dot(x,y,z,normal))>=aabbRadii[i]+aabbRadius(box,normal)-EPS)return false;
        }
        return true;
    }
    public boolean intersects(OrientedBox box) {
        if(!bounds.intersects(box.bounds))return false;
        if(frame.aligned)return box.intersects(bounds);
        double x=box.centre.x-centre.x,y=box.centre.y-centre.y,z=box.centre.z-centre.z;
        Vec3[] normals=box.frame.aligned?frame.normals:separatingNormals(frame.axes,box.frame.axes);
        for(int i=0;i<normals.length;i++) {
            Vec3 normal=normals[i];double r=box.frame.aligned?aabbRadii[i]:radius(normal);
            if(Math.abs(dot(x,y,z,normal))>=r+box.radius(normal)-EPS)return false;
        }
        return true;
    }
    /** Shortest correction for an existing overlap; null includes mere face contact. */
    public Vec3 penetration(AABB box) {
        if(!bounds.intersects(box))return null;
        double x=Mth.lerp(.5,box.minX,box.maxX)-centre.x,y=Mth.lerp(.5,box.minY,box.maxY)-centre.y,z=Mth.lerp(.5,box.minZ,box.maxZ)-centre.z;
        Vec3 normal=null;double minimum=Double.POSITIVE_INFINITY,sign=1;
        for(int i=0;i<frame.normals.length;i++) {
            Vec3 n=frame.normals[i];double signed=dot(x,y,z,n),overlap=aabbRadii[i]+aabbRadius(box,n)-Math.abs(signed);
            if(overlap<=EPS)return null;
            if(overlap<minimum) { minimum=overlap;normal=n;sign=signed<0?-1:1; }
        }
        return new Vec3(normal.x*sign*(minimum+EPS),normal.y*sign*(minimum+EPS),normal.z*sign*(minimum+EPS));
    }
    /** Same broad phase as expandTowards(motion).inflate(EPS), without two temporary boxes. */
    private boolean sweptBounds(AABB moving,Vec3 motion) {
        return bounds.minX<Math.max(moving.maxX,moving.maxX+motion.x)+EPS&&bounds.maxX>Math.min(moving.minX,moving.minX+motion.x)-EPS
            &&bounds.minY<Math.max(moving.maxY,moving.maxY+motion.y)+EPS&&bounds.maxY>Math.min(moving.minY,moving.minY+motion.y)-EPS
            &&bounds.minZ<Math.max(moving.maxZ,moving.maxZ+motion.z)+EPS&&bounds.maxZ>Math.min(moving.minZ,moving.minZ+motion.z)-EPS;
    }
    public Hit sweep(AABB moving,Vec3 motion) {
        if(!sweptBounds(moving,motion))return null;
        return sweep(Mth.lerp(.5,moving.minX,moving.maxX)-centre.x,Mth.lerp(.5,moving.minY,moving.maxY)-centre.y,Mth.lerp(.5,moving.minZ,moving.maxZ)-centre.z,
            motion,frame.normals,moving,null);
    }
    /** Sweep another rigid box relative to this stationary box, without changing either orientation. */
    public Hit sweep(OrientedBox moving,Vec3 motion) {
        if(!sweptBounds(moving.bounds,motion))return null;
        if(frame.aligned) {
            Hit reversed=moving.sweep(bounds,motion.scale(-1));
            return reversed==null?null:new Hit(reversed.time,reversed.normal.scale(-1));
        }
        return sweep(moving.centre.x-centre.x,moving.centre.y-centre.y,moving.centre.z-centre.z,
            motion,moving.frame.aligned?frame.normals:separatingNormals(frame.axes,moving.frame.axes),null,moving);
    }
    // Either an AABB or an OBB is supplied. No per-query callback or intermediate normal vectors.
    private Hit sweep(double x,double y,double z,Vec3 motion,Vec3[] normals,AABB aabb,OrientedBox obb) {
        double enter=Double.NEGATIVE_INFINITY,exit=Double.POSITIVE_INFINITY,minimum=Double.POSITIVE_INFINITY;
        Vec3 contact=null,escape=null;double contactSign=1,escapeSign=1;
        for(int i=0;i<normals.length;i++) {
            Vec3 n=normals[i];
            double r=(normals==frame.normals?aabbRadii[i]:radius(n))+(aabb!=null?aabbRadius(aabb,n):obb.radius(n));
            double s=dot(x,y,z,n),v=motion.dot(n),depth=r-Math.abs(s);
            if(depth<minimum) { minimum=depth;escape=n;escapeSign=s<0?-1:1; }
            if(Math.abs(v)<1e-12) { if(Math.abs(s)>=r-EPS)return null;continue; }
            double a=(-r-s)/v,b=(r-s)/v;
            double first=Math.min(a,b),last=Math.max(a,b);
            if(first>enter) { enter=first;contact=n;contactSign=v>0?-1:1; }
            exit=Math.min(exit,last);
            if(enter>exit+EPS)return null;
        }
        // Existing overlaps must permit escape and tangent travel, rather than freezing the entity.
        if(minimum>EPS)return motion.dot(escape)*escapeSign<-EPS?new Hit(0,escape.scale(escapeSign)):null;
        if(enter<-EPS&&minimum>=-EPS&&minimum<=EPS&&motion.dot(escape)*escapeSign<-EPS)return new Hit(0,escape.scale(escapeSign));
        if(contact==null||enter<-EPS||enter>1||exit<=EPS||motion.dot(contact)*contactSign>=-EPS)return null;
        return new Hit(Math.max(0,enter),contact.scale(contactSign));
    }
    /** Minimum positive translation along a given direction that clears an overlap. */
    public double escapeDistance(OrientedBox other,Vec3 direction) {
        if(!intersects(other))return 0;
        double x=other.centre.x-centre.x,y=other.centre.y-centre.y,z=other.centre.z-centre.z,distance=Double.POSITIVE_INFINITY;
        Vec3[] normals=frame.aligned?other.frame.normals:other.frame.aligned?frame.normals:separatingNormals(frame.axes,other.frame.axes);
        for(Vec3 n:normals) {
            double v=direction.dot(n);if(Math.abs(v)<1e-12)continue;
            double s=dot(x,y,z,n),r=radius(n)+other.radius(n);
            distance=Math.min(distance,(v>0?r-s:-r-s)/v);
        }
        return distance+EPS;
    }
}
