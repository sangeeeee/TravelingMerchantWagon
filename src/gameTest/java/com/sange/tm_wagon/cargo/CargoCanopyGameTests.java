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
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
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
public class CargoCanopyGameTests {
    private static final Vec3 SIDE=new Vec3(-1.15625,1.9,.5);
    private static AssemblyFrameBlockEntity frame(GameTestHelper h,boolean extended) {
        var pos=h.absolutePos(new BlockPos(11,2,17));h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState().setValue(AssemblyFrameBlock.FACING,Direction.NORTH),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(f.initializeFrame()==null,"Frame failed");
        var parts=WagonEntity.defaultParts();if(extended)parts.put(WagonSlot.BODY,WagonPart.LONG_CARGO_BODY);
        for(var part:parts.entrySet())h.assertTrue(f.install(part.getKey(),part.getValue(),null,new ItemStack(WagonContent.PART_ITEMS.get(part.getValue()).get()))==null,"Module failed");return f;
    }
    private static Player player(GameTestHelper h,CargoHold hold) { var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(hold.owner().cargoPose().point(new Vec3(-3,0,0)));return p; }
    private static void install(GameTestHelper h,CargoHold hold,Player p) { h.assertTrue(hold.canopy().install(new ItemStack(WagonContent.CANOPY.get()),p,SIDE)==null,"Canopy install failed"); }
    private static void toggle(GameTestHelper h,CargoHold hold,Player p,boolean front) {
        double x=hold.canopy().closed(front)?0:.82;
        h.assertTrue(hold.canopy().toggle(p,new Vec3(x,2.8,hold.canopy().curtainZ(hold.owner().cargoBody(),front)),front)==null,"Curtain toggle failed");
    }
    private static int carried(Player p) { return p.getInventory().items.stream().filter(s->s.is(WagonContent.CANOPY.get())).mapToInt(ItemStack::getCount).sum(); }
    private static int drops(GameTestHelper h,Vec3 p) { return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p,p).inflate(6),e->e.getItem().is(WagonContent.CANOPY.get())).stream().mapToInt(e->e.getItem().getCount()).sum(); }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void roof_accessories_are_exclusive_and_install_remove_once(GameTestHelper h) {
        var f=frame(h,false);var hold=f.cargo();var p=player(h,hold);var stack=new ItemStack(WagonContent.CANOPY.get(),2);
        h.assertTrue(!(stack.getItem() instanceof net.minecraft.world.item.BlockItem)&&!CargoHold.allowed(stack),"Canopy is a world block or cargo entry");
        h.assertTrue(hold.cover().install(new ItemStack(WagonContent.CARGO_COVER.get()),p,SIDE)==null,"Cover setup failed");
        h.assertTrue(hold.canopy().install(stack,p,SIDE)!=null&&stack.getCount()==2&&!hold.canopy().installed(),"Canopy consumed item over an installed cover");
        h.assertTrue(hold.cover().remove(p,SIDE)==null&&hold.canopy().install(stack,p,SIDE)==null&&stack.getCount()==1,"Exclusive swap failed");
        h.assertTrue(hold.cover().install(new ItemStack(WagonContent.CARGO_COVER.get()),p,SIDE)!=null&&!hold.cover().installed(),"Cover installed over canopy");
        h.assertTrue(hold.canopy().install(stack,p,SIDE)!=null&&stack.getCount()==1,"Repeated canopy install consumed item");
        h.assertTrue(hold.canopy().remove(p,SIDE)==null&&carried(p)==1&&!hold.canopy().installed(),"Canopy removal did not return one item");
        h.assertTrue(hold.canopy().remove(p,SIDE)!=null&&carried(p)==1,"Repeated removal duplicated item");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=55)
    public static void standard_canopy_has_clear_interior_and_native_independent_curtains(GameTestHelper h) { curtains(h,false); }
    @GameTest(template="assembly_test",timeoutTicks=55)
    public static void extended_canopy_has_clear_interior_and_native_independent_curtains(GameTestHelper h) { curtains(h,true); }
    private static void curtains(GameTestHelper h,boolean extended) {
        var f=frame(h,extended);var hold=f.cargo();var p=h.makeMockServerPlayerInLevel();p.setNoGravity(true);p.getAbilities().instabuild=false;p.setPos(f.cargoPose().point(new Vec3(-3,0,0)));
        int slot=hold.capacity()-2;h.assertTrue(hold.place(slot,new ItemStack(Items.CHEST),p)==null,"Rear chest failed");var chest=hold.entry(slot);chest.inventory.setItem(0,new ItemStack(Items.DIAMOND,19));install(h,hold,p);
        AABB inside=p.getDimensions(Pose.STANDING).makeBoundingBox(f.cargoPose().point(new Vec3(0,1.51,0)));
        h.assertTrue(h.getLevel().noCollision(p,inside),"Player cannot stand below the canopy");
        var sheep=EntityType.SHEEP.create(h.getLevel());sheep.setNoAi(true);sheep.setNoGravity(true);sheep.setPos(f.cargoPose().point(new Vec3(0,2.4,0)));h.getLevel().addFreshEntity(sheep);sheep.move(MoverType.SELF,new Vec3(2,0,0));
        h.assertTrue(f.cargoPose().local(sheep.position()).x<1.05,"Entity walked through the canopy side");sheep.discard();
        h.assertTrue(hold.toggleGate()==null,"Tailgate failed");
        h.runAtTickTime(22,()->{
            p.setPos(f.cargoPose().point(new Vec3(-.5,.25,hold.canopy().back(f.cargoBody())+1)));
            h.assertTrue(hold.valid(chest,p),"Open canopy exit blocked rear cargo");CargoMenus.open(hold,chest,p);h.assertTrue(p.containerMenu!=p.inventoryMenu,"Rear chest did not open");
            nativeCurtainClick(h,f,p,false,false);
            h.assertTrue(hold.canopy().closed(false)&&!hold.canopy().closed(true)&&!hold.valid(chest,p)&&p.containerMenu==p.inventoryMenu,"Rear curtain was not independent or left cargo usable: front="+hold.canopy().closed(true)+", rear="+hold.canopy().closed(false)+", valid="+hold.valid(chest,p)+", closedMenu="+(p.containerMenu==p.inventoryMenu));
            nativeCurtainClick(h,f,p,false,true);h.assertTrue(!hold.canopy().closed(false)&&hold.valid(chest,p),"Reopened curtain left cargo inaccessible");
            p.setPos(f.cargoPose().point(new Vec3(0,1, CargoCanopy.FRONT-1)));
            nativeCurtainClick(h,f,p,true,false);h.assertTrue(hold.canopy().closed(true)&&!hold.canopy().closed(false),"Front curtain changed rear state");
            h.assertTrue(chest.inventory.getItem(0).getCount()==19,"Curtains lost cargo contents");h.succeed();
        });
    }
    private static void nativeCurtainClick(GameTestHelper h,AssemblyFrameBlockEntity f,Player p,boolean front,boolean closed) {
        double x=closed?0:.82;var target=f.cargoPose().point(new Vec3(x,2.8,f.cargo().canopy().curtainZ(f.cargoBody(),front)+CargoCanopy.THICK/2));var start=p.getEyePosition();
        var hit=h.getLevel().clip(new net.minecraft.world.level.ClipContext(start,target.add(target.subtract(start).normalize().scale(.08)),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        h.assertTrue(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK,"Native curtain ray missed");
        var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,hit.getBlockPos(),hit);CargoInteractions.block(event);
        h.assertTrue(event.isCanceled(),"Native curtain click unhandled: "+f.cargoPose().local(hit.getLocation()));
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void obstructed_roof_and_curtain_roll_back_without_consuming_or_embedding(GameTestHelper h) {
        var f=frame(h,false);var hold=f.cargo();var p=player(h,hold);var stack=new ItemStack(WagonContent.CANOPY.get(),2);
        BlockPos ceiling=BlockPos.containing(f.cargoPose().point(new Vec3(0,CargoCanopy.TOP,0)));h.getLevel().setBlock(ceiling,Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(hold.canopy().install(stack,p,SIDE)!=null&&stack.getCount()==2&&!hold.canopy().installed(),"Blocked roof consumed canopy");h.getLevel().setBlock(ceiling,Blocks.AIR.defaultBlockState(),3);install(h,hold,p);
        var pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);pig.setNoGravity(true);pig.setPos(f.cargoPose().point(new Vec3(0,1.6,hold.canopy().curtainZ(f.cargoBody(),false))));h.getLevel().addFreshEntity(pig);
        String error=hold.canopy().toggle(p,new Vec3(.82,2.8,hold.canopy().curtainZ(f.cargoBody(),false)),false);
        h.assertTrue(error!=null&&!hold.canopy().closed(false)&&!pig.isRemoved(),"Closing curtain embedded entity or committed state");pig.discard();toggle(h,hold,p,false);h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=95)
    public static void extended_canopy_round_trip_preserves_curtains_stool_and_inventory_once(GameTestHelper h) {
        var f=frame(h,true);var hold=f.cargo();var p=player(h,hold);h.assertTrue(hold.place(8,new ItemStack(WagonContent.STOOL.get()),p)==null&&hold.place(11,new ItemStack(Items.BARREL),p)==null,"Cargo setup failed");var barrel=hold.entry(11);barrel.inventory.setItem(0,new ItemStack(Items.EMERALD,29));install(h,hold,p);toggle(h,hold,p,true);
        h.assertTrue(hold.seats.sit(8,p)==null&&p.isPassenger(),"Canopy disabled block stool");p.stopRiding();
        h.assertTrue(f.toggleFrame(null)==null,"Assembly failed");var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(7)).getFirst();
        h.assertTrue(hold.empty()&&w.cargo().canopy().installed()&&w.cargo().canopy().closed(true)&&!w.cargo().canopy().closed(false),"Assembly cloned/lost canopy states");
        h.assertTrue(w.lock(f.getBlockPos()),"Could not lock accessory during conversion");
        h.assertTrue(w.cargo().canopy().remove(p,SIDE)!=null,"Accessory removed during conversion lock");w.unlock();
        h.assertTrue(w.cargo().seats.sit(8,p)==null,"Canopy disabled moving stool");p.stopRiding();
        h.runAtTickTime(24,()->h.assertTrue(f.toggleFrame(null)==null,"Restore failed to start"));
        h.runAtTickTime(49,()->{
            h.assertTrue(w.isRemoved()&&w.cargo().empty()&&f.cargo().canopy().closed(true)&&!f.cargo().canopy().closed(false)&&f.cargo().entry(11)==barrel&&barrel.inventory.getItem(0).getCount()==29,"Round trip lost or copied cargo/canopy");
            h.assertTrue(f.cargo().canopy().remove(p,SIDE)==null&&carried(p)==1&&drops(h,f.cargoPose().position())==0,"Round trip returned wrong canopy count");h.succeed();
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void canopy_load_and_destroy_have_one_owner_and_reject_conflicting_data(GameTestHelper h) {
        var f=frame(h,true);var hold=f.cargo();var p=player(h,hold);install(h,hold,p);toggle(h,hold,p,true);toggle(h,hold,p,false);
        var tag=hold.save(h.getLevel().registryAccess(),false);var copy=new CargoHold(f);copy.load(tag,h.getLevel().registryAccess());
        h.assertTrue(copy.canopy().installed()&&copy.canopy().closed(true)&&copy.canopy().closed(false),"Save/load lost canopy state");
        var bad=tag.copy();var cover=new CompoundTag();cover.putBoolean("Installed",true);bad.put("Cover",cover);copy.load(bad,h.getLevel().registryAccess());h.assertTrue(copy.cover().installed()&&!copy.canopy().installed(),"Conflicting save created two roofs");
        hold.destroy(true);hold.destroy(true);h.assertTrue(drops(h,f.cargoPose().position())==1,"Canopy destruction duplicated/missed item");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=60)
    public static void decorative_canopy_rim_is_clickable_but_does_not_trap_standing_players(GameTestHelper h) {
        var f=frame(h,false);var hold=f.cargo();var loader=player(h,hold);install(h,hold,loader);h.assertTrue(hold.toggleGate()==null,"Tailgate setup failed");
        h.runAtTickTime(22,()->{
            double rear=hold.canopy().back(f.cargoBody());
            var p=h.makeMockServerPlayerInLevel();p.setNoGravity(true);p.setPos(f.cargoPose().point(new Vec3(0,2.05,rear-.3)));
            Vec3 rayStart=f.cargoPose().point(new Vec3(0,CargoCanopy.TOP-.25,rear+.3)),rayEnd=f.cargoPose().point(new Vec3(0,CargoCanopy.TOP-.25,rear-.3));
            var outline=h.getLevel().clip(new net.minecraft.world.level.ClipContext(rayStart,rayEnd,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            var collider=h.getLevel().clip(new net.minecraft.world.level.ClipContext(rayStart,rayEnd,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            h.assertTrue(outline.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&collider.getType()==net.minecraft.world.phys.HitResult.Type.MISS,"Block canopy rim lost selection or retained a solid collider");
            Vec3 before=p.position();p.move(MoverType.SELF,new Vec3(0,0,.65));h.assertTrue(p.getZ()>before.z+.6,"Block canopy arch trapped the player's head");
            p.setPos(f.cargoPose().point(new Vec3(-3,0,0)));h.assertTrue(f.toggleFrame(null)==null,"Entity conversion failed");
            var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(7)).getFirst();
            h.assertTrue(w.pick(w.pose().point(new Vec3(0,CargoCanopy.TOP-.25,rear+.3)),w.pose().point(new Vec3(0,CargoCanopy.TOP-.25,rear-.3))).isPresent(),"Entity decorative rim cannot be selected");
            p.setPos(w.pose().point(new Vec3(0,2.05,rear-.3)));before=p.position();p.move(MoverType.SELF,new Vec3(0,0,.65));
            h.assertTrue(p.getZ()>before.z+.6,"Entity canopy arch trapped the player's head");h.succeed();
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void driving_canopy_stops_at_low_ceiling_but_moves_when_ceiling_removed(GameTestHelper h) {
        for(int x=1;x<24;x++)for(int z=1;z<24;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var w=WagonContent.WAGON.get().create(h.getLevel());w.configure(WagonEntity.defaultParts(),Direction.NORTH);w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(w);
        var tag=new CompoundTag();var canopy=new CompoundTag();canopy.putBoolean("Installed",true);tag.put("Canopy",canopy);w.cargo().load(tag,h.getLevel().registryAccess());w.cargoGeometryChanged();
        var p=player(h,w.cargo());h.assertTrue(p.startRiding(w),"Driver failed");var horse=EntityType.HORSE.create(h.getLevel());horse.setNoAi(true);horse.setPos(w.horsePosition(0));h.getLevel().addFreshEntity(horse);horse.setLeashedTo(p,true);h.assertTrue(w.attachHorse(p,horse,0)==null,"Horse attach failed");
        for(int x=9;x<=14;x++)for(int z=10;z<=15;z++)h.setBlock(new BlockPos(x,6,z),Blocks.STONE);
        double start=w.getZ();for(int i=0;i<12;i++) { w.acceptInput(p,1,0);w.tick(); }
        h.assertTrue(w.getZ()>=start-.25,"Canopy passed through low ceiling: "+(start-w.getZ()));
        for(int x=9;x<=14;x++)for(int z=10;z<=15;z++)h.setBlock(new BlockPos(x,6,z),Blocks.AIR);
        for(int i=0;i<20;i++) { w.acceptInput(p,1,0);w.tick(); }h.assertTrue(w.getZ()<start-1,"Canopy remained stuck after ceiling removed");h.succeed();
    }
    @GameTest(template="assembly_test",batch="canopy_mat_sleep",timeoutTicks=35)
    public static void canopy_mat_sleep_wakes_inside_with_normal_standing_clearance(GameTestHelper h) {
        var f=frame(h,false);var hold=f.cargo();var loader=player(h,hold);h.assertTrue(hold.place(8,new ItemStack(WagonContent.STRAW_MAT.get()),loader)==null,"Mat failed");install(h,hold,loader);
        var p=h.makeMockServerPlayerInLevel();p.setNoGravity(true);Vec3 point=f.cargoPose().point(new Vec3(-.5,1.626,1.14));p.teleportTo(point.x,point.y,point.z);
        long time=h.getLevel().getDayTime();h.setNight();
        h.runAfterDelay(2,()->{
        try {
            String error=StrawMatSleep.sleep(hold,hold.entry(8),p);h.assertTrue(error==null&&p.isSleeping(),"Canopy mat sleep failed: "+error);p.stopSleepInBed(true,true);
            h.assertTrue(!p.isSleeping()&&StrawMatSleep.sleepingPoint(p)==null&&p.getPose()==Pose.STANDING&&f.cargoPose().local(p.position()).y<CargoCanopy.BASE&&h.getLevel().noCollision(p,p.getBoundingBox().deflate(.001)),"Canopy wake pushed player onto roof or trapped body");h.succeed();
        }finally { h.getLevel().setDayTime(time); }
        });
    }
}
