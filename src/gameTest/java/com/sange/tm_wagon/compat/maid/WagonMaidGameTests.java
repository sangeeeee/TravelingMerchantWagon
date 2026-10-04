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
    public static void movement_keeps_maid_asleep_and_mat_cannot_have_two_sleepers(GameTestHelper h) { Scenarios.movement_keeps_maid_asleep_and_mat_cannot_have_two_sleepers(h); }
    @GameTest(template="assembly_test",batch="maid_rest",timeoutTicks=65)
    public static void entity_conversion_keeps_maid_asleep_without_new_reward(GameTestHelper h) { Scenarios.entity_conversion_keeps_maid_asleep_without_new_reward(h); }
    @GameTest(template="assembly_test",batch="maid_work",timeoutTicks=40)
    public static void changing_task_immediately_releases_companion_seat(GameTestHelper h) { Scenarios.changing_task_immediately_releases_companion_seat(h); }
    @GameTest(template="assembly_test",batch="maid_rest",timeoutTicks=70)
    public static void other_tasks_sleep_on_entity_mat_and_task_change_keeps_sleep(GameTestHelper h) { Scenarios.other_tasks_sleep_on_entity_mat_and_task_change_keeps_sleep(h); }
    @GameTest(template="assembly_test",batch="maid_rest",timeoutTicks=70)
    public static void nearest_native_bed_wins_over_farther_mat(GameTestHelper h) { Scenarios.nearest_native_bed_wins_over_farther_mat(h); }
    @GameTest(template="assembly_test",batch="maid_rest",timeoutTicks=70)
    public static void nearest_mat_wins_over_farther_native_beds(GameTestHelper h) { Scenarios.nearest_mat_wins_over_farther_native_beds(h); }
    @GameTest(template="assembly_test",batch="maid_rest",timeoutTicks=70)
    public static void destroying_wagon_wakes_without_extra_favorability_loss(GameTestHelper h) { Scenarios.destroying_wagon_wakes_without_extra_favorability_loss(h); }
    @GameTest(template="assembly_test",batch="maid_rest",timeoutTicks=70)
    public static void working_task_chooses_nearest_mat_across_both_wagon_forms(GameTestHelper h) { Scenarios.working_task_chooses_nearest_mat_across_both_wagon_forms(h); }
    @GameTest(template="assembly_test",batch="maid_rest",timeoutTicks=55)
    public static void sleeper_does_not_snap_to_block_centres_or_block_tilting(GameTestHelper h) { Scenarios.sleeper_does_not_snap_to_block_centres_or_block_tilting(h); }
    @GameTest(template="assembly_test",batch="maid_work",timeoutTicks=100)
    public static void riding_task_skips_occupied_and_obstructed_stools_and_leaves_on_task_change(GameTestHelper h) { Scenarios.riding_task_stools(h); }
    @GameTest(template="assembly_test",batch="maid_items",timeoutTicks=45)
    public static void maid_items_release_on_wagon_and_capture_seated_maid(GameTestHelper h) { Scenarios.itemRoundTrip(h); }
    @GameTest(template="assembly_test",batch="maid_items_sleep",timeoutTicks=45)
    public static void camera_respects_sleep_and_soul_capture_releases_mat(GameTestHelper h) { Scenarios.sleepCapture(h); }
    @GameTest(template="assembly_test",batch="maid_items",timeoutTicks=100)
    public static void lead_maid_stool_requires_riding_task(GameTestHelper h) { Scenarios.leadMaid(h); }
    @GameTest(template="assembly_test",batch="maid_items",timeoutTicks=45)
    public static void carryon_maid_stool_transfer_obeys_task_without_duplication(GameTestHelper h) {
        if(net.neoforged.fml.ModList.get().isLoaded("carryon"))CarryMaidScenarios.run(h);else h.succeed();
    }
    private static final class CarryMaidScenarios {
        static void run(GameTestHelper h) {
            var w=Scenarios.wagon(h,WagonPart.DOUBLE_SEAT);var hold=w.cargo();var p=h.makeMockServerPlayerInLevel();p.setPos(w.position().add(-2,0,0));
            h.assertTrue(hold.place(4,new ItemStack(WagonContent.STOOL.get()),p)==null,"Stool fixture failed");
            for(boolean riding:new boolean[]{false,true}) {
                var maid=EntityMaid.TYPE.create(h.getLevel());maid.setTame(true,true);maid.setOwnerUUID(p.getUUID());maid.setHomeModeEnable(false);
                maid.setTask(riding?TaskManager.findTask(WagonMaidExtension.RIDE_TASK).orElseThrow():TaskManager.getIdleTask());
                var data=tschipp.carryon.common.carry.CarryOnDataManager.getCarryData(p);data.setEntity(maid);data.setTick(-1);
                hold.interact(p,net.minecraft.world.InteractionHand.MAIN_HAND,hold.centreAt(4).add(0,.5,0));
                var live=h.getLevel().getEntity(maid.getUUID());
                h.assertTrue(live!=null&&!data.isCarrying()&&live.isPassenger()==riding,"Carried maid ownership/task transfer failed");
                live.stopRiding();live.discard();
            }
            h.succeed();
        }
    }
    private static final class Scenarios {

    static void itemRoundTrip(GameTestHelper h) {
        time(h,1000);var w=wagon(h,WagonPart.DOUBLE_SEAT);var hold=w.cargo();var p=h.makeMockServerPlayerInLevel();p.setPos(w.position().add(0,1,0));
        var maid=EntityMaid.TYPE.create(h.getLevel());maid.setTame(true,true);maid.setOwnerUUID(p.getUUID());maid.setHomeModeEnable(false);
        maid.setTask(TaskManager.findTask(WagonMaidExtension.RIDE_TASK).orElseThrow());
        var slab=com.github.tartaricacid.touhoulittlemaid.init.InitItems.SMART_SLAB_HAS_MAID.get().getDefaultInstance();
        com.github.tartaricacid.touhoulittlemaid.item.AbstractStoreMaidItem.storeMaidData(slab,maid);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,slab);
        Vec3 at=new Vec3(0,1.5,0);var block=BlockPos.containing(w.pose().point(at.add(0,1,0)));h.getLevel().setBlock(block,Blocks.STONE.defaultBlockState(),3);
        hold.interact(p,net.minecraft.world.InteractionHand.MAIN_HAND,at);
        h.assertTrue(p.getMainHandItem()==slab&&h.getLevel().getEntity(maid.getUUID())==null,"Blocked release lost source");h.getLevel().removeBlock(block,false);
        hold.interact(p,net.minecraft.world.InteractionHand.MAIN_HAND,at);var live=(EntityMaid)h.getLevel().getEntity(maid.getUUID());
        h.assertTrue(live!=null&&live.position().distanceTo(w.pose().point(at))<.1,"Stored maid did not release at clicked point");
        h.assertTrue(p.getMainHandItem().is(com.github.tartaricacid.touhoulittlemaid.init.InitItems.SMART_SLAB_EMPTY.get()),"Filled slab not replaced by empty slab");
        live.setPos(w.position().add(-2,0,0));p.setPos(w.position().add(2,0,0));p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        h.assertTrue(hold.place(4,new ItemStack(WagonContent.STOOL.get()),p)==null&&hold.seats.sit(4,live)==null,"Capture stool fixture failed");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,com.github.tartaricacid.touhoulittlemaid.init.InitItems.CAMERA.get().getDefaultInstance());
        h.assertTrue(WagonMaidItems.capture(p,live,net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction()&&live.isRemoved(),"Camera did not capture seated maid");
        var photo=p.getInventory().items.stream().filter(it->it.is(com.github.tartaricacid.touhoulittlemaid.init.InitItems.PHOTO.get())).findFirst().orElseThrow();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,photo);hold.interact(p,net.minecraft.world.InteractionHand.MAIN_HAND,new Vec3(0,1.5,1.4));
        h.assertTrue(photo.isEmpty()&&h.getLevel().getEntity(maid.getUUID()) instanceof EntityMaid,"Photo did not release or consume once");h.succeed();
    }
    static void sleepCapture(GameTestHelper h) {
        time(h,17000);var w=wagon(h,WagonPart.DOUBLE_SEAT);mat(h,w.cargo());var p=h.makeMockServerPlayerInLevel();
        p.setPos(w.position().add(-1,1,0));var m=maid(h,w.position().add(-2,0,0));m.setOwnerUUID(p.getUUID());
        h.assertTrue(StrawMatSleep.sleepMob(w.cargo(),8,m),"Sleep fixture failed");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,com.github.tartaricacid.touhoulittlemaid.init.InitItems.CAMERA.get().getDefaultInstance());
        WagonMaidItems.capture(p,m,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(m.isSleeping()&&!m.isRemoved()&&p.getMainHandItem().getDamageValue()==0,"Camera changed native sleeping rule");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,com.github.tartaricacid.touhoulittlemaid.init.InitItems.SMART_SLAB_EMPTY.get().getDefaultInstance());
        Vec3 point=w.pose().point(w.cargo().centreAt(6).add(0,.25,0));p.setPos(point.add(0,2-p.getEyeHeight(),0));p.setXRot(90);
        w.cargo().interact(p,net.minecraft.world.InteractionHand.MAIN_HAND,w.cargo().centreAt(6).add(0,.125,0));
        h.assertTrue(m.isRemoved()&&!StrawMatSleep.matSleeper(m)&&w.cargo().entry(8).sleeper==null,"Soul capture did not win over mat interaction or clear sleeping binding");
        h.assertTrue(p.getMainHandItem().is(com.github.tartaricacid.touhoulittlemaid.init.InitItems.SMART_SLAB_HAS_MAID.get()),"Soul capture did not store maid");h.succeed();
    }
    static void leadMaid(GameTestHelper h) {
        time(h,1000);var w=wagon(h,WagonPart.DOUBLE_SEAT);var p=h.makeMockServerPlayerInLevel();p.setPos(w.position().add(-2,0,0));
        h.assertTrue(w.cargo().place(4,new ItemStack(WagonContent.STOOL.get()),p)==null,"Stool fixture failed");
        var m=maid(h,p.position());m.setNoAi(true);m.setOwnerUUID(p.getUUID());m.setTask(TaskManager.getIdleTask());m.setLeashedTo(p,true);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(net.minecraft.world.item.Items.LEAD));
        w.cargo().interact(p,net.minecraft.world.InteractionHand.MAIN_HAND,w.cargo().centreAt(4).add(0,.5,0));
        h.assertTrue(!m.isPassenger()&&!m.isLeashed(),"Non-riding task stayed seated or failed to transfer");
        m.discard();var passenger=maid(h,p.position());passenger.setNoAi(true);passenger.setOwnerUUID(p.getUUID());
        passenger.setTask(TaskManager.findTask(WagonMaidExtension.RIDE_TASK).orElseThrow());passenger.setLeashedTo(p,true);
        w.cargo().interact(p,net.minecraft.world.InteractionHand.MAIN_HAND,w.cargo().centreAt(4).add(0,.5,0));
        h.assertTrue(passenger.getVehicle()==w&&!passenger.isLeashed(),"Riding-task maid did not stay seated");h.succeed();
    }
    static void riding_task_stools(GameTestHelper h) {
        time(h,1000);var w=wagon(h,WagonPart.DOUBLE_SEAT);
        var p=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);p.setPos(w.position().add(0,1,0));
        for(int slot:new int[]{2,4,6})h.assertTrue(w.cargo().place(slot,new ItemStack(WagonContent.STOOL.get()),p)==null,"Stool fixture failed");
        var occupant=net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());occupant.setNoAi(true);occupant.setPos(w.position());
        h.getLevel().addFreshEntity(occupant);h.assertTrue(w.cargo().seats.sit(2,occupant)==null,"Occupant failed");
        Vec3 blocked=w.pose().point(w.cargo().centreAt(4).add(0,1.6,0));
        h.getLevel().setBlock(BlockPos.containing(blocked),Blocks.STONE.defaultBlockState(),3);
        var m=maid(h,w.pose().point(new Vec3(-5,0,1)));
        m.setTask(TaskManager.findTask(WagonMaidExtension.RIDE_TASK).orElseThrow());
        h.succeedWhen(()->{
            h.assertTrue(m.getVehicle()==w,"Passenger task did not approach a stool");
            h.assertTrue(w.passengerSeat(m)==WagonEntity.CARGO_SEAT_BASE+6,"Maid chose occupied/blocked stool or driver seat");
            m.setTask(TaskManager.getIdleTask());h.assertTrue(!m.isPassenger(),"Passenger task change did not dismount immediately");
        });
    }
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
        for(var pair:WagonEntity.defaultParts().entrySet()) {
            String error=f.install(pair.getKey(),pair.getValue(),null,new ItemStack(WagonContent.PART_ITEMS.get(pair.getValue()).get()));
            h.assertTrue(error==null,"Module failed: "+pair.getKey()+", "+error);
        }
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
        m.setTask(TaskManager.getIdleTask());m.setFavorability(100);
        h.runAfterDelay(30,()->{
            h.assertTrue(m.isSleeping()&&StrawMatSleep.matSleeper(m),"Maid did not use block-form mat: "+m.position()+", schedule="+m.getScheduleDetail()+", sleep="+StrawMatSleep.matSleeper(m));
            h.assertTrue(m.getFavorability()==102,"Mat did not retain native sleep favorability reward");
            var p=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(m.position().add(-1,0,0));
            int favorability=m.getFavorability();
            h.assertTrue(f.cargo().take(8,p)==null,"Mat removal failed");
            h.assertTrue(!m.isSleeping()&&!StrawMatSleep.matSleeper(m)&&h.getLevel().noCollision(m,m.getBoundingBox().deflate(.0001)),"Removed mat left sleeping or trapped maid");
            h.assertTrue(m.getFavorability()==favorability,"Mat removal added a non-native favorability penalty");
            h.succeed();
        });
    }
    public static void changing_task_immediately_releases_companion_seat(GameTestHelper h) {
        time(h,1000);var w=wagon(h,WagonPart.DOUBLE_SEAT);var m=maid(h,w.position().add(-2,0,0));
        h.assertTrue(w.boardCompanion(m,1),"Fixture boarding failed");
        m.setTask(TaskManager.getIdleTask());
        h.assertTrue(!m.isPassenger(),"Task change left maid riding until next schedule update");
        h.assertTrue(h.getLevel().noCollision(m,m.getBoundingBox().deflate(.0001)),"Task change dismounted into wagon");
        h.runAfterDelay(22,()->{h.assertTrue(!m.isPassenger(),"Other task boarded the companion seat again");h.succeed();});
    }
    public static void other_tasks_sleep_on_entity_mat_and_task_change_keeps_sleep(GameTestHelper h) {
        time(h,17000);var w=wagon(h,WagonPart.DOUBLE_SEAT);mat(h,w.cargo());
        var m=maid(h,w.pose().point(new Vec3(-2,0,.5)));m.setTask(TaskManager.getIdleTask());
        h.runAfterDelay(30,()->{
            h.assertTrue(m.isSleeping()&&StrawMatSleep.matSleeper(m),"Idle task could not use entity mat");
            m.setTask(TaskManager.findTask(WagonMaidExtension.TASK).orElseThrow());
        });
        h.runAfterDelay(50,()->{
            h.assertTrue(m.isSleeping()&&StrawMatSleep.matSleeper(m),"Task change unnecessarily interrupted rest");
            h.succeed();
        });
    }
    private static BlockPos nativeBed(GameTestHelper h,BlockPos head) {
        var bed=com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.MAID_BED.get().defaultBlockState()
            .setValue(com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed.FACING,Direction.NORTH);
        h.setBlock(head,bed.setValue(com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        h.setBlock(head.south(),bed.setValue(com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed.PART,net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        return h.absolutePos(head);
    }
    public static void nearest_native_bed_wins_over_farther_mat(GameTestHelper h) {
        time(h,17000);var w=wagon(h,WagonPart.DOUBLE_SEAT);mat(h,w.cargo());
        BlockPos bed=nativeBed(h,new BlockPos(3,2,17));
        var m=maid(h,Vec3.atBottomCenterOf(bed.west()));m.setTask(TaskManager.getIdleTask());
        h.succeedWhen(()->{
            h.assertTrue(m.isSleeping()&&m.getSleepingPos().filter(bed::equals).isPresent(),"Nearest native bed was not selected: "+m.position()+", sleeping="+m.getSleepingPos());
            h.assertTrue(!StrawMatSleep.matSleeper(m)&&w.cargo().entry(8).sleeper==null,"Farther mat stole native bed target");
        });
    }
    public static void nearest_mat_wins_over_farther_native_beds(GameTestHelper h) {
        time(h,17000);var w=wagon(h,WagonPart.DOUBLE_SEAT);mat(h,w.cargo());
        nativeBed(h,new BlockPos(3,2,17));nativeBed(h,new BlockPos(21,2,17));
        var m=maid(h,w.pose().point(new Vec3(-2,0,.5)));m.setTask(TaskManager.getIdleTask());
        h.succeedWhen(()->{
            h.assertTrue(m.isSleeping()&&StrawMatSleep.matSleeper(m)&&m.getUUID().equals(w.cargo().entry(8).sleeper),"Farther native bed stole nearer mat target: "+m.position()+", sleeping="+m.getSleepingPos());
        });
    }
    public static void destroying_wagon_wakes_without_extra_favorability_loss(GameTestHelper h) {
        time(h,17000);var w=wagon(h,WagonPart.DOUBLE_SEAT);mat(h,w.cargo());
        var m=maid(h,w.pose().point(new Vec3(-2,0,.5)));m.setTask(TaskManager.getIdleTask());m.setFavorability(100);
        h.runAfterDelay(30,()->{
            h.assertTrue(m.isSleeping()&&StrawMatSleep.matSleeper(m),"Maid did not sleep before destruction");
            int favorability=m.getFavorability();w.cargo().destroy(true);w.discard();
            h.assertTrue(!m.isSleeping()&&!StrawMatSleep.matSleeper(m),"Destroyed wagon retained sleeping maid");
            h.assertTrue(m.getFavorability()==favorability,"Wagon destruction added a non-native favorability penalty");
            h.assertTrue(h.getLevel().noCollision(m,m.getBoundingBox().deflate(.0001)),"Destroyed wagon left maid trapped");h.succeed();
        });
    }
    public static void working_task_chooses_nearest_mat_across_both_wagon_forms(GameTestHelper h) {
        time(h,17000);var w=wagon(h,WagonPart.DOUBLE_SEAT);
        // Install the block-form wagon before adding the second entity fixture:
        // assembly deliberately rejects an intersecting entity's broad selection box.
        w.discard();var f=frame(h);mat(h,f.cargo());
        w=WagonContent.WAGON.get().create(h.getLevel());var parts=WagonEntity.defaultParts();parts.put(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);
        w.configure(parts,Direction.NORTH);w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,8))));h.getLevel().addFreshEntity(w);mat(h,w.cargo());
        var farther=w;nativeBed(h,new BlockPos(3,2,17));
        var m=maid(h,f.cargoPose().point(new Vec3(-2,0,.5)));m.setTask(TaskManager.getTaskIndex().get(1));
        h.succeedWhen(()->{
            h.assertTrue(m.isSleeping()&&StrawMatSleep.matSleeper(m)&&m.getUUID().equals(f.cargo().entry(8).sleeper),"Working task did not choose nearest block-form mat");
            h.assertTrue(farther.cargo().entry(8).sleeper==null,"Farther entity mat was occupied");
        });
    }
    public static void movement_keeps_maid_asleep_and_mat_cannot_have_two_sleepers(GameTestHelper h) {
        time(h,17000);var w=wagon(h,WagonPart.DOUBLE_SEAT);mat(h,w.cargo());
        var m=maid(h,w.pose().point(new Vec3(-2,0,.5)));var other=maid(h,m.position());other.setNoAi(true);
        h.runAfterDelay(30,()->{
            h.assertTrue(m.isSleeping(),"First maid did not sleep");
            h.assertTrue(!StrawMatSleep.sleepMob(w.cargo(),8,other),"Occupied mat accepted second maid");
            Vec3 local=w.pose().local(StrawMatSleep.sleepingPoint(m));int favorability=m.getFavorability();m.setNoAi(true);
            w.applyPose(new WagonPose(w.position().add(2,1,-3),225,1.2F,1.4F));w.setDeltaMovement(new Vec3(.4,-1,.2));
            StrawMatSleep.checkMobSleep(m,true);
            m.tick();h.assertTrue(m.isSleeping()&&StrawMatSleep.matSleeper(m)&&m.position().distanceToSqr(w.pose().point(local))<.000001,"Moving/tilted wagon woke or lost maid");
            h.assertTrue(m.getFavorability()==favorability&&m.getUUID().equals(w.cargo().entry(8).sleeper),"Movement restarted sleep reward or released mat");
            m.stopSleeping();h.assertTrue(!m.isSleeping()&&!StrawMatSleep.matSleeper(m),"Manual wake failed after movement");h.succeed();
        });
    }
    public static void entity_conversion_keeps_maid_asleep_without_new_reward(GameTestHelper h) {
        time(h,17000);var f=frame(h);mat(h,f.cargo());var m=maid(h,f.cargoPose().point(new Vec3(-2,0,.5)));
        h.runAfterDelay(30,()->{
            h.assertTrue(m.isSleeping()&&StrawMatSleep.matSleeper(m),"Maid did not sleep on block-form mat");
            int favorability=m.getFavorability();var mat=f.cargo().entry(8);
            h.assertTrue(f.toggleFrame(null)==null,"Conversion failed with sleeping maid");
            var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new net.minecraft.world.phys.AABB(f.getBlockPos()).inflate(6)).getFirst();
            h.assertTrue(m.isSleeping()&&m.getPose()==Pose.SLEEPING&&m.getFavorability()==favorability,"Conversion woke/restarted sleeping maid");
            h.assertTrue(f.cargo().empty()&&w.cargo().entry(8)==mat&&m.getUUID().equals(mat.sleeper),"Conversion duplicated mat or cleared occupancy");
            w.applyPose(new WagonPose(w.position().add(2,1,0),237,.4F,-.3F));m.setNoAi(true);m.tick();
            h.assertTrue(m.isSleeping()&&m.position().distanceToSqr(StrawMatSleep.sleepingPoint(m))<.000001,"Converted maid remained at old frame");h.succeed();
        });
    }
    public static void sleeper_does_not_snap_to_block_centres_or_block_tilting(GameTestHelper h) {
        time(h,17000);var w=wagon(h,WagonPart.DOUBLE_SEAT);mat(h,w.cargo());
        var m=maid(h,w.pose().point(new Vec3(-2,0,.5)));
        h.runAfterDelay(30,()->{
            h.assertTrue(m.isSleeping(),"Maid did not sleep");m.setNoAi(true);
            var start=new WagonPose(w.position().add(.37,4,.23),213,0,0);w.applyPose(start);
            StrawMatSleep.follow(m);Vec3 expected=StrawMatSleep.sleepingPoint(m);
            m.baseTick();h.assertTrue(m.position().distanceToSqr(expected)<1e-10,"Native maid base tick snapped mat sleeper to block centre");
            h.assertTrue(!w.canCollideWith(m)&&!com.sange.tm_wagon.physics.WagonCollision.eligible(m,w),"Sleeper collided with its own wagon");
            h.assertTrue(h.getLevel().getEntityCollisions(w,m.getBoundingBox()).isEmpty(),"Sleeping maid entered wagon's movement obstacles");
            // Exercise the real swept translation/rotation solver, with the sleeper
            // still at the previous pose just as it is during a vehicle tick.
            try {
                var move=com.sange.tm_wagon.physics.WagonPhysics.class.getDeclaredMethod("move",WagonEntity.class,Vec3.class,float.class,float.class,float.class);
                move.setAccessible(true);var physics=new com.sange.tm_wagon.physics.WagonPhysics();
                Vec3 delta=new Vec3(.2,.7,-.3);
                move.invoke(physics,w,delta,235F,.4F,-.3F);
                h.assertTrue(w.position().distanceToSqr(start.position().add(delta))<1e-8&&Math.abs(w.pitch()-.4F)<1e-5&&Math.abs(w.roll()+.3F)<1e-5,"Sleeping maid blocked wagon climb/tilt");
            } catch(ReflectiveOperationException e) { throw new RuntimeException(e); }
            StrawMatSleep.followAfterLevel(new net.neoforged.neoforge.event.tick.LevelTickEvent.Post(()->true,h.getLevel()));
            h.assertTrue(m.position().distanceToSqr(StrawMatSleep.sleepingPoint(m))<1e-10,"Tick order left sleeper behind wagon");
            m.stopSleeping();
            h.assertTrue(w.canCollideWith(m)&&com.sange.tm_wagon.physics.WagonCollision.eligible(m,w),"Wake retained own-wagon collision exemption");
            h.succeed();
        });
    }
    }
}
