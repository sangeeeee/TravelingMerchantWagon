package com.sange.tm_wagon.entity;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class WoodenDriverSeatGameTests {
    private static final double TOP=31.48/16;
    private static AssemblyFrameBlockEntity frame(GameTestHelper h,WagonPart seat,boolean extended) {
        var pos=h.absolutePos(new BlockPos(11,2,17));h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState().setValue(AssemblyFrameBlock.FACING,Direction.NORTH),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(f.initializeFrame()==null,"Frame setup failed");
        var parts=WagonEntity.defaultParts();parts.put(WagonSlot.SEAT,seat);if(extended)parts.put(WagonSlot.BODY,WagonPart.LONG_CARGO_BODY);
        for(var e:parts.entrySet())h.assertTrue(f.install(e.getKey(),e.getValue(),null,new ItemStack(WagonContent.PART_ITEMS.get(e.getValue()).get()))==null,"Part installation failed: "+e);
        return f;
    }
    private static WagonEntity wagon(GameTestHelper h,AssemblyFrameBlockEntity f) {
        h.assertTrue(f.toggleFrame(null)==null,"Assembly failed");return h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(7)).getFirst();
    }
    private static ServerPlayer player(GameTestHelper h,Vec3 position) {
        var p=h.makeMockServerPlayerInLevel();p.setNoGravity(true);p.getAbilities().instabuild=false;p.setPos(position);return p;
    }
    private static void aim(ServerPlayer p,Vec3 eye,Vec3 target) {
        p.setPos(eye.subtract(0,p.getEyeHeight(),0));Vec3 d=target.subtract(eye).normalize();
        p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.asin(d.y)));
    }
    private static InteractionResult click(WagonEntity w,ServerPlayer p,Vec3 localEye,Vec3 target,boolean at) {
        aim(p,w.pose().point(localEye),w.pose().point(target));
        return at?w.interactAt(p,w.pose().point(target).subtract(w.position()),InteractionHand.MAIN_HAND):w.interact(p,InteractionHand.MAIN_HAND);
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void all_four_driver_seats_are_exclusive_required_modules(GameTestHelper h) {
        for(var seat:new WagonPart[]{WagonPart.SINGLE_SEAT,WagonPart.DOUBLE_SEAT,WagonPart.SINGLE_WOODEN_SEAT,WagonPart.DOUBLE_WOODEN_SEAT}) {
            var parts=WagonEntity.defaultParts();parts.remove(WagonSlot.SEAT);h.assertTrue(!AssemblyFrameBlockEntity.complete(parts),"Seat became optional");
            parts.put(WagonSlot.SEAT,seat);h.assertTrue(WagonSlot.SEAT.accepts(seat)&&AssemblyFrameBlockEntity.complete(parts),"New seat not accepted as required module");
        }
        var f=frame(h,WagonPart.SINGLE_WOODEN_SEAT,false);
        for(var other:new WagonPart[]{WagonPart.SINGLE_SEAT,WagonPart.DOUBLE_SEAT,WagonPart.DOUBLE_WOODEN_SEAT}) {
            var stack=new ItemStack(WagonContent.PART_ITEMS.get(other).get(),2);
            h.assertTrue("message.tm_wagon.occupied".equals(f.install(WagonSlot.SEAT,other,null,stack))&&stack.getCount()==2,"Second seat consumed item or installed");
        }
        h.assertTrue(WagonGeometry.partBoxes(WagonPart.SINGLE_WOODEN_SEAT).size()==1&&WagonGeometry.partBoxes(WagonPart.DOUBLE_WOODEN_SEAT).size()==1,"Plain seat collision unnecessarily detailed");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void single_wooden_seat_accepts_top_and_dismounts_onto_lower_wood_surface(GameTestHelper h) {
        var w=wagon(h,frame(h,WagonPart.SINGLE_WOODEN_SEAT,false));var p=player(h,w.position());
        h.assertTrue(click(w,p,new Vec3(0,2.7,-2.9),new Vec3(0,TOP,-1.875),true).consumesAction()&&w.driver()==p&&w.seatCapacity()==1,"Wood top did not board driver");
        w.positionRider(p);Vec3 ride=w.getPassengerRidingPosition(p);h.assertTrue(Math.abs(w.pose().local(ride).y-31.5/16)<.00001,"Wood seat retained wool cushion ride height");p.stopRiding();
        h.assertTrue(p.position().distanceTo(ride.add(0,.001,0))<.001&&h.getLevel().noCollision(p,p.getBoundingBox()),"Wooden seat dismount not safe directly above board");
        click(w,p,new Vec3(-1.5,1.92,-1.875),new Vec3(-8.5/16,1.92,-1.875),true);h.assertTrue(!p.isPassenger(),"Wooden side face allowed boarding");
        click(w,p,new Vec3(0,1.7,-3.1),new Vec3(0,1.92,-35.5/16),false);h.assertTrue(!p.isPassenger(),"Wooden front face allowed boarding");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void double_wooden_seat_has_independent_passenger_and_left_driver_positions(GameTestHelper h) {
        var w=wagon(h,frame(h,WagonPart.DOUBLE_WOODEN_SEAT,true));var right=player(h,w.position());var left=player(h,w.position());
        h.assertTrue(click(w,right,new Vec3(.45,2.7,-2.9),new Vec3(.45,TOP,-1.875),true).consumesAction()&&w.passengerSeat(right)==1&&w.driver()==null,"Right wooden seat assigned driver");
        h.assertTrue(click(w,left,new Vec3(.45,2.7,-2.9),new Vec3(.45,TOP,-1.875),false)==InteractionResult.FAIL&&!left.isPassenger(),"Occupied seat redirected passenger");
        h.assertTrue(click(w,left,new Vec3(-.45,2.7,-2.9),new Vec3(-.45,TOP,-1.875),false).consumesAction()&&w.driver()==left&&w.passengerSeat(left)==0&&w.seatCapacity()==2,"Left wooden driver position unavailable");
        var tag=new net.minecraft.nbt.CompoundTag();w.saveWithoutId(tag);var restored=WagonContent.WAGON.get().create(h.getLevel());restored.load(tag);
        h.assertTrue(restored.cargoSeat()==WagonPart.DOUBLE_WOODEN_SEAT&&restored.passengerSeat(left)==0&&restored.passengerSeat(right)==1,"Saved wooden seat identity or assignments changed");
        w.releasePassengers();h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void wooden_top_uses_local_orientation_for_tilted_rotated_wagons(GameTestHelper h) {
        var f=frame(h,WagonPart.DOUBLE_WOODEN_SEAT,false);var w=wagon(h,f);var p=player(h,w.position());
        for(var angles:new float[][]{{90,0,0},{213,.14F,-.09F},{137,-.10F,.07F}}) {
            w.applyPose(new WagonPose(w.position(),angles[0],angles[1],angles[2]));
            h.assertTrue(click(w,p,new Vec3(.45,2.7,-2.9),new Vec3(.45,TOP,-1.875),true).consumesAction()&&w.passengerSeat(p)==1,"Angled wooden top rejected right passenger");p.stopRiding();
            click(w,p,new Vec3(1.5,1.92,-1.875),new Vec3(15.5/16,1.92,-1.875),true);h.assertTrue(!p.isPassenger(),"Angled side boarded via supplied top point");
        }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=85)
    public static void single_wooden_seat_cabinet_round_trip(GameTestHelper h) { cabinet(h,false); }
    @GameTest(template="assembly_test",timeoutTicks=85)
    public static void double_wooden_seat_cabinet_round_trip(GameTestHelper h) { cabinet(h,true); }
    private static void cabinet(GameTestHelper h,boolean dual) {
        var type=dual?WagonPart.DOUBLE_WOODEN_SEAT:WagonPart.SINGLE_WOODEN_SEAT;var f=frame(h,type,dual);var p=player(h,f.cargoPose().point(new Vec3(-3,0,-1.8)));
        var side=new Vec3(-(dual?15.5:8.5)/16,1.7,-1.8);var c=f.cargo().cabinet();
        h.assertTrue(c.install(new ItemStack(WagonContent.CABINET.get()),p,side)==null&&c.rows()==(dual?6:3),"Wood cabinet size or installation incorrect");var inventory=c.inventory();int last=inventory.getContainerSize()-1;inventory.setItem(last,new ItemStack(Items.DIAMOND,26));
        var w=wagon(h,f);h.assertTrue(w.cargoSeat()==type&&w.cargo().cabinet().inventory()==inventory&&!c.installed(),"Entity lost wooden seat or cabinet identity");
        h.assertTrue(w.cargo().cabinet().open(p,side)==null,"Entity wooden-seat cabinet would not open");
        h.runAtTickTime(24,()->h.assertTrue(f.toggleFrame(null)==null,"Restore failed to begin"));
        h.runAtTickTime(49,()->{
            h.assertTrue(w.isRemoved()&&f.part(WagonSlot.SEAT)==type&&c.inventory()==inventory&&inventory.getItem(last).getCount()==26&&p.containerMenu==p.inventoryMenu,"Restoration changed wooden seat/inventory or kept old GUI");
            f.remove(java.util.Set.of(WagonSlot.SEAT),true);Vec3 origin=f.cargoPose().position();var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(origin,origin).inflate(7));
            h.assertTrue(drops.stream().filter(e->e.getItem().is(WagonContent.CABINET.get())).mapToInt(e->e.getItem().getCount()).sum()==1
                &&drops.stream().filter(e->e.getItem().is(WagonContent.PART_ITEMS.get(type).get())).mapToInt(e->e.getItem().getCount()).sum()==1
                &&drops.stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum()==26,"Wood seat removal lost/duplicated cabinet, component or contents");h.succeed();
        });
    }
}
