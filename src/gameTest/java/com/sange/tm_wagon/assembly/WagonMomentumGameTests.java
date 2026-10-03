package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class WagonMomentumGameTests {
    private static WagonEntity wagon(GameTestHelper h) {
        for(int x=1;x<24;x++)for(int z=1;z<24;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var w=WagonContent.WAGON.get().create(h.getLevel());w.configure(WagonEntity.defaultParts(),Direction.NORTH);
        w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(w);return w;
    }
    private static Player atWall(GameTestHelper h,WagonEntity w,double height) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(w.pose().point(new Vec3(.55,height,-.5)));p.setOnGround(height==1.5);
        p.move(MoverType.SELF,w.pose().vector(new Vec3(.3,0,0)));p.setDeltaMovement(Vec3.ZERO);return p;
    }
    private static Vec3 localVelocity(WagonEntity w,Player p) { return w.pose().local(w.pose().point(Vec3.ZERO).add(p.getDeltaMovement())); }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void continuous_diagonal_wall_walking_keeps_tangent_momentum(GameTestHelper h) {
        var w=wagon(h);
        for(float yaw:new float[]{165,150,135,120,105}) {
            w.applyPose(new WagonPose(w.position(),yaw,0,0));var p=atWall(h,w,1.5);
            double expected=0;
            for(int i=0;i<20;i++) {
                expected+=.02;
                Vec3 velocity=p.getDeltaMovement().add(w.pose().vector(new Vec3(.01,0,.02))).add(0,-.08,0);
                p.setDeltaMovement(velocity);p.move(MoverType.SELF,velocity);
                Vec3 local=localVelocity(w,p);
                h.assertTrue(Math.abs(local.z-expected)<1e-6,"Wall erased tangent momentum at "+yaw+" tick="+i+" velocity="+local);
                h.assertTrue(Math.abs(local.x)<1e-6&&Math.abs(local.y)<1e-6,"Wall or floor retained inward velocity: "+local+" yaw="+yaw+" tick="+i+" pos="+w.pose().local(p.position()));
                h.assertTrue(!w.intersects(p.getBoundingBox().deflate(1e-5)),"Momentum fix penetrated wall");
                p.setDeltaMovement(p.getDeltaMovement().scale(.55));expected*=.55;
            }
            h.assertTrue(w.pose().local(p.position()).z>.3,"Continuous wall travel still slowed excessively");
        }
        h.succeed();
    }
    private static void airborne(GameTestHelper h,double vertical) {
        var w=wagon(h);
        for(float yaw:new float[]{165,135,105}) {
            w.applyPose(new WagonPose(w.position(),yaw,0,0));var p=atWall(h,w,vertical>0?1.72:2.05);p.setOnGround(false);
            Vec3 velocity=w.pose().vector(new Vec3(.04,vertical,.08));p.setDeltaMovement(velocity);p.move(MoverType.SELF,velocity);
            Vec3 actual=localVelocity(w,p);
            h.assertTrue(Math.abs(actual.y-vertical)<1e-6&&Math.abs(actual.z-.08)<1e-6,"Airborne wall contact reduced jump/fall or tangent velocity: "+actual);
            h.assertTrue(Math.abs(actual.x)<1e-6&&!p.onGround(),"Side wall became ground or retained inward velocity");
        }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void jumping_along_diagonal_wall_keeps_vertical_and_tangent_velocity(GameTestHelper h) { airborne(h,.42); }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void falling_along_diagonal_wall_keeps_vertical_and_tangent_velocity(GameTestHelper h) { airborne(h,-.25); }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void tilted_side_contact_preserves_surface_tangent_velocity_without_false_ground(GameTestHelper h) {
        var w=wagon(h);w.applyPose(new WagonPose(w.position(),135,.08F,.10F));
        for(double vertical:new double[]{.12,-.12}) {
            var p=atWall(h,w,vertical>0?1.8:2.03);p.setOnGround(false);
            Vec3 velocity=w.pose().vector(new Vec3(.04,vertical,.05));p.setDeltaMovement(velocity);p.move(MoverType.SELF,velocity);
            Vec3 actual=localVelocity(w,p);
            h.assertTrue(Math.abs(p.getDeltaMovement().y-velocity.y)<1e-6&&Math.abs(actual.z-.05)<1e-6,"Tilted wall changed surface-tangent momentum: "+actual);
            h.assertTrue(!p.onGround(),"Tilted side wall falsely grounded an airborne player");
        }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void world_ceiling_and_wagon_floor_still_stop_vertical_momentum(GameTestHelper h) {
        var w=wagon(h);w.applyPose(new WagonPose(w.position(),135,0,0));var p=atWall(h,w,1.5);
        Vec3 falling=w.pose().vector(new Vec3(.03,-.2,.07));p.setDeltaMovement(falling);p.move(MoverType.SELF,falling);
        h.assertTrue(p.getDeltaMovement().y==0&&p.onGround(),"Wagon floor stopped grounding or clearing falling velocity");
        for(int x=8;x<=15;x++)for(int z=13;z<=21;z++)h.setBlock(new BlockPos(x,6,z),Blocks.STONE);
        p=atWall(h,w,1.9);Vec3 jump=w.pose().vector(new Vec3(.03,.42,.07));p.setDeltaMovement(jump);p.move(MoverType.SELF,jump);
        h.assertTrue(p.getDeltaMovement().y==0&&!p.onGround(),"World ceiling did not stop jump velocity");
        h.assertTrue(Math.abs(localVelocity(w,p).z-.07)<1e-6,"Ceiling contact erased along-wall momentum");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void nearby_wagon_does_not_override_native_slime_bounce(GameTestHelper h) {
        var w=wagon(h);w.applyPose(new WagonPose(w.position(),135,0,0));
        BlockPos floor=h.absolutePos(new BlockPos(13,1,18));h.getLevel().setBlock(floor,Blocks.SLIME_BLOCK.defaultBlockState(),3);
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(floor.getX()+.5,floor.getY()+1.2,floor.getZ()+.5);
        p.setDeltaMovement(0,-.4,0);p.move(MoverType.SELF,p.getDeltaMovement());
        h.assertTrue(p.getDeltaMovement().y>.3,"Nearby wagon suppressed native slime bounce: "+p.getDeltaMovement()+" pos="+p.position()+" grounded="+p.onGround()+" careful="+p.isSteppingCarefully());h.succeed();
    }
}
