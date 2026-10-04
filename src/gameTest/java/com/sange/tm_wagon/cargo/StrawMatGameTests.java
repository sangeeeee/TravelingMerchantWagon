package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class StrawMatGameTests {
    @GameTest(template="assembly_test",batch="mat_clicks",timeoutTicks=35)
    public static void all_three_mat_segments_sleep_in_block_and_entity_forms(GameTestHelper h) {
        h.setNight();var f=frame(h);
        for(CargoHold hold:new CargoHold[]{f.cargo()})checkMatSegments(h,hold);
        f.dismantle(false,true);var w=wagon(h);checkMatSegments(h,w.cargo());h.succeed();
    }
    private static void checkMatSegments(GameTestHelper h,CargoHold hold) {
        h.assertTrue(hold.place(8,new ItemStack(WagonContent.STRAW_MAT.get()),player(h,hold))==null,"Mat setup failed");
        var p=sleeper(h,hold);p.getAbilities().instabuild=true;
        for(int slot:new int[]{4,6,8}) {
            Vec3 hit=hold.centreAt(slot).add(0,.125,0);Vec3 eye=hold.owner().cargoPose().point(hit.add(0,2,0));
            p.setPos(eye.add(0,-p.getEyeHeight(),0));p.setXRot(90);p.setYRot(0);
            hold.interact(p,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            h.assertTrue(p.isSleeping()&&hold.entry(8).sleeper.equals(p.getUUID()),"Cannot sleep via mat segment "+slot);
            p.stopSleepInBed(true,true);
        }
        p.discard();
    }
    private static WagonEntity wagon(GameTestHelper h) {
        var w=WagonContent.WAGON.get().create(h.getLevel());w.configure(WagonEntity.defaultParts(),Direction.NORTH);
        w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(w);return w;
    }
    private static Player player(GameTestHelper h,CargoHold hold) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(hold.owner().cargoPose().point(new Vec3(-3,0,0)));return p;
    }
    private static ServerPlayer sleeper(GameTestHelper h,CargoHold hold) {
        var p=h.makeMockServerPlayerInLevel();p.getAbilities().instabuild=false;p.getAbilities().flying=false;
        p.teleportTo(hold.owner().cargoPose().point(new Vec3(-2,1.5,.44)).x,hold.owner().cargoPose().position().y+1.5,hold.owner().cargoPose().position().z+.44);
        return p;
    }
    private static int carried(Player p) { return p.getInventory().items.stream().filter(s->s.is(WagonContent.STRAW_MAT.get())).mapToInt(ItemStack::getCount).sum(); }
    private static AssemblyFrameBlockEntity frame(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(11,2,17));h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState(),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(f.initializeFrame()==null,"Frame failed");
        h.assertTrue(f.install(WagonSlot.BODY,WagonPart.CARGO_BODY,null,new ItemStack(WagonContent.PART_ITEMS.get(WagonPart.CARGO_BODY).get()))==null,"Body failed");
        for(var pair:WagonEntity.defaultParts().entrySet())if(pair.getKey()!=WagonSlot.BODY)
            h.assertTrue(f.install(pair.getKey(),pair.getValue(),null,new ItemStack(WagonContent.PART_ITEMS.get(pair.getValue()).get()))==null,"Module failed");
        return f;
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void straw_mat_reserves_three_cells_and_removes_once_from_each_segment(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var p=player(h,hold);var stack=new ItemStack(WagonContent.STRAW_MAT.get(),4);
        h.assertTrue(CargoHold.allowed(stack)&&hold.place(8,stack,p)==null&&stack.getCount()==3,"Mat module was blocked by the own-block blacklist or not consumed once");
        var entry=hold.entry(8);
        h.assertTrue(entry.kind==CargoEntry.Kind.STRAW_MAT&&hold.entry(6)==entry&&hold.entry(4)==entry,"Missing reserved cells");
        h.assertTrue(hold.boxes().size()==1&&hold.boxes().getFirst().getYsize()<.13,"Mat collision is duplicated or too tall");
        for(int cell:new int[]{8,6,4})h.assertTrue(hold.place(cell,new ItemStack(Items.STONE),p)!=null,"Cargo overlapped reserved mat cell");
        h.assertTrue(hold.place(9,stack,p)==null,"Adjacent column mat could not be placed");
        h.assertTrue(!hold.boxes().get(0).intersects(hold.boxes().get(1)),"Adjacent mats overlap");
        for(int clicked:new int[]{6,8,4}) {
            p.setShiftKeyDown(true);hold.interact(p,net.minecraft.world.InteractionHand.MAIN_HAND,CargoHold.centre(clicked).add(0,.125,0));
        }
        h.assertTrue(hold.entry(8)==null&&hold.entry(6)==null&&hold.entry(4)==null&&carried(p)==1,"Repeated segment clicks duplicated a mat");
        h.assertTrue(hold.take(5,p)==null&&carried(p)==2&&hold.empty(),"Second mat removal failed");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void straw_mat_requires_contiguous_space_and_keeps_multiblock_ban(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=player(h,hold);var mat=new ItemStack(WagonContent.STRAW_MAT.get(),2);
        for(int slot:new int[]{0,1,2,3})h.assertTrue("message.tm_wagon.mat_space".equals(hold.place(slot,mat,p)),"Front edge did not reject missing cells");
        h.assertTrue(hold.place(4,new ItemStack(Items.STONE),p)==null,"Obstacle cargo failed");
        h.assertTrue("message.tm_wagon.mat_space".equals(hold.place(8,mat,p))&&mat.getCount()==2,"Mat skipped an occupied cell or consumed on failure");
        for(var item:new net.minecraft.world.item.Item[]{Items.RED_BED,Items.OAK_DOOR,Items.TALL_GRASS})
            h.assertTrue("message.tm_wagon.cargo_unsupported".equals(hold.place(9,new ItemStack(item),p)),"Mat exception admitted an ordinary multi-block item");
        h.assertTrue(hold.place(9,mat,p)==null&&hold.entry(7)==hold.entry(9),"Free opposite column rejected");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void straw_mat_save_visual_load_and_destroy_preserve_single_item(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var p=player(h,hold);var stack=new ItemStack(WagonContent.STRAW_MAT.get());stack.set(DataComponents.CUSTOM_NAME,Component.literal("Dry mat"));
        h.assertTrue(hold.place(8,stack,p)==null,"Placement failed");var id=hold.entry(8).id;
        for(boolean visual:new boolean[]{true,false}) {
            var saved=hold.save(h.getLevel().registryAccess(),visual);
            h.assertTrue(saved.getList("Entries",10).size()==1,"Mat saved per reserved cell");hold.load(saved,h.getLevel().registryAccess());
            h.assertTrue(hold.entry(4)==hold.entry(8)&&hold.entry(8).id.equals(id),"Load lost cell span or identity");
        }
        hold.destroy(true);hold.destroy(true);
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(w.position(),w.position()).inflate(5),e->e.getItem().is(WagonContent.STRAW_MAT.get()));
        h.assertTrue(drops.isEmpty(),"Entity destruction returned a wagon accessory instead of debris");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=90)
    public static void straw_mat_block_entity_round_trip_keeps_span_and_content(GameTestHelper h) {
        var f=frame(h);var p=player(h,f.cargo());h.assertTrue(f.cargo().place(8,new ItemStack(WagonContent.STRAW_MAT.get()),p)==null,"Block-form mat failed");
        h.assertTrue(f.cargo().place(5,new ItemStack(Items.CHEST),p)==null,"Neighbor cargo failed");f.cargo().entry(5).inventory.setItem(0,new ItemStack(Items.DIAMOND,7));
        var mat=f.cargo().entry(8);h.assertTrue(f.toggleFrame(null)==null,"Assembly failed");
        var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(6)).getFirst();
        h.assertTrue(f.cargo().empty()&&w.cargo().entry(4)==mat&&w.cargo().entry(8)==mat,"Assembly copied mat ownership");
        h.runAtTickTime(24,()->h.assertTrue(f.toggleFrame(null)==null,"Restoration failed to start"));
        h.runAtTickTime(49,()->{
            h.assertTrue(w.isRemoved()&&f.cargo().entry(4)==mat&&f.cargo().entry(6)==mat&&f.cargo().entry(8)==mat,"Round trip lost reservations");
            h.assertTrue(f.cargo().entry(5).inventory.getItem(0).getCount()==7,"Round trip lost neighbor inventory");
            h.assertTrue(f.cargo().take(6,p)==null&&carried(p)==1,"Block-form mat removal failed");h.succeed();
        });
    }
    @GameTest(template="assembly_test",batch="straw_mat_sleep",timeoutTicks=170)
    public static void straw_mat_native_sleep_skip_no_respawn_and_safe_wake(GameTestHelper h) {
        var f=frame(h);var hold=f.cargo();var loader=player(h,hold);
        h.assertTrue(hold.place(8,new ItemStack(WagonContent.STRAW_MAT.get()),loader)==null,"Mat placement failed");
        var p=sleeper(h,hold);var original=h.absolutePos(new BlockPos(3,2,3));p.setRespawnPosition(Level.OVERWORLD,original,47,false,false);
        var rule=h.getLevel().getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);int old=rule.get();
        var daylight=h.getLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT);boolean oldDaylight=daylight.get();long oldTime=h.getLevel().getDayTime();
        h.setNight();rule.set(0,h.getLevel().getServer());daylight.set(true,h.getLevel().getServer());
        h.runAfterDelay(2,()->{
            var mat=hold.entry(8);String error=StrawMatSleep.sleep(hold,mat,p);h.assertTrue(error==null,"Native sleep failed: "+error);
            h.assertTrue(p.isSleeping()&&p.getPose()==Pose.SLEEPING&&p.getSleepTimer()==0&&!p.isPassenger(),"Wrong native sleeping state");
            h.assertTrue(original.equals(p.getRespawnPosition())&&p.getRespawnAngle()==47,"Mat replaced respawn point");
            var other=sleeper(h,hold);h.assertTrue("block.minecraft.bed.occupied".equals(StrawMatSleep.sleep(hold,mat,other)),"Two players occupied one mat");
            p.stopSleepInBed(true,true);
            h.assertTrue(!p.isSleeping()&&p.getPose()==Pose.STANDING&&StrawMatSleep.sleepingPoint(p)==null&&h.getLevel().noCollision(p,p.getBoundingBox().deflate(.001)),"Wake left a stuck camera pose or collision");
            p.setYRot(81);p.setXRot(17);h.assertTrue(p.getYRot()==81&&p.getXRot()==17,"Wake locked camera rotation");
            h.assertTrue(StrawMatSleep.sleep(hold,mat,p)==null&&p.getSleepTimer()==0,"Second sleep did not reset counter");
        });
        // The embedded GameTest connection is not ticked by the network listener;
        // doTick is the same native player tick called by ServerGamePacketListenerImpl.
        for(int tick=3;tick<115;tick++)h.runAtTickTime(tick,p::doTick);
        h.runAfterDelay(115,()->{
            h.assertTrue(!p.isSleeping()&&h.getLevel().getDayTime()%24000<12000,"Native sleep did not skip night and wake: sleeping="+p.isSleeping()+", counter="+p.getSleepTimer()+", time="+h.getLevel().getDayTime()+", pose="+p.getPose()+", playerLevel="+p.level().dimension()+", ownerLive="+f.cargoLive());
            h.assertTrue(original.equals(p.getRespawnPosition())&&hold.entry(8).sleeper==null,"Night skip changed respawn or kept occupancy");
            h.assertTrue("block.minecraft.bed.no_sleep".equals(StrawMatSleep.sleep(hold,hold.entry(8),p)),"Daytime sleep accepted");
            rule.set(old,h.getLevel().getServer());daylight.set(oldDaylight,h.getLevel().getServer());h.getLevel().setDayTime(oldTime);h.succeed();
        });
    }
    @GameTest(template="assembly_test",batch="straw_mat_tilt_sleep",timeoutTicks=40)
    public static void straw_mat_tilted_wake_removal_and_movement_preserve_sleep_state(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();
        h.assertTrue(hold.place(8,new ItemStack(WagonContent.STRAW_MAT.get()),player(h,hold))==null,"Mat placement failed");
        // Stage the wagon to hold the chosen slope pose while exercising real Player sleep/wake ticks.
        h.getLevel().setBlock(w.blockPosition(),WagonContent.FRAME.get().defaultBlockState(),3);
        w.applyPose(new WagonPose(w.position(),213,.18F,.12F));var p=sleeper(h,hold);
        h.setNight();h.runAfterDelay(2,()->{
            w.applyPose(new WagonPose(w.position(),213,.18F,.12F));w.setDeltaMovement(Vec3.ZERO);
            String error=StrawMatSleep.sleep(hold,hold.entry(8),p);h.assertTrue(error==null,"Tilted sleep failed: "+error+", falling="+w.falling()+", player="+p.position()+", wagon="+w.position());p.stopSleepInBed(true,true);
            h.assertTrue(!p.isSleeping()&&p.getPose()==Pose.STANDING&&h.getLevel().noCollision(p,p.getBoundingBox().deflate(.001)),"Tilted wake trapped player");
            h.assertTrue(StrawMatSleep.sleep(hold,hold.entry(8),p)==null,"Repeat tilted sleep failed");
            var observer=player(h,hold);observer.setPos(w.pose().point(new Vec3(-4,0,0)));h.assertTrue(hold.take(4,observer)==null&&!p.isSleeping()&&carried(observer)==1,"Removing a mat did not wake the sleeper or dropped duplicates");
            Vec3 outside=w.pose().point(new Vec3(-3,1.5,0));p.teleportTo(outside.x,outside.y,outside.z);
            String replacement=hold.place(8,new ItemStack(WagonContent.STRAW_MAT.get()),observer);h.assertTrue(replacement==null,"Replacing mat failed: "+replacement+", observer="+observer.position()+", outside="+outside+", player="+p.position());
            h.assertTrue(StrawMatSleep.sleep(hold,hold.entry(8),p)==null,"Third sleep failed");
            Vec3 local=w.pose().local(StrawMatSleep.sleepingPoint(p));int timer=p.getSleepTimer();
            w.applyPose(new WagonPose(w.position().add(2,1,-3),267,1.2F,1.4F));w.setDeltaMovement(new Vec3(.4,-1,.2));
            var event=new net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent(p,Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
            StrawMatSleep.continueSleep(event);h.assertTrue(event.mayContinueSleeping(),"Movement or extreme tilt interrupted sleep");
            p.doTick();h.assertTrue(p.isSleeping()&&p.getPose()==Pose.SLEEPING&&p.getSleepTimer()>timer,"Movement reset native sleep state/counter");
            h.assertTrue(p.position().distanceToSqr(w.pose().point(local))<.000001,"Sleeping player did not follow wagon");
            h.assertTrue(hold.entry(8).sleeper.equals(p.getUUID()),"Movement released mat occupancy");
            p.stopSleepInBed(true,true);h.assertTrue(!p.isSleeping()&&StrawMatSleep.sleepingPoint(p)==null,"Manual wake did not release moving mat");h.succeed();
        });
    }
    @GameTest(template="assembly_test",batch="straw_mat_conversion_sleep",timeoutTicks=45)
    public static void sleeping_player_survives_block_to_entity_conversion(GameTestHelper h) {
        var f=frame(h);var hold=f.cargo();h.assertTrue(hold.place(8,new ItemStack(WagonContent.STRAW_MAT.get()),player(h,hold))==null,"Mat placement failed");
        h.setNight();var p=sleeper(h,hold);h.runAfterDelay(2,()->{
            String error=StrawMatSleep.sleep(hold,hold.entry(8),p);h.assertTrue(error==null,"Sleep setup failed: "+error);p.doTick();
            var mat=hold.entry(8);int timer=p.getSleepTimer();
            h.assertTrue(f.toggleFrame(null)==null,"Sleeping mat prevented entity conversion");
            var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(6)).getFirst();
            h.assertTrue(p.isSleeping()&&p.getPose()==Pose.SLEEPING&&p.getSleepTimer()==timer,"Conversion woke/restarted sleeping player");
            h.assertTrue(hold.empty()&&w.cargo().entry(8)==mat&&mat.sleeper.equals(p.getUUID()),"Conversion duplicated mat or lost sleeper");
            w.applyPose(new WagonPose(w.position().add(3,.5,0),231,.4F,-.3F));p.doTick();
            h.assertTrue(p.isSleeping()&&p.position().distanceToSqr(StrawMatSleep.sleepingPoint(p))<.000001,"Converted sleeper retained old frame anchor");
            w.cargo().destroy(false);h.assertTrue(!p.isSleeping()&&!StrawMatSleep.matSleeper(p),"Destroy did not wake transferred sleeper");h.succeed();
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void straw_mat_nether_and_end_refuse_without_explosion(GameTestHelper h) {
        for(var dimension:java.util.List.of(Level.NETHER,Level.END)) {
            var level=h.getLevel().getServer().getLevel(dimension);var w=WagonContent.WAGON.get().create(level);w.configure(WagonEntity.defaultParts(),Direction.NORTH);w.setPos(0,80,0);
            var p=new ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"mat-dimension-test"),net.minecraft.server.level.ClientInformation.createDefault());p.setPos(-2,81,0);
            var mat=CargoEntry.fromItem(w.cargo(),new ItemStack(WagonContent.STRAW_MAT.get()),Blocks.HAY_BLOCK.defaultBlockState());
            var saved=mat.save(level.registryAccess(),false);saved.putInt("Slot",8);var entries=new net.minecraft.nbt.ListTag();entries.add(saved);var data=new net.minecraft.nbt.CompoundTag();data.put("Entries",entries);w.cargo().load(data,level.registryAccess());
            h.assertTrue("message.tm_wagon.mat_dimension".equals(StrawMatSleep.sleep(w.cargo(),w.cargo().entry(8),p))&&!p.isSleeping()&&w.cargo().entry(4)!=null,"Forbidden dimension exploded, consumed, or slept");
        }h.succeed();
    }
}
