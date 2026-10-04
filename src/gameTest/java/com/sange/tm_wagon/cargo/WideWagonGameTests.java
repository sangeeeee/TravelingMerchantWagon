package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.material.*;
import com.sange.tm_wagon.physics.WagonPose;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class WideWagonGameTests {
    private static ItemStack item(WagonPart part) { return new ItemStack(WagonContent.PART_ITEMS.get(part).get()); }
    private static AssemblyFrameBlockEntity frame(GameTestHelper h,WagonPart seat) {
        var pos=h.absolutePos(new BlockPos(11,2,17));
        h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState(),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(pos);
        h.assertTrue(f.initializeFrame()==null,"Frame failed");
        var parts=WagonEntity.defaultParts();parts.put(WagonSlot.BODY,WagonPart.WIDE_CARGO_BODY);parts.put(WagonSlot.SEAT,seat);
        for(var e:parts.entrySet()) {
            var style=new WagonMaterial(WoodMaterial.values()[e.getKey().ordinal()],e.getKey()==WagonSlot.SEAT?DyeColor.BLUE:DyeColor.WHITE);
            String error=f.install(e.getKey(),e.getValue(),null,style.stack(item(e.getValue()).getItem()));
            h.assertTrue(error==null,"Wide part failed: "+e+" / "+error);
        }
        return f;
    }
    private static WagonEntity wagon(GameTestHelper h,WagonPart seat) {
        var w=WagonContent.WAGON.get().create(h.getLevel());var parts=WagonEntity.defaultParts();
        parts.put(WagonSlot.BODY,WagonPart.WIDE_CARGO_BODY);parts.put(WagonSlot.SEAT,seat);
        w.configure(parts,Direction.NORTH);w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));
        h.getLevel().addFreshEntity(w);return w;
    }
    private static ServerPlayer player(GameTestHelper h,CargoHold hold,Vec3 local) {
        var p=h.makeMockServerPlayerInLevel();p.setNoGravity(true);p.getAbilities().instabuild=false;
        p.setPos(hold.owner().cargoPose().point(local));return p;
    }
    private static void place(GameTestHelper h,CargoHold hold,int slot,ItemStack stack) {
        var p=player(h,hold,hold.centreAt(slot).add(0,2,0));String error=hold.place(slot,stack,p);
        h.assertTrue(error==null,"Cargo "+slot+" failed: "+error);
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void wide_layout_is_centred_and_has_32_independent_slots(GameTestHelper h) {
        var w=wagon(h,WagonPart.TRIPLE_WOODEN_SEAT);var hold=w.cargo();
        var floor=WagonGeometry.partBoxes(WagonPart.WIDE_CARGO_BODY).getFirst();
        h.assertTrue(hold.capacity()==32&&hold.columns()==4&&hold.rows()==8&&floor.getCenter().x==0&&floor.getCenter().z==0,"Wrong centred dimensions");
        h.assertTrue(Math.abs(floor.getZsize()-5.8)<1e-6,"Wide deck retained the fourteen-row length");
        for(int i=0;i<32;i++) {
            var p=player(h,hold,hold.centreAt(i).add(0,2,0));p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE,2));
            h.assertTrue(hold.interact(p,InteractionHand.MAIN_HAND,hold.centreAt(i)).consumesAction()&&p.getMainHandItem().getCount()==1,"Pointed floor slot did not consume exactly one cargo block: "+i);
            h.assertTrue(hold.entry(i)!=null&&hold.anchorSlot(i)==i,"Slot aliases its neighbour");
            var centre=hold.centreAt(i);var reflected=hold.centreAt(31-i);
            h.assertTrue(Math.abs(centre.x+reflected.x)<1e-6&&Math.abs(centre.z+reflected.z)<1e-6,"Grid is not centred on the frame block centre");
        }
        var saved=hold.save(h.getLevel().registryAccess(),false);hold.load(saved,h.getLevel().registryAccess());
        h.assertTrue(saved.getList("Entries",10).size()==32&&hold.entry(31)!=null,"Last cargo slots lost on reload");
        var extra=new ItemStack(Items.STONE,2);var p=player(h,hold,hold.centreAt(31).add(0,2,0));
        h.assertTrue(hold.place(32,extra,p)!=null&&extra.getCount()==2&&hold.entry(32)==null,"A thirty-third slot accepted or consumed cargo");
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void wide_mats_reserve_three_rows_in_the_same_column(GameTestHelper h) {
        var w=wagon(h,WagonPart.DOUBLE_SEAT);var hold=w.cargo();
        for(int col=0;col<4;col++)place(h,hold,28+col,new ItemStack(WagonContent.STRAW_MAT.get()));
        for(int col=0;col<4;col++) {
            h.assertTrue(hold.anchorSlot(20+col)==28+col&&hold.anchorSlot(24+col)==28+col,"Mat reserved another column");
            h.assertTrue(hold.entry(16+col)==null,"Mat reserved a fourth row");
            if(col>0)h.assertTrue(Math.abs(hold.matBounds(28+col-1).maxX-hold.matBounds(28+col).minX)<1e-6,"Adjacent mats do not meet cleanly");
        }
        var saved=hold.save(h.getLevel().registryAccess(),false);hold.load(saved,h.getLevel().registryAccess());
        h.assertTrue(saved.getList("Entries",10).size()==4&&hold.anchorSlot(23)==31,"Reload copied mats into reserved cells");
        var p=player(h,hold,hold.centreAt(31).add(0,2,0));h.assertTrue(hold.take(23,p)==null&&hold.entry(23)==null&&hold.entry(27)==null&&hold.entry(31)==null,"Reserved-cell removal left mat ownership");
        h.assertTrue(p.getInventory().items.stream().filter(s->s.is(WagonContent.STRAW_MAT.get())).mapToInt(ItemStack::getCount).sum()==1,"Mat removal duplicated the item");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void triples_are_restricted_to_wide_bodies_without_consuming_items(GameTestHelper h) {
        for(var body:new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY}) {
            var parts=WagonEntity.defaultParts();parts.put(WagonSlot.BODY,body);
            for(var seat:new WagonPart[]{WagonPart.TRIPLE_SEAT,WagonPart.TRIPLE_WOODEN_SEAT}) {
                parts.put(WagonSlot.SEAT,seat);h.assertTrue(AssemblyFrameBlockEntity.complete(parts)==(body==WagonPart.WIDE_CARGO_BODY),"Triple body restriction failed");
            }
        }
        var pos=h.absolutePos(new BlockPos(11,2,17));h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState(),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(pos);f.initializeFrame();f.install(WagonSlot.BODY,WagonPart.CARGO_BODY,null,item(WagonPart.CARGO_BODY));
        var stack=item(WagonPart.TRIPLE_SEAT);stack.setCount(2);
        h.assertTrue("message.tm_wagon.wrong_slot".equals(f.install(WagonSlot.SEAT,WagonPart.TRIPLE_SEAT,null,stack))&&stack.getCount()==2&&!f.has(WagonSlot.SEAT),"Rejected triple consumed item");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void triple_upgrade_recipes_preserve_wood_and_reject_mixed_wood(GameTestHelper h) {
        var recipe=new WagonComponentRecipe("triple_wooden_seat",List.of(),1);
        for(var wood:WoodMaterial.values()) {
            var style=new WagonMaterial(wood,DyeColor.WHITE);
            var single=style.stack(item(WagonPart.SINGLE_WOODEN_SEAT).getItem());var dual=style.stack(item(WagonPart.DOUBLE_WOODEN_SEAT).getItem());
            for(var stacks:List.of(List.of(single,single.copy(),single.copy()),List.of(single,dual,ItemStack.EMPTY))) {
                var in=CraftingInput.of(3,1,stacks);var out=recipe.assemble(in,h.getLevel().registryAccess());
                h.assertTrue(out.is(item(WagonPart.TRIPLE_WOODEN_SEAT).getItem())&&WagonMaterial.of(out).wood()==wood,"Triple upgrade lost material");
            }
        }
        var spruce=new WagonMaterial(WoodMaterial.SPRUCE,DyeColor.WHITE);
        h.assertTrue(!recipe.matches(CraftingInput.of(2,1,List.of(item(WagonPart.SINGLE_WOODEN_SEAT),spruce.stack(item(WagonPart.DOUBLE_WOODEN_SEAT).getItem()))),h.getLevel()),"Mixed seat woods accepted");
        h.assertTrue(!recipe.matches(CraftingInput.of(2,1,List.of(item(WagonPart.DOUBLE_WOODEN_SEAT),item(WagonPart.DOUBLE_WOODEN_SEAT))),h.getLevel()),"Four seats produce a triple");h.succeed();
    }
    private static void aim(ServerPlayer p,WagonEntity w,Vec3 eye,Vec3 target) {
        Vec3 start=w.pose().point(eye),end=w.pose().point(target),d=end.subtract(start).normalize();
        p.setPos(start.subtract(0,p.getEyeHeight(),0));p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.asin(d.y)));
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void triple_driver_positions_are_independent_of_cargo_stools(GameTestHelper h) {
        for(var kind:new WagonPart[]{WagonPart.TRIPLE_WOODEN_SEAT,WagonPart.TRIPLE_SEAT}) {
            var w=wagon(h,kind);var hold=w.cargo();
            h.assertTrue(Math.abs(WagonGeometry.partBoxes(kind).getFirst().getXsize()-WagonGeometry.partBoxes(WagonPart.DOUBLE_SEAT).getFirst().getXsize()*WagonPart.WIDE_CARGO_BODY.widthScale())<2e-6,"Triple seat does not match the wider body");
            var riders=new ArrayList<ServerPlayer>();
            for(int seat:new int[]{2,1,0}) {
                var p=player(h,hold,Vec3.ZERO);double x=(seat-1)*kind.seatSpacing();var target=new Vec3(x,kind.isWoodenSeat()?31.48/16:34.4/16,-3.275);
                aim(p,w,new Vec3(x,2.8,-4.3),target);
                h.assertTrue(w.interactAt(p,w.pose().point(target).subtract(w.position()),InteractionHand.MAIN_HAND).consumesAction()&&p.getVehicle()==w&&w.passengerSeat(p)==seat,"Clicked triple position not boarded: "+seat);
                riders.add(p);
                h.assertTrue(w.driver()==riders.stream().filter(r->w.passengerSeat(r)==1).findFirst().orElse(null),"Triple driver is not the middle rider");
                var local=w.pose().local(w.getPassengerRidingPosition(p));
                h.assertTrue(Math.abs(local.x-x)<1e-6,"Rider not centred on the selected third of the bench");
            }
            place(h,hold,31,new ItemStack(WagonContent.STOOL.get()));var p=player(h,hold,hold.centreAt(31).add(0,1,0));
            h.assertTrue(hold.seats.sit(31,p)==null&&w.passengerSeat(p)==WagonEntity.CARGO_SEAT_BASE+31,"Last stool overlaps triple driver index");
            for(var rider:riders) { w.positionRider(rider);var before=w.getPassengerRidingPosition(rider);rider.stopRiding();h.assertTrue(rider.position().distanceTo(before.add(0,.001,0))<.01,"Triple dismount did not stay on the seat"); }
            w.discard();
        }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void old_saved_cargo_seat_indices_migrate_to_reserved_triple_range(GameTestHelper h) {
        var w=wagon(h,WagonPart.DOUBLE_SEAT);place(h,w.cargo(),31,new ItemStack(WagonContent.STOOL.get()));
        var p=player(h,w.cargo(),w.cargo().centreAt(31));h.assertTrue(w.cargo().seats.sit(31,p)==null,"Fixture boarding failed");
        var tag=new CompoundTag();w.saveWithoutId(tag);tag.remove("SeatLayoutVersion");tag.getCompound("Seats").putInt(p.getUUID().toString(),33);
        var restored=WagonContent.WAGON.get().create(h.getLevel());restored.load(tag);
        h.assertTrue(restored.passengerSeat(p)==34&&restored.cargo().entry(31)!=null,"Legacy seat index not migrated");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=90)
    public static void wide_cargo_and_triple_cabinet_round_trip_keep_one_inventory_owner(GameTestHelper h) {
        var f=frame(h,WagonPart.TRIPLE_SEAT);var hold=f.cargo();
        place(h,hold,31,new ItemStack(Items.CHEST));var chest=hold.entry(31);chest.inventory.setItem(0,new ItemStack(Items.DIAMOND,17));
        var p=player(h,hold,new Vec3(-3,0,-3.2));var c=hold.cabinet();
        h.assertTrue(c.install(new ItemStack(WagonContent.CABINET.get()),p,new Vec3(-WagonPart.TRIPLE_SEAT.seatHalfWidth(),1.7,-3.2))==null&&c.inventory().getContainerSize()==54,"Triple cabinet is not a 54-slot wide cabinet");
        var inv=c.inventory();inv.setItem(53,new ItemStack(Items.EMERALD,29));
        var styles=f.materials();h.assertTrue(f.toggleFrame(null)==null,"Wide assembly failed");
        var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(12)).getFirst();
        h.assertTrue(hold.empty()&&w.cargo().entry(31)==chest&&w.cargo().cabinet().inventory()==inv&&w.materials().equals(styles),"Assembly copied inventory or lost styles");
        h.runAtTickTime(24,()->h.assertTrue(f.toggleFrame(null)==null,"Wide restore failed to start"));
        h.runAtTickTime(49,()->{
            h.assertTrue(w.isRemoved()&&hold.entry(31)==chest&&c.inventory()==inv&&c.inventory().getContainerSize()==54,"Wide restore changed inventory ownership");
            h.assertTrue(chest.inventory.getItem(0).getCount()==17&&inv.getItem(53).getCount()==29&&f.part(WagonSlot.SEAT)==WagonPart.TRIPLE_SEAT,"Round trip lost contents or seat");h.succeed();
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void wide_cover_rolls_all_eight_rows_and_uses_all_outline_faces(GameTestHelper h) {
        var f=frame(h,WagonPart.DOUBLE_SEAT);var hold=f.cargo();var cover=hold.cover();
        var p=player(h,hold,new Vec3(-3,0,0));
        h.assertTrue(cover.install(new ItemStack(WagonContent.CARGO_COVER.get()),p,new Vec3(-18.5/16*1.75,1.9,0))==null,"Wide cover install failed");
        h.assertTrue(cover.rows()==8&&cover.boxes(WagonPart.WIDE_CARGO_BODY).getFirst().getXsize()>4,"Cover did not widen");
        for(int row=0;row<8;row++) {
            var b=cover.selectionBoxes(WagonPart.WIDE_CARGO_BODY).getFirst();var local=b.getCenter();p.setPos(f.cargoPose().point(local.add(0,2,0)));
            h.assertTrue(cover.step(p,local,1)==null&&cover.openRows()==row+1,"Wide cover stopped before final row");
        }
        var b=cover.rollBox(WagonPart.WIDE_CARGO_BODY);var local=new Vec3(0,b.maxY,b.getCenter().z);p.setPos(f.cargoPose().point(local.add(0,2,0)));
        h.assertTrue(cover.boxes(WagonPart.WIDE_CARGO_BODY).isEmpty()&&cover.step(p,local,-1)==null&&cover.openRows()==7,"Fully rolled wide cover cannot spread from above");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void wide_canopy_keeps_front_and_rear_curtains_inside_the_gate(GameTestHelper h) {
        var w=wagon(h,WagonPart.TRIPLE_SEAT);var hold=w.cargo();var saved=hold.save(h.getLevel().registryAccess(),false);var canopy=new CompoundTag();
        canopy.putBoolean("Installed",true);canopy.putBoolean("FrontClosed",true);canopy.putBoolean("RearClosed",true);saved.put("Canopy",canopy);hold.load(saved,h.getLevel().registryAccess());w.cargoGeometryChanged();
        var roof=hold.canopy();var body=WagonPart.WIDE_CARGO_BODY;
        h.assertTrue(roof.back(body)<hold.tailBox().minZ&&roof.curtainZ(body,false)<hold.tailBox().minZ,"Curtain penetrates the closed tailgate");
        h.assertTrue(roof.boxes(body).stream().mapToDouble(b->b.maxY).max().orElseThrow()<5,"Wide roof exceeds five blocks");
        h.assertTrue(roof.boxes(body).stream().anyMatch(b->b.minX< -1.9)&&roof.curtainZ(body,true)< -2.6,"Canopy not sized to the centred wide body");
        h.assertTrue(hold.cover().obstructs(new Vec3(-3,2.6,0),new Vec3(0,2,0)),"Cargo interaction passes through wide side canvas");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void wide_wheels_and_hitch_follow_the_new_axle_positions(GameTestHelper h) {
        var w=wagon(h,WagonPart.SINGLE_SEAT);var body=WagonPart.WIDE_CARGO_BODY;
        h.assertTrue(Math.abs(w.wheelbase()-4.55)<1e-6,"Wide wheelbase retained old size");
        for(int i=0;i<4;i++) {
            var local=w.pose().local(w.wheelCentre(i,w.pose()));
            h.assertTrue(Math.abs(Math.abs(local.x)-body.wheelHalfTrack())<1e-5&&Math.abs(local.z-(i<2?-2.65:1.9))<1e-5,"Wheel misplaced on wide body");
        }
        h.assertTrue(w.pose().local(w.horsePosition(0)).z< -5.7&&w.pose().local(w.getRopeHoldPosition(1)).z< -3.7,"Horse/hitch retained old coordinates");
        w.applyPose(new WagonPose(w.position(),213,.12F,.06F));
        var rear=w.pose().local(w.wheelCentre(3,w.pose()));h.assertTrue(Math.abs(rear.z-1.9)<1e-5,"Rotated wide axle shifted");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void wide_wagon_drives_reverses_and_turns_with_distance_matched_wheels(GameTestHelper h) {
        for(int x=1;x<24;x++)for(int z=1;z<24;z++)h.setBlock(new BlockPos(x,1,z),net.minecraft.world.level.block.Blocks.STONE);
        var w=wagon(h,WagonPart.TRIPLE_SEAT);var p=player(h,w.cargo(),Vec3.ZERO);
        var target=new Vec3(0,34.4/16,-3.275);aim(p,w,new Vec3(0,2.8,-4.3),target);
        h.assertTrue(w.interactAt(p,w.pose().point(target).subtract(w.position()),InteractionHand.MAIN_HAND).consumesAction()&&w.driver()==p,"Middle wide driver failed");
        var side=player(h,w.cargo(),Vec3.ZERO);target=new Vec3(WagonPart.TRIPLE_SEAT.seatSpacing(),34.4/16,-3.275);
        aim(side,w,target.add(0,.8,-1),target);w.interactAt(side,w.pose().point(target).subtract(w.position()),InteractionHand.MAIN_HAND);
        h.assertTrue(side.getVehicle()==w&&w.driver()!=side,"Side rider took driving permission");
        double initial=w.getZ();float beforeYaw=w.getYRot();w.acceptInput(side,1,1);w.tick();
        h.assertTrue(Math.abs(w.getZ()-initial)<.001&&Math.abs(w.getYRot()-beforeYaw)<.001,"Side rider moved or steered the wagon");
        var horse=EntityType.HORSE.create(h.getLevel());horse.setNoAi(true);horse.setPos(w.horsePosition(0));h.getLevel().addFreshEntity(horse);horse.setLeashedTo(p,true);
        h.assertTrue(w.attachHorse(p,horse,0)==null,"Wide hitch rejected horse");
        initial=w.getZ();
        for(int i=0;i<12;i++) { w.acceptInput(p,1,0);w.tick(); }
        double forward=initial-w.getZ();
        h.assertTrue(Math.abs(forward-.98865)<.025,"Wide wagon cannot advance at normal speed: "+forward);
        for(int i=0;i<4;i++)h.assertTrue(Math.abs(w.renderWheel(i,1)+forward/com.sange.tm_wagon.physics.WagonPhysics.radius(i))<.025,"Wide wheel phase does not match travel");
        for(int i=0;i<5;i++) { w.acceptInput(p,-1,0);w.tick(); }
        h.assertTrue(w.getDeltaMovement().horizontalDistance()<1e-6,"Wide wagon did not brake before reversing");
        double before=w.getZ();for(int i=0;i<12;i++) { w.acceptInput(p,-1,0);w.tick(); }
        h.assertTrue(Math.abs(w.getZ()-before-.41145)<.025,"Wide reverse failed");
        float yaw=w.getYRot();for(int i=0;i<12;i++) { w.acceptInput(p,1,1);w.tick(); }
        h.assertTrue(w.getYRot()>yaw+1&&w.hasHorse(horse.getUUID())&&!w.falling(),"Wide turning lost horse/support");h.succeed();
    }
}
