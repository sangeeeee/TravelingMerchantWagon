package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.CargoSeatEntity;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class CargoStoolGameTests {
    private static WagonEntity wagon(GameTestHelper h) {
        var w=WagonContent.WAGON.get().create(h.getLevel());var parts=WagonEntity.defaultParts();parts.put(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);
        w.configure(parts,Direction.NORTH);w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(w);return w;
    }
    private static Player player(GameTestHelper h,CargoHold hold) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(hold.owner().cargoPose().point(new Vec3(-3,1,0)));return p;
    }
    private static AssemblyFrameBlockEntity frame(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(11,2,17));h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState(),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(f.initializeFrame()==null,"Frame failed");
        for(var slot:new WagonSlot[]{WagonSlot.BODY,WagonSlot.SEAT,WagonSlot.SHAFTS,WagonSlot.FRONT_LEFT,WagonSlot.FRONT_RIGHT,WagonSlot.REAR_LEFT,WagonSlot.REAR_RIGHT}) {
            var part=WagonEntity.defaultParts().get(slot);h.assertTrue(f.install(slot,part,null,new ItemStack(WagonContent.PART_ITEMS.get(part).get()))==null,"Module failed");
        }return f;
    }
    private static void place(GameTestHelper h,CargoHold hold,int slot) {
        h.assertTrue(hold.place(slot,new ItemStack(WagonContent.STOOL.get()),player(h,hold))==null,"Stool placement failed");
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void stool_dismount_with_tall_front_cargo_stays_on_seat(GameTestHelper h) {
        var w=wagon(h);place(h,w.cargo(),6);
        h.assertTrue(w.cargo().place(4,new ItemStack(Items.CHEST),player(h,w.cargo()))==null,"Chest placement failed");
        w.applyPose(new WagonPose(w.position(),225,0,0));
        assertStandOnStool(h,w.cargo(),6,player(h,w.cargo()));
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void block_stool_dismount_with_tall_front_cargo_stays_on_seat(GameTestHelper h) {
        var f=frame(h);place(h,f.cargo(),6);
        h.assertTrue(f.cargo().place(4,new ItemStack(Items.STONE),player(h,f.cargo()))==null,"Stone placement failed");
        assertStandOnStool(h,f.cargo(),6,player(h,f.cargo()));
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void rotated_canopied_stools_avoid_adjacent_tall_cargo_in_all_body_sizes(GameTestHelper h) {
        var w=wagon(h);
        for(var body:new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY}) {
            var modules=new java.util.EnumMap<WagonSlot,WagonPart>(WagonSlot.class);modules.putAll(w.parts());
            modules.put(WagonSlot.BODY,body);w.configure(modules,Direction.NORTH);
            // Both halves need to escape away from the centre when that side is blocked.
            int front=body.columns(),rear=(body.rows()-2)*body.columns()+body.columns()-1;
            place(h,w.cargo(),front);place(h,w.cargo(),rear);
            var p=player(h,w.cargo());p.setPos(w.position().add(0,1,0));
            h.assertTrue(w.cargo().place(front+body.columns(),new ItemStack(Items.STONE),p)==null,"Rear neighbour failed");
            h.assertTrue(w.cargo().place(rear-body.columns(),new ItemStack(Items.CHEST),p)==null,"Front neighbour failed");
            var tag=w.cargo().save(h.getLevel().registryAccess(),false);var canopy=new net.minecraft.nbt.CompoundTag();
            canopy.putBoolean("Installed",true);tag.put("Canopy",canopy);w.cargo().load(tag,h.getLevel().registryAccess());w.cargoGeometryChanged();
            for(float yaw:new float[]{180,195,210,225,240,270,315}) {
                w.applyPose(new WagonPose(w.position(),yaw,0,0));
                for(int slot:new int[]{front,rear})assertStandOnStool(h,w.cargo(),slot,player(h,w.cargo()));
            }
            w.cargo().load(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());w.cargoGeometryChanged();
        }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void block_canopy_stool_dismount_stays_on_seat_for_players_and_mobs(GameTestHelper h) {
        var f=frame(h);place(h,f.cargo(),4);var p=player(h,f.cargo());
        h.assertTrue(f.cargo().canopy().install(new ItemStack(WagonContent.CANOPY.get()),p,new Vec3(-1.15625,1.9,.5))==null,"Roof install failed");
        assertStandOnStool(h,f.cargo(),4,p);
        var mob=EntityType.ZOMBIE.create(h.getLevel());mob.setNoAi(true);mob.setPos(p.position());
        assertStandOnStool(h,f.cargo(),4,mob);
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void extended_canopy_stool_dismount_stays_near_seat_or_uses_safe_outside_on_slopes(GameTestHelper h) {
        var w=wagon(h);var parts=w.parts();var modules=new java.util.EnumMap<WagonSlot,WagonPart>(WagonSlot.class);modules.putAll(parts);
        modules.put(WagonSlot.BODY,WagonPart.LONG_CARGO_BODY);w.configure(modules,Direction.NORTH);
        for(int slot:new int[]{4,5,10,11})place(h,w.cargo(),slot);
        var tag=w.cargo().save(h.getLevel().registryAccess(),false);var canopy=new net.minecraft.nbt.CompoundTag();canopy.putBoolean("Installed",true);tag.put("Canopy",canopy);
        w.cargo().load(tag,h.getLevel().registryAccess());w.cargoGeometryChanged();
        h.assertTrue(w.collisionBoxes().stream().mapToDouble(b->b.maxY-w.getY()).max().orElseThrow()<5,"Raised roof exceeds the flat five-block height envelope");
        for(var angles:new float[][]{{29.676F,0,0},{213,0,0},{137,0,0},{213,.14F,.09F},{137,-.10F,-.07F}}) {
            w.applyPose(new WagonPose(w.position(),angles[0],angles[1],angles[2]));
            for(int slot:new int[]{4,5,10,11})assertStandOnStool(h,w.cargo(),slot,player(h,w.cargo()),angles[1]!=0);
        }
        h.succeed();
    }
    private static void assertStandOnStool(GameTestHelper h,CargoHold hold,int slot,net.minecraft.world.entity.LivingEntity rider) {
        assertStandOnStool(h,hold,slot,rider,false);
    }
    private static void assertStandOnStool(GameTestHelper h,CargoHold hold,int slot,net.minecraft.world.entity.LivingEntity rider,boolean allowOutside) {
        if(hold.seats.available(slot,rider))h.assertTrue(hold.seats.sit(slot,rider)==null,"Canopied stool boarding failed");
        else {
            h.assertTrue(hold.seats.sit(slot,rider)!=null,"Overlapping cargo must prevent new boarding");
            // Preserve the dismount regression for an already seated rider whose
            // surroundings changed (rotation or newly placed adjacent cargo).
            h.assertTrue(hold.owner() instanceof WagonEntity,"Unexpected blocked block-form stool");
            var wagon=(WagonEntity)hold.owner();
            try {
                var requested=WagonEntity.class.getDeclaredField("requestedSeat");requested.setAccessible(true);
                requested.setInt(wagon,WagonEntity.CARGO_SEAT_BASE+slot);
                try { h.assertTrue(rider.startRiding(wagon,true),"Legacy rider setup failed");wagon.positionRider(rider); }
                finally { requested.setInt(wagon,-1); }
            } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        rider.stopRiding();Vec3 target=rider.position();
        h.assertTrue(rider.getPose()==Pose.STANDING,"Dismount left the rider pose active");
        var pose=hold.owner().cargoPose();Vec3 centre=pose.point(hold.centreAt(slot).add(0,.5,0));
        double top=CargoHold.worldBox(hold.stoolBounds(slot),pose).maxY;
        boolean aboveStool=Math.hypot(target.x-centre.x,target.z-centre.z)<=.355&&target.y>=top&&target.y-top<(allowOutside?.126:.01);
        h.assertTrue(aboveStool||allowOutside&&Math.abs(pose.local(target).x)>1.5,"Dismount jumped off stool or onto roof: slot="+slot+", yaw="+pose.yaw()+", pitch="+pose.pitch()+", roll="+pose.roll()+", local="+pose.local(target));
        h.assertTrue(h.getLevel().noCollision(rider,rider.getBoundingBox().deflate(.0001)),"Standing rider still intersects the canopy");
        rider.setPos(pose.point(new Vec3(-3,1,0)));
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void stool_is_wagon_only_one_slot_and_half_block_high(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var p=player(h,hold);var stack=new ItemStack(WagonContent.STOOL.get(),2);
        h.assertTrue(!(stack.getItem() instanceof BlockItem)&&CargoHold.allowed(stack),"Stool could place a world block or was filtered as ordinary cargo");
        h.assertTrue(hold.place(4,stack,p)==null&&stack.getCount()==1,"Stool did not consume exactly one item");
        var box=hold.entryBox(4);
        h.assertTrue(Math.abs(box.getYsize()-.5)<.0001&&Math.abs(box.getXsize()-CargoHold.SCALE)<.0001&&Math.abs(box.getZsize()-CargoHold.SCALE)<.0001,"Wrong stool size");
        h.assertTrue(hold.entry(2)==null&&hold.entry(6)==null&&hold.place(6,new ItemStack(Items.STONE),p)==null,"Stool reserved extra cells");
        var id=hold.entry(4).id;hold.load(hold.save(h.getLevel().registryAccess(),false),h.getLevel().registryAccess());
        h.assertTrue(hold.entry(4).kind==CargoEntry.Kind.STOOL&&hold.entry(4).id.equals(id),"Stool save/load lost identity");
        h.assertTrue(h.getLevel().getEntitiesOfClass(CargoSeatEntity.class,new AABB(w.position(),w.position()).inflate(5)).isEmpty(),"Empty moving stools created seat entities");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void cargo_stools_do_not_take_driver_seats_and_follow_tilt(GameTestHelper h) {
        var w=wagon(h);place(h,w.cargo(),4);place(h,w.cargo(),5);
        var a=player(h,w.cargo());var b=player(h,w.cargo());var driver=player(h,w.cargo());var right=player(h,w.cargo());
        h.assertTrue(w.cargo().seats.sit(4,a)==null&&w.cargo().seats.sit(5,b)==null,"Two cargo seats could not be occupied");
        h.assertTrue(w.driver()==null&&w.passengerSeat(a)==WagonEntity.CARGO_SEAT_BASE+4&&w.passengerSeat(b)==WagonEntity.CARGO_SEAT_BASE+5,"Cargo rider acquired driver identity");
        h.assertTrue(driver.startRiding(w)&&right.startRiding(w)&&w.driver()==driver&&w.passengerSeat(right)==1,"Cargo riders consumed the two front seats");
        h.assertTrue("message.tm_wagon.seat_occupied".equals(w.cargo().seats.sit(4,player(h,w.cargo()))),"Occupied seat accepted a second passenger");
        var pose=new WagonPose(w.position().add(.4,0,.3),213,.18F,.12F);w.applyPose(pose);
        for(var p:new Player[]{a,b}) {
            w.positionRider(p);Vec3 expected=pose.point(CargoHold.centre(w.passengerSeat(p)-WagonEntity.CARGO_SEAT_BASE).add(0,.5,0)).subtract(p.getVehicleAttachmentPoint(w));
            h.assertTrue(p.position().distanceTo(expected)<.0001,"Cargo passenger did not follow translation/rotation");
        }
        a.stopRiding();Vec3 target=w.getDismountLocationForPassenger(a);a.setPos(target);a.setPose(Pose.STANDING);
        h.assertTrue(!a.isPassenger()&&h.getLevel().noCollision(a,a.getBoundingBox().deflate(.001)),"Tilted stool dismount was obstructed");
        h.assertTrue(w.driver()==driver&&w.cargoSeatOccupied(5)&&!w.cargoSeatOccupied(4),"Leaving one stool changed other seat identities");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=45)
    public static void mobs_step_onto_stools_but_players_are_never_auto_seated(GameTestHelper h) {
        var w=wagon(h);place(h,w.cargo(),4);place(h,w.cargo(),5);
        h.getLevel().setBlock(w.blockPosition(),WagonContent.FRAME.get().defaultBlockState(),3);
        var pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);pig.setPos(w.pose().point(CargoHold.centre(4).add(0,.5,0)));h.getLevel().addFreshEntity(pig);
        var p=h.makeMockServerPlayerInLevel();p.setPos(w.pose().point(CargoHold.centre(5).add(0,.5,0)));
        h.runAfterDelay(20,()->{
            h.assertTrue(pig.getVehicle()==w&&w.passengerSeat(pig)==WagonEntity.CARGO_SEAT_BASE+4,"Mob standing on a stool did not sit");
            h.assertTrue(!p.isPassenger()&&!w.cargoSeatOccupied(5),"Player was auto-captured by a stool");
            w.cargo().destroy(false);h.assertTrue(!pig.isPassenger(),"Destroying stool retained mob passenger");h.succeed();
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void block_stool_removal_and_chunk_unload_release_riders(GameTestHelper h) {
        var f=frame(h);var hold=f.cargo();place(h,hold,4);place(h,hold,5);
        var a=player(h,hold);var b=player(h,hold);var loader=player(h,hold);
        h.assertTrue(hold.seats.sit(4,a)==null&&hold.seats.sit(5,b)==null,"Block stools could not seat riders");
        h.assertTrue(a.getVehicle() instanceof CargoSeatEntity&&b.getVehicle() instanceof CargoSeatEntity,"Block stool vehicle missing");
        var anchor=a.getVehicle();h.assertTrue(hold.take(4,loader)==null&&!a.isPassenger()&&anchor.isRemoved(),"Taking a stool retained a rider or anchor");
        h.assertTrue(hold.take(4,loader)!=null&&loader.getInventory().items.stream().filter(s->s.is(WagonContent.STOOL.get())).mapToInt(ItemStack::getCount).sum()==1,"Repeated removal duplicated a stool");
        f.onChunkUnloaded();h.assertTrue(!b.isPassenger()&&hold.entry(5)!=null,"Chunk unload retained rider or deleted stool");
        h.assertTrue(h.getLevel().noCollision(a,a.getBoundingBox().deflate(.001))&&h.getLevel().noCollision(b,b.getBoundingBox().deflate(.001)),"Removal left rider trapped");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=90)
    public static void occupied_stools_round_trip_without_passenger_or_item_duplication(GameTestHelper h) {
        var f=frame(h);place(h,f.cargo(),4);var entry=f.cargo().entry(4);var p=player(h,f.cargo());
        h.assertTrue(f.cargo().seats.sit(4,p)==null,"Block seat failed");
        h.assertTrue(f.toggleFrame(null)==null&&!p.isPassenger(),"Assembly retained block-seat passenger");
        var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(6)).getFirst();
        h.assertTrue(f.cargo().empty()&&w.cargo().entry(4)==entry,"Conversion copied stool data");
        h.runAtTickTime(22,()->{
            var pig=EntityType.PIG.create(h.getLevel());pig.setPos(w.position().add(-2,1,0));h.getLevel().addFreshEntity(pig);
            h.assertTrue(w.cargo().seats.sit(4,pig)==null,"Entity seat failed");
            h.assertTrue(f.toggleFrame(null)==null&&!pig.isPassenger(),"Restoration retained cargo passenger");
        });
        h.runAtTickTime(49,()->{
            h.assertTrue(w.isRemoved()&&f.cargo().entry(4)==entry&&f.cargo().boxes().size()==1,"Round trip lost or duplicated stool");
            h.assertTrue(h.getLevel().getEntitiesOfClass(CargoSeatEntity.class,new AABB(f.getBlockPos()).inflate(8)).isEmpty(),"Conversion left a ghost seat entity");h.succeed();
        });
    }
}
