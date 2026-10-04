package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.cargo.*;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.WagonPhysics;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class ExtendedCargoGameTests {
    private static AssemblyFrameBlockEntity frame(GameTestHelper h,boolean full) {
        var pos=h.absolutePos(new BlockPos(11,2,17));h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState(),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(f.initializeFrame()==null,"Frame failed");
        install(h,f,WagonSlot.BODY,WagonPart.LONG_CARGO_BODY);
        if(full)for(var pair:WagonEntity.defaultParts().entrySet())if(pair.getKey()!=WagonSlot.BODY)install(h,f,pair.getKey(),pair.getValue());
        return f;
    }
    private static void install(GameTestHelper h,AssemblyFrameBlockEntity f,WagonSlot slot,WagonPart part) {
        h.assertTrue(f.install(slot,part,null,new ItemStack(WagonContent.PART_ITEMS.get(part).get()))==null,"Module failed: "+slot);
    }
    private static Player player(GameTestHelper h,CargoHold hold) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(hold.owner().cargoPose().point(new Vec3(-3,0,0)));return p;
    }
    private static WagonEntity wagon(GameTestHelper h) {
        var w=WagonContent.WAGON.get().create(h.getLevel());var parts=WagonEntity.defaultParts();parts.put(WagonSlot.BODY,WagonPart.LONG_CARGO_BODY);
        w.configure(parts,Direction.NORTH);w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(w);return w;
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void either_compartment_is_required_and_only_one_fits_the_body_slot(GameTestHelper h) {
        var f=frame(h,false);var stack=new ItemStack(WagonContent.PART_ITEMS.get(WagonPart.CARGO_BODY).get(),2);
        h.assertTrue(f.cargo().capacity()==12&&f.cargoBody()==WagonPart.LONG_CARGO_BODY,"Extended body did not select twelve slots");
        h.assertTrue("message.tm_wagon.occupied".equals(f.install(WagonSlot.BODY,WagonPart.CARGO_BODY,null,stack))&&stack.getCount()==2,"Second body replaced installed body or consumed an item");
        h.assertTrue(WagonSlot.BODY.position(f.getBlockPos(),f.facing(),f.cargoBody()).equals(f.getBlockPos().above()),"Frame docking anchor moved");
        var parts=WagonEntity.defaultParts();h.assertTrue(AssemblyFrameBlockEntity.complete(parts),"Standard body rejected");
        parts.put(WagonSlot.BODY,WagonPart.LONG_CARGO_BODY);h.assertTrue(AssemblyFrameBlockEntity.complete(parts),"Extended body rejected");
        parts.remove(WagonSlot.BODY);h.assertTrue(!AssemblyFrameBlockEntity.complete(parts),"Assembly without a body accepted");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void extended_rear_wheel_mounting_and_rotation_cache_match_the_model(GameTestHelper h) {
        var f=frame(h,false);var p=player(h,f.cargo());var stack=new ItemStack(WagonContent.PART_ITEMS.get(WagonPart.LARGE_WHEEL).get(),2);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var wrong=WagonSlot.REAR_LEFT.position(f.getBlockPos(),f.facing()).above();
        var hit=new BlockHitResult(Vec3.atCenterOf(wrong),Direction.DOWN,wrong,false);
        h.assertTrue(stack.getItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit))==InteractionResult.FAIL&&stack.getCount()==2,"Old rear mounting point was accepted");
        var right=WagonSlot.REAR_LEFT.position(f.getBlockPos(),f.facing(),f.cargoBody()).above();
        hit=new BlockHitResult(Vec3.atCenterOf(right),Direction.DOWN,right,false);
        h.assertTrue(stack.getItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit))==InteractionResult.CONSUME&&stack.getCount()==1,"Extended rear mounting point failed");
        var body=WagonGeometry.partBoxes(WagonPart.CARGO_BODY);var extended=WagonGeometry.partBoxes(WagonPart.LONG_CARGO_BODY);
        h.assertTrue(Math.abs(extended.getFirst().maxZ-body.getFirst().maxZ-.7)<.0001&&extended.getFirst().minZ==body.getFirst().minZ,"Extension moved the front or wrong length");
        for(var facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
            var normal=WagonGeometry.cells(WagonPart.LARGE_WHEEL,WagonSlot.REAR_LEFT,facing,WagonPart.CARGO_BODY);
            var longWheel=WagonGeometry.cells(WagonPart.LARGE_WHEEL,WagonSlot.REAR_LEFT,facing,WagonPart.LONG_CARGO_BODY);
            h.assertTrue(!normal.equals(longWheel),"Rear collision cache ignored body size: "+facing);
            h.assertTrue(longWheel.containsKey(WagonSlot.REAR_LEFT.position(BlockPos.ZERO,facing,WagonPart.LONG_CARGO_BODY)),"Rear anchor missing: "+facing);
            h.assertTrue(WagonGeometry.cells(WagonPart.SMALL_WHEEL,WagonSlot.FRONT_LEFT,facing,WagonPart.CARGO_BODY).equals(WagonGeometry.cells(WagonPart.SMALL_WHEEL,WagonSlot.FRONT_LEFT,facing,WagonPart.LONG_CARGO_BODY)),"Front axle moved");
        }h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void twelve_cargo_positions_place_save_and_remove_without_hidden_slots(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var p=player(h,hold);var stack=new ItemStack(Items.STONE,12);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        for(int i=0;i<12;i++)hold.interact(p,InteractionHand.MAIN_HAND,CargoHold.centre(i));
        h.assertTrue(stack.isEmpty()&&hold.boxes().size()==12&&hold.entry(10)!=null&&hold.entry(11)!=null,"Last row placement hit the wrong slot or consumed too few items");
        var saved=hold.save(h.getLevel().registryAccess(),false);hold.load(saved,h.getLevel().registryAccess());
        h.assertTrue(hold.capacity()==12&&hold.entry(11).item.is(Items.STONE)&&saved.getList("Entries",10).size()==12,"Saving lost the extra row");
        for(int i=0;i<12;i++)h.assertTrue(hold.take(i,p)==null,"Cargo removal failed at "+i);
        int count=p.getInventory().items.stream().filter(s->s.is(Items.STONE)).mapToInt(ItemStack::getCount).sum();
        h.assertTrue(count==12&&hold.empty(),"Removing all cargo duplicated or lost items");
        h.assertTrue(hold.place(12,new ItemStack(Items.STONE),p)!=null,"Out-of-range cargo accepted");
        w.configure(WagonEntity.defaultParts(),Direction.NORTH);h.assertTrue(hold.capacity()==10&&hold.place(10,new ItemStack(Items.STONE),p)!=null,"Standard body gained hidden slots");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void last_row_supports_straw_mat_stool_and_native_passenger_identity(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var p=player(h,hold);
        h.assertTrue(hold.place(10,new ItemStack(WagonContent.STRAW_MAT.get()),p)==null&&hold.entry(6)==hold.entry(10)&&hold.entry(8)==hold.entry(10),"Mat did not span the extended row");
        h.assertTrue(hold.place(11,new ItemStack(WagonContent.STOOL.get()),p)==null&&hold.seats.sit(11,p)==null&&w.passengerSeat(p)==WagonEntity.CARGO_SEAT_BASE+11&&w.driver()==null,"Last-row stool failed or took driving permission");
        hold.load(hold.save(h.getLevel().registryAccess(),false),h.getLevel().registryAccess());
        h.assertTrue(!p.isPassenger()&&hold.entry(11).kind==CargoEntry.Kind.STOOL&&hold.entry(6)==hold.entry(10),"Reload lost accessories or retained stale passenger");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=90)
    public static void extended_compartment_round_trip_keeps_last_row_container_and_seat(GameTestHelper h) {
        var f=frame(h,true);var hold=f.cargo();var p=player(h,hold);
        h.assertTrue(hold.place(10,new ItemStack(Items.CHEST),p)==null&&hold.place(11,new ItemStack(WagonContent.STOOL.get()),p)==null,"Last-row fixtures failed");
        hold.entry(10).inventory.setItem(0,new ItemStack(Items.DIAMOND,7));var chest=hold.entry(10);var stool=hold.entry(11);
        h.assertTrue(hold.seats.sit(11,p)==null&&f.toggleFrame(null)==null&&!p.isPassenger(),"Assembling occupied extended body failed");
        var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(7)).getFirst();
        h.assertTrue(f.cargo().empty()&&w.cargo().entry(10)==chest&&w.cargo().entry(11)==stool&&w.cargo().capacity()==12,"Transfer lost extra row when source modules cleared");
        h.runAtTickTime(23,()->{
            var pig=EntityType.PIG.create(h.getLevel());pig.setPos(w.position().add(-3,0,0));h.getLevel().addFreshEntity(pig);
            h.assertTrue(w.cargo().seats.sit(11,pig)==null&&f.toggleFrame(null)==null&&!pig.isPassenger(),"Restoring occupied extended body failed");
        });
        h.runAtTickTime(49,()->{
            h.assertTrue(w.isRemoved()&&f.cargoBody()==WagonPart.LONG_CARGO_BODY&&f.cargo().entry(10)==chest&&f.cargo().entry(11)==stool,"Restoration changed body or entry ownership");
            h.assertTrue(chest.inventory.getItem(0).getCount()==7&&f.cargo().capacity()==12,"Restoration lost container contents");
            h.assertTrue(f.cargo().take(10,p)==null&&f.cargo().take(11,p)==null,"Extra-row removal after restoration failed");h.succeed();
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=90)
    public static void extended_restoration_checks_added_rear_space_and_preserves_cargo_on_failure(GameTestHelper h) {
        var f=frame(h,true);var p=player(h,f.cargo());h.assertTrue(f.cargo().place(11,new ItemStack(Items.DIAMOND_BLOCK),p)==null&&f.toggleFrame(null)==null,"Assembly failed");
        var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(7)).getFirst();var entry=w.cargo().entry(11);
        var obstacle=f.getBlockPos().offset(0,1,3);
        h.runAtTickTime(23,()->{h.getLevel().setBlock(obstacle,Blocks.STONE.defaultBlockState(),3);h.assertTrue(f.toggleFrame(null)==null,"Restoration did not start");});
        h.runAtTickTime(70,()->{
            h.assertTrue(!w.isRemoved()&&w.cargo().entry(11)==entry&&f.cargo().empty()&&!f.extended(),"Failed restoration lost ownership or did not fold back");
            h.assertTrue(h.getLevel().getBlockState(obstacle).is(Blocks.STONE),"Restoration overwrote obstruction");h.succeed();
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void breaking_extended_body_returns_selected_body_and_extra_row_once(GameTestHelper h) {
        var f=frame(h,true);var p=player(h,f.cargo());h.assertTrue(f.cargo().place(11,new ItemStack(Items.DIAMOND_BLOCK),p)==null,"Cargo failed");
        f.remove(EnumSet.of(WagonSlot.BODY),true);f.remove(EnumSet.of(WagonSlot.BODY),true);
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(f.getBlockPos()).inflate(8));
        h.assertTrue(drops.stream().filter(e->e.getItem().is(WagonContent.PART_ITEMS.get(WagonPart.LONG_CARGO_BODY).get())).mapToInt(e->e.getItem().getCount()).sum()==1,"Extended body duplicated or returned standard body");
        h.assertTrue(drops.stream().filter(e->e.getItem().is(Items.DIAMOND_BLOCK)).mapToInt(e->e.getItem().getCount()).sum()==1&&f.parts().isEmpty()&&f.cargo().empty(),"Extra-row cargo lost or duplicated during dismantling");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void extended_wheelbase_movement_contacts_and_rolling_match_geometry(GameTestHelper h) {
        for(int x=1;x<24;x++)for(int z=1;z<24;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var w=wagon(h);var p=player(h,w.cargo());h.assertTrue(p.startRiding(w),"Driver failed");
        var horse=EntityType.HORSE.create(h.getLevel());horse.setNoAi(true);horse.setPos(w.horsePosition(0));h.getLevel().addFreshEntity(horse);horse.setLeashedTo(p,true);
        h.assertTrue(w.attachHorse(p,horse,0)==null,"Horse failed");
        Vec3 front=w.wheelCentre(0,w.pose()),rear=w.wheelCentre(2,w.pose());
        h.assertTrue(Math.abs(rear.z-front.z-3.2)<.0001&&Math.abs(w.wheelbase()-3.2)<.0001,"Physics used old rear axle or wheelbase");
        Vec3 start=w.position();for(int i=0;i<12;i++){w.acceptInput(p,1,0);w.tick();}
        double distance=start.z-w.getZ();
        h.assertTrue(Math.abs(distance-.6084)<.025&&w.supportMask()==15&&!w.falling(),"Extended wagon did not move on four supported wheels");
        h.assertTrue(Math.abs(w.renderWheel(2,1)+distance/WagonPhysics.radius(2))<.025,"Extended rear wheels slide");h.succeed();
    }
}
