package com.sange.tm_wagon.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.cargo.*;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon_maid")
@PrefixGameTestTemplate(false)
public class WagonMaidGameTests {
    @GameTest(template="assembly_test",batch="maid_work",timeoutTicks=110)
    public static void maid_walks_to_double_seat_without_taking_driver(GameTestHelper h) { Scenarios.maid_walks_to_double_seat_without_taking_driver(h); }
    @GameTest(template="assembly_test",batch="maid_work",timeoutTicks=110)
    public static void triple_companions_fill_outer_seats_and_leave_middle_driver_empty(GameTestHelper h) { Scenarios.triple_companions_fill_outer_seats_and_leave_middle_driver_empty(h); }
    @GameTest(template="assembly_test",batch="maid_work",timeoutTicks=40)
    public static void single_and_full_passenger_seats_do_not_capture_maids(GameTestHelper h) { Scenarios.single_and_full_passenger_seats_do_not_capture_maids(h); }
    @GameTest(template="assembly_test",batch="maid_schedule",timeoutTicks=85)
    public static void entity_mat_sleep_uses_schedule_and_safe_wake(GameTestHelper h) { Scenarios.entity_mat_sleep_uses_schedule_and_safe_wake(h); }
    @GameTest(template="assembly_test",batch="maid_rest",timeoutTicks=55)
    public static void block_mat_sleeps_and_removal_releases_occupancy(GameTestHelper h) { Scenarios.block_mat_sleeps_and_removal_releases_occupancy(h); }
    @GameTest(template="assembly_test",batch="maid_rest",timeoutTicks=55)
    public static void movement_wakes_maid_and_mat_cannot_have_two_sleepers(GameTestHelper h) { Scenarios.movement_wakes_maid_and_mat_cannot_have_two_sleepers(h); }
    private static final class Scenarios {

