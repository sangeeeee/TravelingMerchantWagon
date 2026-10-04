package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class CargoCoverGameTests {
    private static final Vec3 SIDE=new Vec3(-1.15625,1.9,.5);
    private static AssemblyFrameBlockEntity frame(GameTestHelper h,boolean extended) {
        return frame(h,extended,Direction.NORTH);
    }
    private static AssemblyFrameBlockEntity frame(GameTestHelper h,boolean extended,Direction facing) {
        var pos=h.absolutePos(new BlockPos(11,2,17));h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState().setValue(AssemblyFrameBlock.FACING,facing),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(f.initializeFrame()==null,"Frame init failed");
        var modules=new java.util.EnumMap<WagonSlot,WagonPart>(WagonSlot.class);modules.putAll(WagonEntity.defaultParts());
        if(extended)modules.put(WagonSlot.BODY,WagonPart.LONG_CARGO_BODY);
        for(var part:modules.entrySet())h.assertTrue(f.install(part.getKey(),part.getValue(),null,new ItemStack(WagonContent.PART_ITEMS.get(part.getValue()).get()))==null,"Module init failed");return f;
    }
    private static Player player(GameTestHelper h,CargoHold hold) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(hold.owner().cargoPose().point(new Vec3(-3,0,0)));return p;
    }
    private static void install(GameTestHelper h,CargoHold hold,Player p) {
        h.assertTrue(hold.cover().install(new ItemStack(WagonContent.CARGO_COVER.get()),p,SIDE)==null,"Cover install failed");
    }
    private static Vec3 cloth(CargoCover c,WagonPart body) {
        return c.openRows()==0?new Vec3(0,CargoCover.TOP,0):new Vec3(0,CargoCover.TOP+c.radius(),c.rollZ(body));
    }
    private static void step(GameTestHelper h,CargoHold hold,Player p,int direction) {
        h.assertTrue(hold.cover().step(p,cloth(hold.cover(),hold.owner().cargoBody()),direction)==null,"Cover row operation failed");
    }
    private static int carried(Player p) { int count=0;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(WagonContent.CARGO_COVER.get()))count+=p.getInventory().getItem(i).getCount();return count; }
    private static int drops(GameTestHelper h,Vec3 pos) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos,pos).inflate(6),e->e.getItem().is(WagonContent.CARGO_COVER.get())).stream().mapToInt(e->e.getItem().getCount()).sum();
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void small_rider_fits_below_cover_without_slot_blanket_ban(GameTestHelper h) {
        var f=frame(h,false);var hold=f.cargo();var p=player(h,hold);
        h.assertTrue(hold.place(4,new ItemStack(WagonContent.STOOL.get()),p)==null,"Stool setup failed");install(h,hold,p);
        var rabbit=EntityType.RABBIT.create(h.getLevel());rabbit.setAge(-24000);rabbit.setNoAi(true);rabbit.setPos(p.position());h.getLevel().addFreshEntity(rabbit);
        h.assertTrue(hold.seats.available(4,rabbit)&&hold.seats.sit(4,rabbit)==null,"Small rider rejected solely because cloth covers slot");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void cover_installs_on_every_cargo_wall_and_returns_exactly_once(GameTestHelper h) {
        var f=frame(h,false);var hold=f.cargo();var p=player(h,hold);
        h.assertTrue(!((net.minecraft.world.item.Item)WagonContent.CARGO_COVER.get() instanceof BlockItem)&&!CargoHold.allowed(new ItemStack(WagonContent.CARGO_COVER.get())),"Cover became a world block or cargo entry");
        for(Vec3 side:new Vec3[]{SIDE,new Vec3(1.15625,1.9,.5),new Vec3(0,1.9,-1.5625),new Vec3(0,1.9,2.3125)}) {
            var stack=new ItemStack(WagonContent.CARGO_COVER.get(),2);int before=carried(p);
            h.assertTrue(hold.cover().install(stack,p,side)==null&&stack.getCount()==1&&hold.cover().openRows()==0,"Wall install consumed wrong number");
            h.assertTrue(hold.cover().install(stack,p,side)!=null&&stack.getCount()==1,"Repeated installation consumed item");
            h.assertTrue(hold.cover().remove(p,side)==null&&carried(p)==before+1,"Removal did not return exactly one cover");
            h.assertTrue(hold.cover().remove(p,side)!=null&&carried(p)==before+1&&!hold.cover().installed(),"Repeated removal duplicated cover");
        }h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void roll_rows_open_from_front_and_spread_in_reverse_for_both_body_lengths(GameTestHelper h) {
        var f=frame(h,true);var hold=f.cargo();var p=player(h,hold);install(h,hold,p);
        double r=0;
        for(int row=0;row<=6;row++) {
            h.assertTrue(hold.cover().openRows()==row,"Wrong exposed row count");
            for(int slot=0;slot<12;slot++)h.assertTrue(hold.cover().covered(slot)==(slot/2>=row),"Cover opened cargo in wrong direction");
            if(row>0)h.assertTrue(hold.cover().radius()>r,"Roll did not thicken");r=hold.cover().radius();
            if(row<6)step(h,hold,p,1);
        }
        step(h,hold,p,1);h.assertTrue(hold.cover().openRows()==6&&hold.cover().boxes(hold.owner().cargoBody()).isEmpty(),"Fully rolled state retained decorative collision or exceeded limit");
        for(int row=6;row>0;row--)step(h,hold,p,-1);
        step(h,hold,p,-1);h.assertTrue(hold.cover().openRows()==0&&hold.cover().boxes(hold.owner().cargoBody()).size()==1,"Spreading did not restore full sheet");
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void thin_cloth_clears_both_seat_backrests_without_decorative_collisions(GameTestHelper h) {
        var f=frame(h,false);var hold=f.cargo();var p=player(h,hold);install(h,hold,p);
        for(var body:new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY}) {
            var boxes=hold.cover().boxes(body);h.assertTrue(boxes.size()==1,"Cloth needs only one flat collision box");
            var cloth=boxes.getFirst();h.assertTrue(Math.abs(cloth.getYsize()-1.0/64)<1e-9,"Cloth thickness was not halved");
            for(var seat:new WagonPart[]{WagonPart.SINGLE_SEAT,WagonPart.DOUBLE_SEAT})
                for(var box:WagonGeometry.partBoxes(seat))h.assertTrue(!cloth.intersects(box),"Cloth overlaps driver seat");
            h.assertTrue(hold.cover().selectionBoxes(body).stream().anyMatch(b->b.minY<cloth.minY),"Cosmetic hanging edges lost click outline");
        }h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=50)
    public static void standard_roll_remains_clickable_without_collision_when_tailgate_open(GameTestHelper h) { decorativeRoll(h,false); }
    @GameTest(template="assembly_test",timeoutTicks=50)
    public static void extended_roll_remains_clickable_without_collision_when_tailgate_open(GameTestHelper h) { decorativeRoll(h,true); }
    private static void decorativeRoll(GameTestHelper h,boolean extended) {
        var f=frame(h,extended);var hold=f.cargo();var p=player(h,hold);install(h,hold,p);
        for(int row=0;row<hold.capacity()/2;row++)step(h,hold,p,1);
        h.assertTrue(hold.toggleGate()==null,"Tailgate failed to open");
        h.runAtTickTime(22,()->{
            h.assertTrue(hold.gateOpen()&&hold.cover().boxes(f.cargoBody()).isEmpty(),"Decorative roll retained collision");
            double z=hold.cover().rollZ(f.cargoBody());
            Vec3 start=f.cargoPose().point(new Vec3(0,CargoCover.TOP+.8,z));
            Vec3 end=f.cargoPose().point(new Vec3(0,CargoCover.TOP-.8,z));
            var outline=h.getLevel().clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            var collision=h.getLevel().clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            h.assertTrue(outline.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&f.cargoPose().local(outline.getLocation()).y>CargoCover.TOP,"Fully rolled cover lost native block picking");
            h.assertTrue(collision.getType()==net.minecraft.world.phys.HitResult.Type.MISS||f.cargoPose().local(collision.getLocation()).y<CargoCover.TOP-.1,"Roll became a physical collider");
            p.setShiftKeyDown(true);var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,outline.getBlockPos(),outline);
            CargoInteractions.block(event);h.assertTrue(event.isCanceled()&&hold.cover().openRows()==hold.capacity()/2-1,"Actual roll outline click did not spread");
            p.setShiftKeyDown(false);step(h,hold,p,1);
            h.assertTrue(f.toggleFrame(null)==null,"Fully rolled assembly failed");
            var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(6)).getFirst();
            Vec3 top=w.pose().point(new Vec3(0,CargoCover.TOP+2*w.cargo().cover().radius(),z));
            h.assertTrue(w.pick(top.add(0,.3,0),top.add(0,-.2,0)).isPresent(),"Entity visual roll lost picking");
            h.assertTrue(!w.intersects(new AABB(top,top).inflate(.015)),"Entity roll retained physical collision");
            h.assertTrue(w.getBoundingBox().inflate(.001).contains(top),"Broad phase excludes decorative roll");h.succeed();
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=45)
    public static void standard_full_roll_every_outline_face_and_edge_spreads(GameTestHelper h) { rollFaces(h,false); }
    @GameTest(template="assembly_test",timeoutTicks=45)
    public static void extended_full_roll_every_outline_face_and_edge_spreads(GameTestHelper h) { rollFaces(h,true); }
    private static void rollFaces(GameTestHelper h,boolean extended) {
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            var f=frame(h,extended,facing);var hold=f.cargo();var p=player(h,hold);install(h,hold,p);
            for(int row=0;row<hold.capacity()/2;row++)step(h,hold,p,1);
            // Open the tailgate so the underside is visible without clicking through wood.
            var saved=hold.save(h.getLevel().registryAccess(),false);saved.putBoolean("GateTarget",true);saved.putBoolean("GateCollision",true);saved.putLong("GateStart",Long.MIN_VALUE);
            hold.load(saved,h.getLevel().registryAccess());h.assertTrue(f.cargoGeometryChanged()==null,"Gate geometry failed");
            AABB outlineBox=null;
            for(var cell:hold.cover().selectionCells(f.cargoBody(),facing).entrySet())for(var box:cell.getValue()) {
                var pos=f.getBlockPos().offset(cell.getKey());var world=box.move(pos.getX(),pos.getY(),pos.getZ());
                outlineBox=outlineBox==null?world:outlineBox.minmax(world);
            }
            h.assertTrue(outlineBox!=null&&hold.cover().boxes(f.cargoBody()).isEmpty(),"Full roll outline absent or became solid");
            var centre=outlineBox.getCenter();var half=new Vec3(outlineBox.getXsize()/2,outlineBox.getYsize()/2,outlineBox.getZsize()/2);
            for(Direction face:Direction.values())for(double u:new double[]{-.98,0,.98})for(double v:new double[]{-.98,0,.98}) {
                Vec3 point=switch(face.getAxis()) {
                    case X->centre.add(face.getStepX()*half.x,u*half.y,v*half.z);
                    case Y->centre.add(u*half.x,face.getStepY()*half.y,v*half.z);
                    case Z->centre.add(u*half.x,v*half.y,face.getStepZ()*half.z);
                };
                Vec3 axis=new Vec3(face.getStepX(),face.getStepY(),face.getStepZ());
                var hit=h.getLevel().clip(new net.minecraft.world.level.ClipContext(point.add(axis.scale(.12)),point.subtract(axis.scale(.08)),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
                h.assertTrue(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&hit.getLocation().distanceToSqr(point)<1e-6,"Native outline ray missed "+facing+"/"+face);
                for(boolean sneak:new boolean[]{false,true}) {
                    p.setShiftKeyDown(sneak);
                    var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,hit.getBlockPos(),hit);
                    CargoInteractions.block(event);
                    h.assertTrue(event.isCanceled()&&hold.cover().installed()&&hold.cover().openRows()==hold.capacity()/2-(sneak?1:0),"Full roll click rejected "+facing+"/"+face+" at "+f.cargoPose().local(hit.getLocation()));
                }
                p.setShiftKeyDown(false);step(h,hold,p,1);
            }
            f.dismantle(false,true);
        }h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=45)
    public static void tilted_standard_entity_full_roll_every_face_spreads(GameTestHelper h) { entityRollFaces(h,false); }
    @GameTest(template="assembly_test",timeoutTicks=45)
    public static void tilted_extended_entity_full_roll_every_face_spreads(GameTestHelper h) { entityRollFaces(h,true); }
    private static void entityRollFaces(GameTestHelper h,boolean extended) {
        var f=frame(h,extended);var hold=f.cargo();var p=player(h,hold);install(h,hold,p);
        for(int row=0;row<hold.capacity()/2;row++)step(h,hold,p,1);
        var saved=hold.save(h.getLevel().registryAccess(),false);saved.putBoolean("GateTarget",true);saved.putBoolean("GateCollision",true);saved.putLong("GateStart",Long.MIN_VALUE);
        hold.load(saved,h.getLevel().registryAccess());h.assertTrue(f.toggleFrame(null)==null,"Entity conversion failed");
        var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(6)).getFirst();
        w.applyPose(new com.sange.tm_wagon.physics.WagonPose(w.position(),29.7F,.13F,-.07F));hold=w.cargo();
        var box=hold.cover().rollBox(w.cargoBody());var centre=box.getCenter();var half=new Vec3(box.getXsize()/2,box.getYsize()/2,box.getZsize()/2);
        for(Direction face:Direction.values())for(double u:new double[]{-.98,0,.98})for(double v:new double[]{-.98,0,.98}) {
            Vec3 point=switch(face.getAxis()) {
                case X->centre.add(face.getStepX()*half.x,u*half.y,v*half.z);
                case Y->centre.add(u*half.x,face.getStepY()*half.y,v*half.z);
                case Z->centre.add(u*half.x,v*half.y,face.getStepZ()*half.z);
            };
            Vec3 axis=new Vec3(face.getStepX(),face.getStepY(),face.getStepZ());
            Vec3 start=w.pose().point(point.add(axis.scale(.12))),end=w.pose().point(point.subtract(axis.scale(.08)));
            var hit=w.pick(start,end);h.assertTrue(hit.isPresent()&&hit.get().distanceToSqr(w.pose().point(point))<1e-6,"Tilted entity outline ray missed "+face);
            p.setPos(start.add(0,-p.getEyeHeight(),0));Vec3 delta=end.subtract(start);
            p.setYRot((float)-Math.toDegrees(Math.atan2(delta.x,delta.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
            for(boolean sneak:new boolean[]{false,true}) {
                p.setShiftKeyDown(sneak);var result=w.interactAt(p,hit.get().subtract(w.position()),InteractionHand.MAIN_HAND);
                h.assertTrue(result.consumesAction()&&hold.cover().installed()&&hold.cover().openRows()==hold.capacity()/2-(sneak?1:0),"Tilted entity full roll click rejected "+face);
            }
            p.setShiftKeyDown(false);step(h,hold,p,1);
        }h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void cloth_obstructed_cargo_is_unusable_and_open_rows_allow_place_take_and_menus(GameTestHelper h) {
        var f=frame(h,false);var hold=f.cargo();var p=h.makeMockServerPlayerInLevel();p.getAbilities().instabuild=false;p.setPos(f.cargoPose().point(new Vec3(-.5,2.9,-.96)));
        h.assertTrue(hold.place(0,new ItemStack(Items.CHEST),p)==null,"Chest placement failed");var chest=hold.entry(0);chest.inventory.setItem(0,new ItemStack(Items.DIAMOND,23));CargoMenus.open(hold,chest,p);
        h.assertTrue(!(p.containerMenu instanceof InventoryMenu)&&chest.opened,"Chest did not open");install(h,hold,p);
        h.assertTrue(p.containerMenu==p.inventoryMenu&&!hold.valid(chest,p)&&!chest.opened,"Cover left container usable");
        var stack=new ItemStack(Items.STONE,2);
        h.assertTrue(hold.place(1,stack,p)!=null&&stack.getCount()==2&&hold.take(0,p)!=null&&chest.inventory.getItem(0).getCount()==23,"Closed cover permitted cargo manipulation");
        CargoMenus.open(hold,chest,p);h.assertTrue(p.containerMenu==p.inventoryMenu,"Covered menu could be reopened");
        step(h,hold,p,1);h.assertTrue(hold.valid(chest,p)&&hold.place(1,stack,p)==null&&stack.getCount()==1,"First exposed row is not usable");
        CargoMenus.open(hold,chest,p);h.assertTrue(p.containerMenu!=p.inventoryMenu,"Exposed chest unusable");
        step(h,hold,p,-1);h.assertTrue(p.containerMenu==p.inventoryMenu&&chest.inventory.getItem(0).getCount()==23,"Spreading lost contents or left menu open");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=85)
    public static void standard_covered_cargo_is_usable_through_open_tailgate(GameTestHelper h) { tailgateAccess(h,false); }
    @GameTest(template="assembly_test",timeoutTicks=85)
    public static void extended_covered_cargo_is_usable_through_open_tailgate(GameTestHelper h) { tailgateAccess(h,true); }
    private static void tailgateAccess(GameTestHelper h,boolean extended) {
        var f=frame(h,extended);var hold=f.cargo();int slot=hold.capacity()-2;
        var p=h.makeMockServerPlayerInLevel();p.setNoGravity(true);p.getAbilities().instabuild=false;
        p.setPos(f.cargoPose().point(new Vec3(-.5,2.9,CargoHold.centre(slot).z)));
        h.assertTrue(hold.place(slot,new ItemStack(Items.CHEST),p)==null,"Rear chest placement failed");
        var chest=hold.entry(slot);chest.inventory.setItem(0,new ItemStack(Items.DIAMOND,27));install(h,hold,p);
        p.setPos(f.cargoPose().point(new Vec3(-.5,.25,hold.cover().back(f.cargoBody())+.8)));
        h.assertTrue(!hold.valid(chest,p)&&hold.take(slot,p)!=null,"Closed tailgate allowed access through wood");
        h.assertTrue(hold.toggleGate()==null,"Tailgate open failed");
        h.runAtTickTime(22,()->{
            h.assertTrue(hold.cover().covered(slot)&&hold.cover().openRows()==0&&hold.valid(chest,p),"Open tailgate still blanket-blocks covered cargo");
            p.setPos(f.cargoPose().point(new Vec3(-.5,1,hold.cover().back(f.cargoBody())+.8)));
            h.assertTrue(hold.valid(chest,p),"Visible lower cargo face was rejected because its upper edge is covered");
            p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            nativeClick(h,f,p,CargoHold.centre(slot).add(0,.3,0));
            h.assertTrue(p.containerMenu!=p.inventoryMenu&&chest.opened,"Real rear click did not open covered chest");
            step(h,hold,p,1);step(h,hold,p,-1);
            h.assertTrue(p.containerMenu!=p.inventoryMenu&&hold.valid(chest,p),"Spreading closed unobstructed rear menu");
            h.assertTrue(hold.toggleGate()==null,"Tailgate close failed");
        });
        h.runAtTickTime(44,()->{
            h.assertTrue(!hold.gateOpen()&&!hold.valid(chest,p)&&p.containerMenu==p.inventoryMenu,"Closed tailgate left covered cargo menu usable");
            h.assertTrue(chest.inventory.getItem(0).getCount()==27,"Obstruction lost chest contents");
            h.assertTrue(hold.toggleGate()==null,"Tailgate reopen failed");
        });
        h.runAtTickTime(66,()->{
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE,2));
            nativeClick(h,f,p,CargoHold.centre(slot+1));
            h.assertTrue(hold.entry(slot+1)!=null&&p.getMainHandItem().getCount()==1&&hold.cover().covered(slot+1),"Covered empty floor could not accept cargo from the rear");
            p.setShiftKeyDown(true);nativeClick(h,f,p,CargoHold.centre(slot).add(0,.3,0));
            h.assertTrue(hold.entry(slot)==null&&hold.take(slot,p)!=null,"Rear unload retained entry or allowed duplicate removal: installed="+hold.cover().installed()+", gate="+hold.gateOpen()+", entry="+(hold.entry(slot)!=null)+", valid="+(hold.entry(slot)!=null&&hold.valid(hold.entry(slot),p)));
            int diamonds=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(f.getBlockPos()).inflate(6),e->e.getItem().is(Items.DIAMOND)).stream().mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(diamonds==27,"Rear unload duplicated or lost container contents");h.succeed();
        });
    }
    private static void nativeClick(GameTestHelper h,AssemblyFrameBlockEntity f,Player p,Vec3 target) {
        Vec3 start=p.getEyePosition(),end=f.cargoPose().point(target);
        var hit=h.getLevel().clip(new net.minecraft.world.level.ClipContext(start,end.add(end.subtract(start).normalize().scale(.02)),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        h.assertTrue(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK,"Native ray did not find cargo");
        var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,hit.getBlockPos(),hit);
        CargoInteractions.block(event);h.assertTrue(event.isCanceled(),"Native cargo click bypassed handler: target="+target+", hit="+f.cargoPose().local(hit.getLocation())+", state="+h.getLevel().getBlockState(hit.getBlockPos())+", eye="+f.cargoPose().local(start));
    }
    @GameTest(template="assembly_test",timeoutTicks=55)
    public static void entity_cover_uses_eye_ray_and_allows_rear_cargo_without_rolling(GameTestHelper h) {
        var f=frame(h,false);var hold=f.cargo();var p=player(h,hold);
        h.assertTrue(hold.place(8,new ItemStack(Items.BARREL),p)==null,"Rear barrel placement failed");install(h,hold,p);
        h.assertTrue(hold.toggleGate()==null,"Tailgate open failed");
        h.runAtTickTime(22,()->{
            h.assertTrue(f.toggleFrame(null)==null,"Assembly failed");
            var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(6)).getFirst();
            p.setPos(w.pose().point(new Vec3(-.5,.25,3.2)));
            var entry=w.cargo().entry(8);h.assertTrue(w.cargo().valid(entry,p),"Covered entity cargo blocked unobstructed rear ray");
            Vec3 target=w.pose().point(CargoHold.centre(8).add(0,.3,0));
            var hit=w.pick(p.getEyePosition(),target);
            h.assertTrue(hit.isPresent()&&w.pose().local(hit.get()).y<CargoCover.Y,"Entity ray picked fabric instead of rear cargo");
            p.setShiftKeyDown(true);w.cargo().interact(p,InteractionHand.MAIN_HAND,w.pose().local(hit.get()));
            h.assertTrue(w.cargo().entry(8)==null,"Entity rear unload failed");
            var stack=new ItemStack(Items.STONE,2);h.assertTrue(w.cargo().place(8,stack,p)==null&&stack.getCount()==1,"Entity rear placement failed");
            p.setPos(w.pose().point(new Vec3(-.5,2.9,CargoHold.centre(8).z)));
            h.assertTrue(!w.cargo().valid(w.cargo().entry(8),p)&&w.cargo().take(8,p)!=null,"Entity cargo usable through cloth from above");
            h.assertTrue(w.cargo().place(9,stack,p)!=null&&stack.getCount()==1,"Blocked entity placement consumed cargo");h.succeed();
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void actual_proxy_block_clicks_install_roll_spread_and_remove_cover(GameTestHelper h) {
        var f=frame(h,false);var p=player(h,f.cargo());p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(WagonContent.CARGO_COVER.get(),2));
        click(h,f,p,SIDE,Direction.WEST);h.assertTrue(f.cargo().cover().installed()&&p.getMainHandItem().getCount()==1,"Proxy side install bypassed accessory");
        click(h,f,p,new Vec3(0,CargoCover.TOP,0),Direction.UP);h.assertTrue(f.cargo().cover().openRows()==1,"Fabric click did not roll a row");
        p.setShiftKeyDown(true);click(h,f,p,cloth(f.cargo().cover(),f.cargoBody()),Direction.UP);h.assertTrue(f.cargo().cover().openRows()==0,"Sneak fabric click did not spread");
        click(h,f,p,new Vec3(0,CargoCover.Y-.06,f.cargo().cover().back(f.cargoBody())),Direction.SOUTH);h.assertTrue(f.cargo().cover().installed()&&f.cargo().cover().openRows()==0,"Sneak hanging cloth click dismantled the cover instead of spreading");
        int before=carried(p);click(h,f,p,SIDE,Direction.WEST);h.assertTrue(!f.cargo().cover().installed()&&carried(p)==before+1,"Sneak wall click did not remove cover");h.succeed();
    }
    private static void click(GameTestHelper h,AssemblyFrameBlockEntity f,Player p,Vec3 local,Direction face) {
        Vec3 world=f.cargoPose().point(local);BlockPos pos=BlockPos.containing(world.add(f.cargoPose().vector(new Vec3(0,-.001,0))));
        var e=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,pos,new net.minecraft.world.phys.BlockHitResult(world,face,pos,false));
        CargoInteractions.block(e);h.assertTrue(e.isCanceled()&&e.getCancellationResult().consumesAction(),"Cover interaction was not handled before world placement");
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void block_cover_is_walkable_and_never_auto_seats_a_mob_above_a_stool(GameTestHelper h) {
        var f=frame(h,false);var hold=f.cargo();var p=player(h,hold);h.assertTrue(hold.place(4,new ItemStack(WagonContent.STOOL.get()),p)==null,"Stool placement failed");install(h,hold,p);
        var sheep=EntityType.SHEEP.create(h.getLevel());sheep.setNoAi(true);sheep.setPos(f.cargoPose().point(new Vec3(0,CargoCover.TOP+.01,0)));h.getLevel().addFreshEntity(sheep);sheep.move(MoverType.SELF,new Vec3(0,-.1,0));
        h.assertTrue(sheep.onGround()&&Math.abs(f.cargoPose().local(sheep.position()).y-CargoCover.TOP)<.002,"Block cover does not support walking");
        sheep.setDeltaMovement(Vec3.ZERO);hold.seats.tick();h.assertTrue(!sheep.isPassenger(),"Walking on cover captured mob into covered stool");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=55)
    public static void covered_stools_reject_manual_direct_and_automatic_boarding_in_both_forms(GameTestHelper h) {
        var f=frame(h,false);var hold=f.cargo();var p=player(h,hold);
        h.assertTrue(hold.place(8,new ItemStack(WagonContent.STOOL.get()),p)==null,"Stool placement failed");install(h,hold,p);
        h.assertTrue(hold.seats.sit(8,p)!=null&&!p.isPassenger(),"Covered block stool allowed manual seating");
        var anchor=WagonContent.CARGO_SEAT.get().create(h.getLevel());anchor.initialize(hold,8,hold.entry(8).id);h.getLevel().addFreshEntity(anchor);
        h.assertTrue(!p.startRiding(anchor),"Direct boarding bypassed covered block stool restriction");anchor.discard();
        var chicken=EntityType.CHICKEN.create(h.getLevel());chicken.setAge(-24000);chicken.setNoAi(true);chicken.setNoGravity(true);
        chicken.setPos(f.cargoPose().point(CargoHold.centre(8).add(0,.5,0)));h.getLevel().addFreshEntity(chicken);
        boolean fits=hold.seats.available(8,chicken);
        h.assertTrue(hold.toggleGate()==null,"Tailgate failed");
        h.runAtTickTime(22,()->{
            p.setPos(f.cargoPose().point(new Vec3(-.5,.25,3.2)));
            h.assertTrue(hold.valid(hold.entry(8),p)&&hold.seats.sit(8,p)!=null,"Visible covered stool became usable from the rear");
            h.assertTrue(chicken.isPassenger()==fits,"Auto seating ignored actual clearance");chicken.discard();
            h.assertTrue(f.toggleFrame(null)==null,"Assembly failed");
            var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(6)).getFirst();
            h.assertTrue(w.cargo().seats.sit(8,p)!=null&&!w.boardCargoSeat(p,8)&&!p.isPassenger(),"Entity stool bypassed cover restriction");
            for(int row=0;row<5;row++)step(h,w.cargo(),p,1);
            h.assertTrue(w.cargo().seats.sit(8,p)==null&&p.getVehicle()==w,"Uncovered stool remained disabled");h.succeed();
        });
    }
    @GameTest(template="assembly_test",batch="covered_mat_sleep",timeoutTicks=55)
    public static void covered_mat_sleep_survives_spreading_and_wakes_above_cloth_in_both_forms(GameTestHelper h) {
        var f=frame(h,false);var hold=f.cargo();var loader=player(h,hold);
        h.assertTrue(hold.place(8,new ItemStack(WagonContent.STRAW_MAT.get()),loader)==null,"Mat placement failed");install(h,hold,loader);
        h.assertTrue(hold.toggleGate()==null,"Tailgate failed");
        var p=h.makeMockServerPlayerInLevel();p.setNoGravity(true);p.getAbilities().instabuild=false;
        h.runAtTickTime(22,()->{
            long oldTime=h.getLevel().getDayTime();h.setNight();
            try {
                Vec3 rear=f.cargoPose().point(new Vec3(-.5,.25,3.2));p.teleportTo(rear.x,rear.y,rear.z);
                h.assertTrue(StrawMatSleep.sleep(hold,hold.entry(8),p)==null&&p.isSleeping(),"Covered mat refused sleep through the open rear");
                step(h,hold,loader,1);step(h,hold,loader,-1);
                h.assertTrue(p.isSleeping()&&hold.cover().openRows()==0,"Spreading cloth woke a covered mat sleeper");
                p.stopSleepInBed(true,true);
                h.assertTrue(p.getPose()==net.minecraft.world.entity.Pose.STANDING&&!p.isSleeping()&&StrawMatSleep.sleepingPoint(p)==null,"Covered wake retained sleep/camera state");
                h.assertTrue(Math.abs(f.cargoPose().local(p.position()).y-CargoCover.TOP)<.02&&h.getLevel().noCollision(p,p.getBoundingBox().deflate(.001)),"Block mat wake did not stand safely above cloth");
                Vec3 outside=f.cargoPose().point(new Vec3(-3,3,0));p.teleportTo(outside.x,outside.y,outside.z);
                h.assertTrue(f.toggleFrame(null)==null,"Assembly failed");
                var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(6)).getFirst();
                w.applyPose(new com.sange.tm_wagon.physics.WagonPose(w.position(),213,.18F,.12F));w.setDeltaMovement(Vec3.ZERO);
                rear=w.pose().point(new Vec3(-.5,.25,3.2));p.teleportTo(rear.x,rear.y,rear.z);
                h.assertTrue(StrawMatSleep.sleep(w.cargo(),w.cargo().entry(8),p)==null,"Covered tilted entity mat refused sleep");p.stopSleepInBed(true,true);
                h.assertTrue(w.pose().local(p.position()).y>=CargoCover.TOP&&h.getLevel().noCollision(p,p.getBoundingBox().deflate(.001)),"Tilted wake intersected covered wagon");
                p.setYRot(81);p.setXRot(17);h.assertTrue(p.getPose()==net.minecraft.world.entity.Pose.STANDING&&StrawMatSleep.sleepingPoint(p)==null&&p.getYRot()==81&&p.getXRot()==17,"Tilted covered wake retained locked camera");
                h.succeed();
            } finally { h.getLevel().setDayTime(oldTime); }
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void obstructed_install_and_spread_do_not_consume_or_change_state(GameTestHelper h) {
        var f=frame(h,false);var hold=f.cargo();var p=player(h,hold);var sheep=EntityType.SHEEP.create(h.getLevel());sheep.setNoAi(true);sheep.setNoGravity(true);sheep.setPos(f.cargoPose().point(new Vec3(0,2.2,-.9)));h.getLevel().addFreshEntity(sheep);
        var stack=new ItemStack(WagonContent.CARGO_COVER.get(),2);h.assertTrue(hold.cover().install(stack,p,SIDE)!=null&&!hold.cover().installed()&&stack.getCount()==2,"Obstructed install consumed item or committed");
        sheep.discard();install(h,hold,p);step(h,hold,p,1);
        var pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);pig.setNoGravity(true);pig.setPos(f.cargoPose().point(new Vec3(0,2.2,-.9)));h.getLevel().addFreshEntity(pig);
        h.assertTrue(hold.cover().step(p,cloth(hold.cover(),f.cargoBody()),-1)!=null&&hold.cover().openRows()==1,"Spread embedded entity or changed state");
        pig.discard();step(h,hold,p,-1);h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void save_transfer_and_destruction_have_one_authoritative_cover(GameTestHelper h) {
        var f=frame(h,true);var hold=f.cargo();var p=player(h,hold);install(h,hold,p);for(int i=0;i<6;i++)step(h,hold,p,1);
        var tag=hold.save(h.getLevel().registryAccess(),false);var copy=new CargoHold(f);copy.load(tag,h.getLevel().registryAccess());
        h.assertTrue(copy.cover().installed()&&copy.cover().openRows()==6&&!copy.empty(),"Cover persistence lost extended row state");
        var target=new CargoHold(f);hold.transferTo(target);h.assertTrue(hold.empty()&&!hold.cover().installed()&&target.cover().openRows()==6,"Transfer left two owners or reset cover");
        h.assertTrue(hold.cover().remove(p,SIDE)!=null,"Transferred source returned a second cover");
        target.destroy(true);target.destroy(true);h.assertTrue(drops(h,f.cargoPose().position())==1,"Destruction duplicated or lost optional cover");
        copy.destroy(false);h.assertTrue(copy.empty()&&drops(h,f.cargoPose().position())==1,"No-drop destruction leaked cover");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=85)
    public static void covered_extended_wagon_round_trip_preserves_roll_and_chest_inventory(GameTestHelper h) {
        var f=frame(h,true);var hold=f.cargo();var p=player(h,hold);h.assertTrue(hold.place(11,new ItemStack(Items.CHEST),p)==null,"Rear chest placement failed");var chest=hold.entry(11);chest.inventory.setItem(0,new ItemStack(Items.EMERALD,31));install(h,hold,p);step(h,hold,p,1);step(h,hold,p,1);
        h.assertTrue(f.toggleFrame(null)==null,"Assembly with optional cover failed");var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(6)).getFirst();
        h.assertTrue(w.cargo().cover().installed()&&w.cargo().cover().openRows()==2&&hold.empty()&&w.cargo().entry(11)==chest,"Entity conversion reset or cloned accessory/cargo");
        h.assertTrue(w.lock(f.getBlockPos()),"Could not lock accessory during conversion");
        h.assertTrue(w.cargo().cover().remove(p,SIDE)!=null,"Accessory removed during conversion lock");w.unlock();
        var animal=EntityType.SHEEP.create(h.getLevel());animal.setNoAi(true);animal.setNoGravity(true);animal.setPos(w.pose().point(new Vec3(0,CargoCover.TOP+.01,1.5)));h.getLevel().addFreshEntity(animal);animal.move(MoverType.SELF,new Vec3(0,-.1,0));
        h.assertTrue(animal.onGround()&&Math.abs(w.pose().local(animal.position()).y-CargoCover.TOP)<.002,"Entity cover is not walkable");animal.discard();
        // Rolling and spreading remain available in entity form.
        step(h,w.cargo(),p,1);step(h,w.cargo(),p,-1);
        h.runAtTickTime(24,()->h.assertTrue(f.toggleFrame(null)==null,"Restoration failed to start"));
        h.runAtTickTime(49,()->{
            h.assertTrue(w.isRemoved()&&w.cargo().empty()&&f.cargo().cover().installed()&&f.cargo().cover().openRows()==2&&f.cargo().entry(11)==chest&&chest.inventory.getItem(0).getCount()==31,"Round trip changed cover/cargo ownership");
            h.assertTrue(f.cargo().cover().remove(p,SIDE)==null&&carried(p)==1&&drops(h,f.cargoPose().position())==0,"Round trip duplicated/lost cover");h.succeed();
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=110)
    public static void blocked_cover_restoration_retains_source_without_drops(GameTestHelper h) {
        var f=frame(h,false);var p=player(h,f.cargo());install(h,f.cargo(),p);h.assertTrue(f.toggleFrame(null)==null,"Assembly failed");var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(6)).getFirst();
        var obstruction=f.getBlockPos().offset(0,2,0);
        h.runAtTickTime(24,()->{h.assertTrue(f.toggleFrame(null)==null,"Restoration start failed");h.getLevel().setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);});
        h.runAtTickTime(74,()->{
            h.assertTrue(!w.isRemoved()&&w.cargo().cover().installed()&&f.cargo().empty()&&drops(h,w.position())==0,"Blocked restore moved/cloned/dropped cover");
            h.getLevel().setBlock(obstruction,Blocks.AIR.defaultBlockState(),3);h.assertTrue(f.toggleFrame(null)==null,"Restoration retry failed");
        });
        h.runAtTickTime(99,()->{h.assertTrue(w.isRemoved()&&f.cargo().cover().installed()&&w.cargo().empty(),"Retry lost cover");h.succeed();});
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void roll_and_spread_sounds_play_once_only_on_committed_steps(GameTestHelper h) {
        var f=frame(h,false);var hold=f.cargo();var p=player(h,hold);install(h,hold,p);var sounds=new java.util.ArrayList<net.minecraft.sounds.SoundEvent>();
        java.util.function.Consumer<net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition> listener=e->{if(e.getLevel()==h.getLevel()&&e.getPosition().distanceToSqr(f.cargoPose().position())<36&&e.getSound()!=null)sounds.add(e.getSound().value());};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        try {
            step(h,hold,p,-1);h.assertTrue(sounds.isEmpty(),"Limit click played a sound");step(h,hold,p,1);step(h,hold,p,-1);
            h.assertTrue(sounds.size()==2&&sounds.get(0)==net.minecraft.world.level.block.SoundType.WOOL.getBreakSound()&&sounds.get(1)==net.minecraft.world.level.block.SoundType.WOOL.getPlaceSound(),"Wrong or duplicate fabric sounds");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener); }h.succeed();
    }
}
