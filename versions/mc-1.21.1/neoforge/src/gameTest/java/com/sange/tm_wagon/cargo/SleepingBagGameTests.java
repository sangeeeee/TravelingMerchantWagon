package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.compat.BackpackCompat;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("tm_wagon_backpacks")
@PrefixGameTestTemplate(false)
public final class SleepingBagGameTests {
    private static ItemStack bag(DyeColor color) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("travelersbackpack",color.getName()+"_sleeping_bag")));
    }
    private static ServerPlayer player(GameTestHelper h,CargoHold hold,boolean atFront) {
        var p=CargoGameTests.serverPlayer(h,hold);
        p.setPos(hold.owner().cargoPose().point(new Vec3(-2,1.5,atFront?-3:3)));
        p.getAbilities().instabuild=false;
        return p;
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void all_colors_both_forms_and_directions_reserve_two_cells(GameTestHelper h) {
        var wagon=CargoGameTests.wagon(h);
        for(int form=0;form<2;form++) {
            var hold=form==0?wagon.cargo():CargoGameTests.frame(h).cargo();
            for(boolean front:new boolean[]{false,true}) {
                var p=player(h,hold,front);int clicked=front?0:8,anchor=front?2:8;
                for(var color:DyeColor.values()) {
                    var stack=bag(color);stack.setCount(2);
                    h.assertTrue(BackpackCompat.sleepingBag(stack)&&CargoHold.allowed(stack),"Native bag was not recognized: "+color);
                    h.assertTrue(hold.place(clicked,stack,p)==null&&stack.getCount()==1,"Placement consumed the wrong count");
                    var entry=hold.entry(anchor);
                    h.assertTrue(entry.kind==CargoEntry.Kind.SLEEPING_BAG&&entry.reversed==front&&hold.entry(anchor-hold.columns())==entry&&hold.occupiedSlots()==2,"Incorrect span/direction");
                    var box=hold.entryBox(anchor);
                    h.assertTrue(hold.boxes().size()==1&&Math.abs(box.getZsize()/box.getXsize()-2)<1e-6,"Bag proportions changed or collision duplicated");
                    h.assertTrue(Math.abs(box.getCenter().z-(hold.centreAt(anchor).z-.35))<1e-6,"Bag is not centred between the two slots");
                    h.assertTrue(hold.take(anchor-hold.columns(),p)==null&&hold.empty(),"Second cell did not unload the whole bag");
                    p.getInventory().clearContent();
                }
                p.discard();
            }
            if(form==0)wagon.discard();
        }h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void occupied_or_missing_second_cell_never_consumes_and_normal_beds_stay_banned(GameTestHelper h) {
        var hold=CargoGameTests.wagon(h).cargo();var p=player(h,hold,true);var stack=bag(DyeColor.BLUE);stack.setCount(3);
        h.assertTrue(hold.place(2,new ItemStack(Items.STONE),p)==null,"Obstacle setup failed");
        h.assertTrue("message.tm_wagon.sleeping_bag_space".equals(hold.place(0,stack,p))&&hold.place(8,stack,p)!=null&&stack.getCount()==3,"Forward failure consumed or skipped cargo");
        p.setPos(hold.owner().cargoPose().point(new Vec3(-2,1.5,3)));
        h.assertTrue(hold.place(0,stack,p)!=null&&stack.getCount()==3,"Front boundary consumed the bag");
        h.assertTrue(!CargoHold.allowed(new ItemStack(Items.RED_BED))&&!CargoHold.allowed(new ItemStack(Items.OAK_DOOR)),"Bag exemption admitted ordinary multiblocks");
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void save_transfer_unload_and_destroy_keep_one_item_with_data(GameTestHelper h) {
        var w=CargoGameTests.wagon(h);var hold=w.cargo();var p=player(h,hold,true);var stack=bag(DyeColor.GREEN);
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Kept bag"));
        h.assertTrue(hold.place(0,stack,p)==null,"Bag setup failed");var id=hold.entry(0).id;
        for(boolean visual:new boolean[]{true,false}) {
            var saved=hold.save(h.getLevel().registryAccess(),visual);
            h.assertTrue(saved.getList("Entries",10).size()==1,"Saved multiple items for one bag");
            hold.load(saved,h.getLevel().registryAccess());
            h.assertTrue(hold.entry(0)==hold.entry(2)&&hold.entry(0).id.equals(id)&&hold.entry(0).reversed,"Reload lost direction, identity or span");
        }
        w.setPos(w.position().add(8,0,0));var f=CargoGameTests.frame(h);hold.transferTo(f.cargo());w.discard();
        h.assertTrue(hold.empty()&&f.cargo().occupiedSlots()==2&&f.cargo().entry(2).id.equals(id),"Transfer duplicated ownership");
        p.setPos(f.cargoPose().point(new Vec3(-2,1.5,-3)));
        h.assertTrue(f.cargo().take(0,p)==null,"Unload failed");
        var returned=p.getInventory().items.stream().filter(BackpackCompat::sleepingBag).toList();
        h.assertTrue(returned.size()==1&&returned.getFirst().getCount()==1&&"Kept bag".equals(returned.getFirst().getHoverName().getString()),"Unload lost data or duplicated bag");
        var target=CargoGameTests.wagon(h);f.cargo().place(0,returned.getFirst(),p);f.cargo().transferTo(target.cargo());f.dismantle(false,true);
        target.cargo().destroy(true);target.cargo().destroy(true);
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,target.getBoundingBox().inflate(4),e->BackpackCompat.sleepingBag(e.getItem()));
        h.assertTrue(drops.size()==1&&"Kept bag".equals(drops.getFirst().getItem().getHoverName().getString()),"Destruction lost/duplicated sleeping bag");h.succeed();
    }
    @GameTest(template="assembly_test",batch="bag_sleep",timeoutTicks=35)
    public static void both_halves_and_directions_sleep_no_respawn_safe_wake_and_no_mobs(GameTestHelper h) {
        h.setNight();var hold=CargoGameTests.frame(h).cargo();
        for(boolean front:new boolean[]{false,true}) {
            var p=player(h,hold,front);int clicked=front?0:8,anchor=front?2:8;
            h.assertTrue(hold.place(clicked,bag(DyeColor.RED),p)==null,"Bag setup failed");
            var spawn=h.absolutePos(new BlockPos(2,2,2));p.setRespawnPosition(Level.OVERWORLD,spawn,47,false,false);
            var entry=hold.entry(anchor);var mob=EntityType.VILLAGER.create(h.getLevel());mob.setPos(p.position());
            h.assertTrue(!StrawMatSleep.sleepMob(hold,anchor,mob),"Non-player was permitted to use a sleeping bag");
            for(int cell:new int[]{anchor,anchor-hold.columns()}) {
                var hit=hold.centreAt(cell).add(0,.08,0);
                hold.interact(p,InteractionHand.MAIN_HAND,hit);
                h.assertTrue(p.isSleeping()&&p.getPose()==Pose.SLEEPING&&StrawMatSleep.reversed(p)==front,"Either half failed to sleep");
                h.assertTrue(p.getBedOrientation()==Direction.fromYRot(hold.owner().cargoPose().yaw()+(front?180:0)),"Sleeping orientation ignored direction");
                h.assertTrue(spawn.equals(p.getRespawnPosition())&&p.getRespawnAngle()==47,"Sleeping bag replaced spawn point");
                p.stopSleepInBed(true,true);
                h.assertTrue(!p.isSleeping()&&StrawMatSleep.sleepingPoint(p)==null&&h.getLevel().noCollision(p,p.getBoundingBox().deflate(.001)),"Wake left a stuck pose/collision");
                p.setPos(hold.owner().cargoPose().point(new Vec3(-2,1.5,front?-3:3)));
            }
            h.assertTrue(hold.take(clicked,p)==null,"Removal failed");p.discard();
        }h.succeed();
    }
    @GameTest(template="assembly_test",batch="bag_conversion_sleep",timeoutTicks=35)
    public static void sleeping_player_keeps_rearward_direction_through_conversion_and_motion(GameTestHelper h) {
        h.setNight();h.getLevel().updateSkyBrightness();var f=CargoGameTests.frame(h);var p=player(h,f.cargo(),true);
        h.assertTrue(f.cargo().place(0,bag(DyeColor.PURPLE),p)==null,"Bag setup failed");
        String error=StrawMatSleep.sleep(f.cargo(),f.cargo().entry(0),p);h.assertTrue(error==null,"Sleep failed: "+error);p.doTick();int timer=p.getSleepTimer();
        h.assertTrue(f.toggleFrame(null)==null,"Conversion failed");
        var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(6)).getFirst();
        h.assertTrue(p.isSleeping()&&p.getSleepTimer()==timer&&StrawMatSleep.reversed(p)&&f.cargo().empty(),"Conversion restarted sleep or lost direction");
        Vec3 local=w.pose().local(StrawMatSleep.sleepingPoint(p));
        w.applyPose(new WagonPose(w.position().add(2,1,-3),267,.4F,-.3F));w.setDeltaMovement(new Vec3(.4,-1,.2));p.doTick();
        h.assertTrue(p.isSleeping()&&p.position().distanceToSqr(w.pose().point(local))<1e-6&&p.getSleepTimer()>timer,"Moving/turning wagon interrupted sleeping bag");
        w.cargo().destroy(true);h.assertTrue(!p.isSleeping()&&!StrawMatSleep.matSleeper(p),"Destruction did not wake sleeper");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void forbidden_dimensions_refuse_without_consuming_bag(GameTestHelper h) {
        for(var dimension:java.util.List.of(Level.NETHER,Level.END)) {
            var level=h.getLevel().getServer().getLevel(dimension);var w=WagonContent.WAGON.get().create(level);w.configure(WagonEntity.defaultParts(),Direction.NORTH);w.setPos(0,80,0);
            var p=new ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"bag-dimension-test"),net.minecraft.server.level.ClientInformation.createDefault());p.setPos(-2,81,0);
            var bag=CargoEntry.fromItem(w.cargo(),bag(DyeColor.RED),((BlockItem)bag(DyeColor.RED).getItem()).getBlock().defaultBlockState());
            var saved=bag.save(level.registryAccess(),false);saved.putInt("Slot",8);var entries=new net.minecraft.nbt.ListTag();entries.add(saved);var data=new net.minecraft.nbt.CompoundTag();data.put("Entries",entries);w.cargo().load(data,level.registryAccess());
            h.assertTrue("message.tm_wagon.mat_dimension".equals(StrawMatSleep.sleep(w.cargo(),w.cargo().entry(8),p))&&!p.isSleeping()&&w.cargo().entry(6)!=null,"Dimension consumed, exploded, or slept");
        }h.succeed();
    }
}