    private static void time(GameTestHelper h,long time) {
        h.getLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,h.getLevel().getServer());h.getLevel().setDayTime(time);
    }
    private static WagonEntity wagon(GameTestHelper h,WagonPart seat) {
        for(int x=0;x<24;x++)for(int z=0;z<30;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var w=WagonContent.WAGON.get().create(h.getLevel());var parts=WagonEntity.defaultParts();parts.put(WagonSlot.SEAT,seat);
        if(seat.seatCapacity()==3)parts.put(WagonSlot.BODY,WagonPart.WIDE_CARGO_BODY);
        w.configure(parts,Direction.NORTH);w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(w);
        h.getLevel().setBlock(w.blockPosition(),WagonContent.FRAME.get().defaultBlockState(),3);return w;
    }
    private static EntityMaid maid(GameTestHelper h,Vec3 pos) {
        var maid=EntityMaid.TYPE.create(h.getLevel());maid.setPos(pos);maid.setTame(true,true);maid.setHomeModeEnable(false);
        h.assertTrue(TaskManager.findTask(WagonMaidExtension.TASK).isPresent(),"Companion task was not registered");
        maid.setTask(TaskManager.findTask(WagonMaidExtension.TASK).orElseThrow());maid.setSchedule(MaidSchedule.DAY);
        h.getLevel().addFreshEntity(maid);return maid;
    }
    private static void mat(GameTestHelper h,CargoHold hold) {
        var p=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);p.setPos(hold.owner().cargoPose().point(new Vec3(-2,1,0)));
        h.assertTrue(hold.place(8,new ItemStack(WagonContent.STRAW_MAT.get()),p)==null,"Mat placement failed");
    }
    private static AssemblyFrameBlockEntity frame(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(11,2,17));h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState(),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(f.initializeFrame()==null,"Frame failed");
        for(var pair:WagonEntity.defaultParts().entrySet())h.assertTrue(f.install(pair.getKey(),pair.getValue(),null,new ItemStack(WagonContent.PART_ITEMS.get(pair.getValue()).get()))==null,"Module failed");
        return f;
    }
    public static void maid_walks_to_double_seat_without_taking_driver(GameTestHelper h) {
        time(h,1000);var w=wagon(h,WagonPart.DOUBLE_SEAT);
        for(int x=0;x<24;x++)for(int z=0;z<30;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var m=maid(h,w.pose().point(new Vec3(-7,0,-1.875)));
        Vec3 initial=m.position();
        h.succeedWhen(()->{
            h.assertTrue(m.getVehicle()==w,"Maid did not approach and board: "+m.position()+", schedule="+m.getScheduleDetail()+", home="+m.isHomeModeEnable()+", pose="+m.getPose()+", walk="+m.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET));
            h.assertTrue(w.passengerSeat(m)==1&&w.driver()==null,"Maid occupied driver seat");
            h.assertTrue(m.position().distanceTo(initial)>3,"Maid did not approach the wagon");
        });
    }
    public static void triple_companions_fill_outer_seats_and_leave_middle_driver_empty(GameTestHelper h) {
        time(h,1000);var w=wagon(h,WagonPart.TRIPLE_SEAT);
        var a=maid(h,w.position().add(-2,0,0));var b=maid(h,w.position().add(2,0,0));
        h.succeedWhen(()->{
            h.assertTrue(a.getVehicle()==w&&b.getVehicle()==w,"Triple companions did not board: "+a.position()+", "+b.position()+", seats="+w.seatCapacity()+", free="+w.freeCompanionSeat()+", seat0="+w.companionSeatPosition(0)+", seat2="+w.companionSeatPosition(2)+", passengers="+w.getPassengers()+", walk="+a.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET));
            h.assertTrue(w.passengerSeat(a)!=1&&w.passengerSeat(b)!=1&&w.passengerSeat(a)!=w.passengerSeat(b)&&w.driver()==null,"Triple driver position was consumed");
        });
    }
    public static void single_and_full_passenger_seats_do_not_capture_maids(GameTestHelper h) {
        time(h,1000);var w=wagon(h,WagonPart.SINGLE_SEAT);var m=maid(h,w.position().add(-1,0,-1.5));
        h.assertTrue(w.freeCompanionSeat()==-1&&!w.boardCompanion(m,0),"Single driver seat accepted companion");
        h.runAfterDelay(22,()->{h.assertTrue(!m.isPassenger(),"Companion task boarded single driver seat");h.succeed();});
    }
    public static void entity_mat_sleep_uses_schedule_and_safe_wake(GameTestHelper h) {
        time(h,17000);var w=wagon(h,WagonPart.DOUBLE_SEAT);mat(h,w.cargo());
        w.applyPose(new WagonPose(w.position(),225,0,0));
        var m=maid(h,w.pose().point(new Vec3(-2,0,.5)));
        h.runAfterDelay(30,()->{
            h.assertTrue(m.isSleeping()&&StrawMatSleep.matSleeper(m)&&m.getUUID().equals(w.cargo().entry(8).sleeper),"Maid did not remain asleep on entity mat");
            time(h,1000);
        });
        h.runAfterDelay(55,()->{
            h.assertTrue(!m.isSleeping()&&!StrawMatSleep.matSleeper(m)&&w.cargo().entry(8).sleeper==null,"Work schedule did not release sleeping mat");
            h.assertTrue(m.getPose()==Pose.STANDING&&h.getLevel().noCollision(m,m.getBoundingBox().deflate(.0001)),"Wake position intersects wagon");h.succeed();
        });
    }
    public static void block_mat_sleeps_and_removal_releases_occupancy(GameTestHelper h) {
        time(h,17000);var f=frame(h);mat(h,f.cargo());
        var m=maid(h,f.cargoPose().point(new Vec3(-2,0,.5)));
        h.runAfterDelay(30,()->{
            h.assertTrue(m.isSleeping()&&StrawMatSleep.matSleeper(m),"Maid did not use block-form mat: "+m.position()+", schedule="+m.getScheduleDetail()+", sleep="+StrawMatSleep.matSleeper(m));
            var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(m.position().add(-1,0,0));
            h.assertTrue(f.cargo().take(8,p)==null,"Mat removal failed");
            h.assertTrue(!m.isSleeping()&&!StrawMatSleep.matSleeper(m)&&h.getLevel().noCollision(m,m.getBoundingBox().deflate(.0001)),"Removed mat left sleeping or trapped maid");h.succeed();
        });
    }
    public static void movement_wakes_maid_and_mat_cannot_have_two_sleepers(GameTestHelper h) {
        time(h,17000);var w=wagon(h,WagonPart.DOUBLE_SEAT);mat(h,w.cargo());
        var m=maid(h,w.pose().point(new Vec3(-2,0,.5)));var other=maid(h,m.position());other.setNoAi(true);
        h.runAfterDelay(30,()->{
            h.assertTrue(m.isSleeping(),"First maid did not sleep");
            h.assertTrue(!StrawMatSleep.sleepMob(w.cargo(),8,other),"Occupied mat accepted second maid");
            w.applyPose(new WagonPose(w.position().add(.3,0,0),225,0,0));
            StrawMatSleep.checkMobSleep(m,true);
            h.assertTrue(!m.isSleeping()&&w.cargo().entry(8).sleeper==null&&h.getLevel().noCollision(m,m.getBoundingBox().deflate(.0001)),"Moving wagon retained or trapped sleeper");h.succeed();
        });
    }
    }
}
