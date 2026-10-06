package com.sange.tm_wagon.physics;

import net.minecraft.world.phys.Vec3;

/** Fixed centre of mass; passengers and cargo never affect this transform. */
public final class WagonPose {
    public static final Vec3 CENTRE=new Vec3(0,1.5,0);
    private final Vec3 position,xAxis,yAxis,zAxis;
    private final float yaw,pitch,roll;
    public WagonPose(Vec3 position,float yaw,float pitch,float roll) {
        this.position=position;this.yaw=yaw;this.pitch=pitch;this.roll=roll;
        double angle=Math.toRadians(yaw-180);
        xAxis=rotate(new Vec3(1,0,0),pitch,roll,angle);
        yAxis=rotate(new Vec3(0,1,0),pitch,roll,angle);
        zAxis=rotate(new Vec3(0,0,1),pitch,roll,angle);
    }
    public Vec3 position() { return position; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public float roll() { return roll; }
    public static Vec3 rotate(Vec3 p,double pitch,double roll,double yaw) {
        double cp=Math.cos(pitch),sp=Math.sin(pitch),cr=Math.cos(roll),sr=Math.sin(roll);
        Vec3 a=new Vec3(p.x,p.y*cp-p.z*sp,p.y*sp+p.z*cp);
        Vec3 b=new Vec3(a.x*cr-a.y*sr,a.x*sr+a.y*cr,a.z);
        double cy=Math.cos(yaw),sy=Math.sin(yaw);
        return new Vec3(b.x*cy-b.z*sy,b.y,b.x*sy+b.z*cy);
    }
    public Vec3 vector(Vec3 p) { return new Vec3(xAxis.x*p.x+yAxis.x*p.y+zAxis.x*p.z,xAxis.y*p.x+yAxis.y*p.y+zAxis.y*p.z,xAxis.z*p.x+yAxis.z*p.y+zAxis.z*p.z); }
    public Vec3 point(Vec3 local) { return position.add(CENTRE).add(vector(local.subtract(CENTRE))); }
    public Vec3 local(Vec3 world) { Vec3 p=world.subtract(position).subtract(CENTRE);return new Vec3(p.dot(xAxis),p.dot(yAxis),p.dot(zAxis)).add(CENTRE); }
    public Vec3 forward() { return zAxis.scale(-1); }
    @Override public boolean equals(Object other) { return other instanceof WagonPose p&&position.equals(p.position)&&yaw==p.yaw&&pitch==p.pitch&&roll==p.roll; }
    @Override public int hashCode() { return java.util.Objects.hash(position,yaw,pitch,roll); }
}
