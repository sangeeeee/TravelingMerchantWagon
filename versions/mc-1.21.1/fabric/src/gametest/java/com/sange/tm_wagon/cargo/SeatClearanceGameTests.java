package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.WagonPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class SeatClearanceGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
    public static WagonEntity wagon(GameTestHelper h) {
        var w=WagonContent.WAGON.get().create(h.getLevel());w.configure(WagonEntity.defaultParts(),Direction.NORTH);
        w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(w);return w;
    }
    private static void stool(GameTestHelper h,CargoHold hold) {
        var p=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);p.setPos(hold.owner().cargoPose().position());
        h.assertTrue(hold.place(4,new ItemStack(WagonContent.STOOL.get()),p)==null,"Stool setup failed");
    }
    public static LivingEntity rider(GameTestHelper h,WagonEntity w,boolean crew) {
        var p=EntityType.ENDERMAN.create(h.getLevel());p.setNoAi(true);p.setNoGravity(true);p.setPos(w.position());
        if(!crew)stool(h,w.cargo());h.getLevel().addFreshEntity(p);
        h.assertTrue(crew?p.startRiding(w):w.cargo().seats.sit(4,p)==null,"Tall rider could not board empty wagon");w.positionRider(p);return p;
    }
    public static void move(WagonEntity w,Vec3 delta,float yaw,float pitch) {
        try {
            var method=WagonPhysics.class.getDeclaredMethod("move",WagonEntity.class,Vec3.class,float.class,float.class,float.class);method.setAccessible(true);
            method.invoke(new WagonPhysics(),w,delta,yaw,pitch,w.roll());
        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=30)
    public void world_ceiling_rejects_stool_and_driver_boarding(GameTestHelper h) {
        var w=wagon(h);stool(h,w.cargo());var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(w.position());
        for(int seat:new int[]{WagonEntity.CARGO_SEAT_BASE+4,0}) {
            Vec3 anchor=seat==0?w.companionSeatPosition(0):w.pose().point(w.cargo().centreAt(4).add(0,.5,0));
            BlockPos ceiling=BlockPos.containing(SeatClearance.body(p,w,anchor).getCenter().add(0,.7,0));
            h.getLevel().setBlock(ceiling,Blocks.STONE.defaultBlockState(),3);
            h.assertTrue(seat==0?!p.startRiding(w):w.cargo().seats.sit(4,p)!=null,"Obstructed seat admitted player");
            h.getLevel().removeBlock(ceiling,false);
            h.assertTrue(seat==0?p.startRiding(w):w.cargo().seats.sit(4,p)==null,"Clear seat rejected player");p.stopRiding();p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(w.position());
        }
        h.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=30)
    public void block_form_stool_checks_external_ceiling(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(11,2,17));h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState(),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(pos);f.initializeFrame();
        h.assertTrue(f.install(WagonSlot.BODY,WagonPart.CARGO_BODY,null,new ItemStack(WagonContent.PART_ITEMS.get(WagonPart.CARGO_BODY).get()))==null,"Body failed");
        stool(h,f.cargo());var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(f.cargoPose().position());
        BlockPos ceiling=BlockPos.containing(f.cargoPose().point(f.cargo().centreAt(4).add(0,1.8,0)));
        h.getLevel().setBlock(ceiling,Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(f.cargo().seats.sit(4,p)!=null,"Block-form stool admitted obstructed player");h.getLevel().removeBlock(ceiling,false);
        h.assertTrue(f.cargo().seats.sit(4,p)==null,"Clear block-form stool rejected player");h.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=30)
    public void tall_stool_rider_stops_wagon_at_low_beam(GameTestHelper h) { beam(h,false); }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=30)
    public void tall_driver_stops_wagon_at_low_beam(GameTestHelper h) { beam(h,true); }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=30)
    public void passenger_head_limits_turning_and_uphill_tilt(GameTestHelper h) {
        var w=wagon(h);var p=rider(h,w,true);Vec3 start=w.position();
        var rotated=new com.sange.tm_wagon.physics.WagonPose(start,90,0,0);
        Vec3 local=w.pose().local(w.getPassengerRidingPosition(p));
        var target=SeatClearance.body(p,w,rotated.point(local));
        BlockPos obstacle=BlockPos.containing(target.getCenter().x,target.maxY-.1,target.getCenter().z);
        h.getLevel().setBlock(obstacle,Blocks.STONE.defaultBlockState(),3);
        move(w,Vec3.ZERO,90,0);w.positionRider(p);
        h.assertTrue(Math.abs(w.getYRot()-180)<.01,"Turn ignored passenger head collision");
        h.assertTrue(!p.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(obstacle)),"Turn embedded passenger");
        h.getLevel().removeBlock(obstacle,false);
        obstacle=BlockPos.containing(p.getX(),Math.floor(p.getBoundingBox().maxY),p.getZ());
        h.getLevel().setBlock(obstacle,Blocks.STONE_SLAB.defaultBlockState()
            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE,
                net.minecraft.world.level.block.state.properties.SlabType.TOP),3);
        move(w,Vec3.ZERO,w.getYRot(),.5F);w.positionRider(p);
        h.assertTrue(w.pitch()<.49F,"Uphill tilt ignored passenger head clearance");
        for(var b:h.getLevel().getBlockState(obstacle).getCollisionShape(h.getLevel(),obstacle).toAabbs())
            h.assertTrue(!p.getBoundingBox().deflate(.0001).intersects(b.move(obstacle)),"Tilt embedded passenger in ceiling");
        h.succeed();
    }
    private static void beam(GameTestHelper h,boolean crew) {
        var w=wagon(h);var p=rider(h,w,crew);var box=p.getBoundingBox();
        BlockPos obstacle=BlockPos.containing(box.getCenter().add(2,box.getYsize()/2-.1,0));
        h.getLevel().setBlock(obstacle,Blocks.STONE.defaultBlockState(),3);
        Vec3 start=w.position();move(w,new Vec3(3,0,0),w.getYRot(),0);w.positionRider(p);
        h.assertTrue(w.getX()-start.x<2.6,"Passenger head passed through low beam");
        h.assertTrue(!p.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(obstacle)),"Passenger ended embedded in beam");
        p.stopRiding();p.discard();w.setPos(start);move(w,new Vec3(3,0,0),w.getYRot(),0);
        h.assertTrue(w.getX()-start.x>2.99,"Unoccupied wagon should fit under beam");h.succeed();
    }
}
