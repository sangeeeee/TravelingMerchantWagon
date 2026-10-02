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
        var pos=h.absolutePos(new BlockPos(11,2,17));h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState().setValue(AssemblyFrameBlock.FACING,Direction.NORTH),3);
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
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void covered_cargo_is_unusable_and_open_rows_allow_place_take_and_menus(GameTestHelper h) {
        var f=frame(h,false);var hold=f.cargo();var p=h.makeMockServerPlayerInLevel();p.getAbilities().instabuild=false;p.setPos(f.cargoPose().point(new Vec3(-3,0,0)));
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
        h.assertTrue(w.cargo().cover().remove(p,SIDE)!=null,"Entity cover was removable");
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
