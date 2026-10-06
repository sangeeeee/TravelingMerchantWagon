package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.physics.OrientedBox;
import com.sange.tm_wagon.physics.WagonPose;
import java.util.Random;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public final class CollisionAllocationGameTests {
    private record Pair(OrientedBox box,ReferenceOrientedBox reference) {}
    private static Vec3 vector(Random r,double scale) {
        return new Vec3((r.nextDouble()-.5)*scale,(r.nextDouble()-.5)*scale,(r.nextDouble()-.5)*scale);
    }
    private static Pair pair(Vec3 centre,Vec3 half,WagonPose pose) {
        Vec3 x=pose.vector(new Vec3(1,0,0)),y=pose.vector(new Vec3(0,1,0)),z=pose.vector(new Vec3(0,0,1));
        return new Pair(new OrientedBox(centre,half,new OrientedBox.Frame(x,y,z)),new ReferenceOrientedBox(centre,half,x,y,z));
    }
    private static void sameVector(GameTestHelper h,Vec3 a,Vec3 b,String label) {
        h.assertTrue(a==null?b==null:b!=null&&a.distanceTo(b)<1e-9,label+": "+a+" / "+b);
    }
    private static void sameHit(GameTestHelper h,OrientedBox.Hit a,ReferenceOrientedBox.Hit b,String label) {
        h.assertTrue(a==null?b==null:b!=null&&Math.abs(a.time()-b.time())<1e-10,label+": "+a+" / "+b);
        if(a!=null)sameVector(h,a.normal(),b.normal(),label+" normal");
    }
    private static void compare(GameTestHelper h,Pair a,Pair b,AABB world,Vec3 motion,String label) {
        h.assertTrue(a.box.intersects(world)==a.reference.intersects(world),label+" world overlap");
        h.assertTrue(a.box.intersects(b.box)==a.reference.intersects(b.reference),label+" rigid overlap");
        sameHit(h,a.box.sweep(world,motion),a.reference.sweep(world,motion),label+" world sweep");
        sameHit(h,a.box.sweep(b.box,motion),a.reference.sweep(b.reference,motion),label+" rigid sweep");
        sameVector(h,a.box.penetration(world),a.reference.penetration(world),label+" escape");
        sameVector(h,a.box.closestPoint(b.box.centre()),a.reference.closestPoint(b.reference.centre()),label+" closest point");
        double actual=a.box.escapeDistance(b.box,motion),expected=a.reference.escapeDistance(b.reference,motion);
        h.assertTrue(Double.compare(actual,expected)==0||Math.abs(actual-expected)<1e-9,label+" directed escape");
    }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void low_allocation_solver_matches_reference_contacts(GameTestHelper h) {
        Random r=new Random(0x5741474f4eL);
        for(int i=0;i<4000;i++) {
            Vec3 centre=vector(r,12).add(i%3==0?29_000_000:0,0,i%3==0?-29_000_000:0);
            var pose=new WagonPose(Vec3.ZERO,i%4==0?180:(float)(r.nextDouble()*360),i%5==0?0:(float)r.nextDouble(),i%5==0?0:(float)-r.nextDouble());
            var otherPose=new WagonPose(Vec3.ZERO,i%2==0?180:(float)(r.nextDouble()*360),0,i%2==0?0:.17F);
            Vec3 half=new Vec3(i%7==0?1.0/256:r.nextDouble()+.1,r.nextDouble()+.1,r.nextDouble()*3+.1);
            Pair a=pair(centre,half,pose),b=pair(centre.add(vector(r,5)),new Vec3(.3,.9,.4),otherPose);
            Vec3 delta=vector(r,2);a=new Pair(a.box.move(delta),a.reference.move(delta));
            Vec3 motion=i%11==0?Vec3.ZERO:i%11==1?new Vec3(0,-2,0):vector(r,8);
            compare(h,a,b,b.box.bounds(),motion,"sample "+i);
        }
        // Exact face contact, epsilon-sized gaps, overlap escape and tangent jump/fall.
        for(float angle:new float[]{180,135,90,179.99999F})for(double gap:new double[]{0,1e-8,1e-7,-1e-8,-1e-6}) {
            var pose=new WagonPose(Vec3.ZERO,angle,0,0);
            Pair wall=pair(Vec3.ZERO,new Vec3(.1,4,4),pose);
            Pair actor=pair(pose.vector(new Vec3(.4+gap,0,0)),new Vec3(.3,.9,.3),pose);
            for(Vec3 motion:new Vec3[]{Vec3.ZERO,new Vec3(0,1,0),new Vec3(0,-1,0),pose.vector(new Vec3(-1,0,0)),pose.vector(new Vec3(1,0,0))})
                compare(h,wall,actor,actor.box.bounds(),motion,"contact "+angle+" / "+gap);
        }
        // Sharing a frame must not share radii between different shapes.
        var pose=new WagonPose(Vec3.ZERO,135,.2F,.1F);
        Vec3 x=pose.vector(new Vec3(1,0,0)),y=pose.vector(new Vec3(0,1,0)),z=pose.vector(new Vec3(0,0,1));
        var frame=new OrientedBox.Frame(x,y,z);
        for(double size:new double[]{.001,.2,1,5}) {
            Vec3 half=new Vec3(size,.4,1.2);
            Pair a=new Pair(new OrientedBox(Vec3.ZERO,half,frame),new ReferenceOrientedBox(Vec3.ZERO,half,x,y,z));
            Pair b=pair(new Vec3(size,.2,.3),new Vec3(.3,.9,.3),pose);
            compare(h,a,b,b.box.bounds(),new Vec3(-1,-.2,.5),"shared frame "+size);
        }
        h.succeed();
    }
}
