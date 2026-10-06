package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.WagonEntity;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class WagonCabinetGameTests {
    private static AssemblyFrameBlockEntity frame(GameTestHelper h,boolean dual) {
        var pos=h.absolutePos(new BlockPos(11,2,17));
        h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState().setValue(AssemblyFrameBlock.FACING,Direction.NORTH),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(pos);
        h.assertTrue(f.initializeFrame()==null,"Frame failed");var parts=WagonEntity.defaultParts();
        if(dual) { parts.put(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);parts.put(WagonSlot.BODY,WagonPart.LONG_CARGO_BODY); }
        for(var e:parts.entrySet())h.assertTrue(f.install(e.getKey(),e.getValue(),null,new ItemStack(WagonContent.PART_ITEMS.get(e.getValue()).get()))==null,"Part failed");
        return f;
    }
    private static Vec3 end(boolean dual,int side) { return new Vec3((side==0?-1:1)*(dual?15.5:8.5)/16,1.7,-1.8); }
    private static ServerPlayer player(GameTestHelper h,CargoHold hold) {
        var p=h.makeMockServerPlayerInLevel();p.setNoGravity(true);p.getAbilities().instabuild=false;
        p.setPos(hold.owner().cargoPose().point(new Vec3(-3,0,-1.8)));return p;
    }
    private static WagonCabinet install(GameTestHelper h,AssemblyFrameBlockEntity f,ServerPlayer p,boolean dual) {
        var c=f.cargo().cabinet();var stack=new ItemStack(WagonContent.CABINET.get(),2);
        h.assertTrue(c.install(stack,p,end(dual,0))==null&&stack.getCount()==1,"Installation failed or wrong consumption");return c;
    }
    private static int dropped(GameTestHelper h,CargoHold hold,Item item) {
        Vec3 p=hold.owner().cargoPose().position();return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p,p).inflate(7),e->e.getItem().is(item)).stream().mapToInt(e->e.getItem().getCount()).sum();
    }
    private static int carried(ServerPlayer p,Item item) { return p.getInventory().items.stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum(); }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void cabinet_adapts_to_seat_and_block_interaction_cancels_held_item(GameTestHelper h) {
        var f=frame(h,false);var p=player(h,f.cargo());var c=f.cargo().cabinet();
        h.assertTrue(!CargoHold.allowed(new ItemStack(WagonContent.CABINET.get()))&&!(new ItemStack(WagonContent.CABINET.get()).getItem() instanceof net.minecraft.world.item.BlockItem),"Cabinet became cargo or world block");
        var stack=new ItemStack(WagonContent.CABINET.get(),2);h.assertTrue(c.install(stack,p,new Vec3(0,2.3,0))!=null&&stack.getCount()==2,"Bad mount consumed cabinet");
        p.setItemInHand(InteractionHand.MAIN_HAND,stack);Vec3 hit=f.cargoPose().point(end(false,0));var pos=BlockPos.containing(hit);
        var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,pos,new net.minecraft.world.phys.BlockHitResult(hit,Direction.WEST,pos,false));
        CargoInteractions.block(event);
        h.assertTrue(event.isCanceled()&&c.installed()&&c.rows()==3&&c.inventory().getContainerSize()==27&&stack.getCount()==1,"Block interaction did not install exactly once");
        var seatBox=WagonGeometry.partBoxes(WagonPart.SINGLE_SEAT).getFirst();
        h.assertTrue(c.boxes().size()==1&&seatBox.inflate(.000001).contains(c.box().getCenter())&&seatBox.minX<=c.box().minX&&seatBox.maxX>=c.box().maxX,"Cabinet not enclosed by the existing rectangular seat collision");
        h.assertTrue(c.install(stack,p,end(false,0))!=null&&stack.getCount()==1,"Repeated install consumed item");
        c.inventory().setItem(0,new ItemStack(Items.DIAMOND,7));p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE));p.setShiftKeyDown(true);
        h.assertTrue(f.cargo().interact(p,InteractionHand.MAIN_HAND,end(false,0)).consumesAction()&&!c.installed(),"Held block bypassed cabinet removal");
        h.assertTrue(carried(p,WagonContent.CABINET.get())==1&&dropped(h,f.cargo(),Items.DIAMOND)==7,"Removal did not return empty cabinet and contents once");
        h.assertTrue(c.remove(p,end(false,0))!=null&&carried(p,WagonContent.CABINET.get())==1,"Repeated removal duplicated cabinet");
        f.remove(Set.of(WagonSlot.SEAT),false);h.assertTrue(c.install(stack,p,end(false,0))!=null&&stack.getCount()==1,"Missing seat accepted cabinet");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void native_shared_drawers_small_chest(GameTestHelper h) { menus(h,false); }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void native_shared_drawers_large_chest(GameTestHelper h) { menus(h,true); }
    private static void menus(GameTestHelper h,boolean dual) {
        var f=frame(h,dual);var p=player(h,f.cargo());var other=player(h,f.cargo());var sameSide=player(h,f.cargo());var c=install(h,f,p,dual);
        c.inventory().setItem(0,new ItemStack(Items.DIAMOND,9));
        h.assertTrue(c.open(p,new Vec3(0,WagonCabinet.TOP,-1.8))!=null&&p.containerMenu==p.inventoryMenu,"Top face incorrectly opened drawer");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE,2));
        h.assertTrue(f.cargo().interact(p,InteractionHand.MAIN_HAND,end(dual,0)).consumesAction()&&p.containerMenu instanceof ChestMenu,"Held item prevented GUI");
        h.assertTrue(((ChestMenu)p.containerMenu).getRowCount()==(dual?6:3)&&c.open(other,end(dual,1))==null&&c.open(sameSide,end(dual,0))==null,"Chest size or second viewer failed");
        h.assertTrue(p.containerMenu.slots.getFirst().container==other.containerMenu.slots.getFirst().container&&c.opened(0)&&c.opened(1),"Drawer inventories were copied or not independently opened");
        var old=p.containerMenu;p.containerMenu.clicked(0,0,ClickType.QUICK_MOVE,p);
        h.assertTrue(carried(p,Items.DIAMOND)==9&&c.inventory().getItem(0).isEmpty()&&other.containerMenu.slots.getFirst().getItem().isEmpty(),"Shift-click duplicated/lost shared contents");
        p.closeContainer();h.assertTrue(c.opened(0)&&c.opened(1),"One viewer closing shut another viewer's drawer");
        sameSide.closeContainer();h.assertTrue(!c.opened(0)&&c.opened(1),"Independent close affected wrong side");
        other.containerMenu.setCarried(new ItemStack(Items.EMERALD,3));c.inventory().setItem(2,new ItemStack(Items.IRON_INGOT,11));
        h.assertTrue(c.remove(p,end(dual,0))==null,"Removal with open GUI failed");
        h.assertTrue(other.containerMenu==other.inventoryMenu&&!old.stillValid(p)&&carried(other,Items.EMERALD)==3&&dropped(h,f.cargo(),Items.IRON_INGOT)==11,"Removal retained menu/cursor or lost contents");
        h.runAtTickTime(8,()->{h.assertTrue(c.progress(0,0)==0&&c.progress(1,0)==0,"Removed drawer remained visible");h.succeed();});
    }
    @GameTest(template="assembly_test",timeoutTicks=85)
    public static void single_cabinet_round_trip_moves_one_inventory(GameTestHelper h) { roundTrip(h,false); }
    @GameTest(template="assembly_test",timeoutTicks=85)
    public static void double_cabinet_round_trip_moves_one_inventory(GameTestHelper h) { roundTrip(h,true); }
    private static void roundTrip(GameTestHelper h,boolean dual) {
        var f=frame(h,dual);var p=player(h,f.cargo());var c=install(h,f,p,dual);var inventory=c.inventory();
        inventory.setItem(inventory.getContainerSize()-1,new ItemStack(Items.DIAMOND,23));c.open(p,end(dual,0));var old=p.containerMenu;old.setCarried(new ItemStack(Items.EMERALD,2));
        h.assertTrue(f.toggleFrame(null)==null,"Assembly failed");var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(7)).getFirst();var wc=w.cargo().cabinet();
        h.assertTrue(!c.installed()&&c.inventory()==null&&wc.inventory()==inventory&&p.containerMenu==p.inventoryMenu&&!old.stillValid(p)&&carried(p,Items.EMERALD)==2,"Assembly copied inventory or retained stale menu/cursor");
        h.assertTrue(wc.install(new ItemStack(WagonContent.CABINET.get()),p,end(dual,0))!=null,"Occupied cabinet accepted another cabinet");
        h.assertTrue(wc.open(p,end(dual,1))==null,"Entity drawer inaccessible");var entityMenu=p.containerMenu;
        h.runAtTickTime(24,()->h.assertTrue(f.toggleFrame(null)==null,"Restoration failed to start"));
        h.runAtTickTime(49,()->{
            h.assertTrue(w.isRemoved()&&wc.inventory()==null&&c.installed()&&c.inventory()==inventory,"Restoration copied/lost cabinet");
            h.assertTrue(inventory.getItem(inventory.getContainerSize()-1).getCount()==23&&p.containerMenu==p.inventoryMenu&&!entityMenu.stillValid(p)&&dropped(h,f.cargo(),Items.DIAMOND)==0,"Restoration duplicated contents or retained entity GUI");
            h.assertTrue(c.open(p,end(dual,0))==null,"Restored drawer inaccessible");h.succeed();
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=115)
    public static void failed_restore_keeps_inventory_until_successful_retry(GameTestHelper h) {
        var f=frame(h,true);var p=player(h,f.cargo());var c=install(h,f,p,true);var inventory=c.inventory();inventory.setItem(53,new ItemStack(Items.GOLD_INGOT,31));
        h.assertTrue(f.toggleFrame(null)==null,"Assembly failed");var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(7)).getFirst();var obstruction=f.getBlockPos().offset(0,1,2);
        h.runAtTickTime(24,()->{h.assertTrue(f.toggleFrame(null)==null,"Restore did not begin");h.getLevel().setBlock(obstruction,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);});
        h.runAtTickTime(74,()->{
            h.assertTrue(!w.isRemoved()&&!c.installed()&&w.cargo().cabinet().inventory()==inventory&&inventory.getItem(53).getCount()==31&&dropped(h,w.cargo(),Items.GOLD_INGOT)==0,"Failed restore transferred/dropped goods");
            h.getLevel().setBlock(obstruction,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);h.assertTrue(f.toggleFrame(null)==null,"Retry failed");
        });
        h.runAtTickTime(99,()->{h.assertTrue(w.isRemoved()&&c.inventory()==inventory&&inventory.getItem(53).getCount()==31,"Retry lost inventory identity");h.succeed();});
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void save_reload_and_chunk_unload_keep_items_but_no_stale_drawers(GameTestHelper h) {
        var f=frame(h,true);var p=player(h,f.cargo());var c=install(h,f,p,true);
        var named=new ItemStack(Items.SHULKER_BOX);named.set(DataComponents.CUSTOM_NAME,Component.literal("Preserve components"));c.inventory().setItem(53,named);c.open(p,end(true,0));p.containerMenu.setCarried(new ItemStack(Items.EMERALD,4));
        var visual=f.cargo().save(h.getLevel().registryAccess(),true).getCompound("Cabinet");h.assertTrue(!visual.contains("Items")&&visual.getCompound("Drawer0").getBoolean("Open"),"Render packet leaked inventory or lost drawer state");
        f.onChunkUnloaded();h.assertTrue(p.containerMenu==p.inventoryMenu&&carried(p,Items.EMERALD)==4&&c.inventory().getItem(53)==named&&!c.opened(0),"Chunk unload lost items/cursor");
        var saved=f.saveWithFullMetadata(h.getLevel().registryAccess());f.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(c.rows()==6&&c.inventory().getItem(53).getHoverName().getString().equals("Preserve components")&&!c.opened(0)&&!c.opened(1),"Save/reload changed capacity/components or retained viewer state");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=45)
    public static void entity_sneak_removal_returns_empty_component_and_spills_once(GameTestHelper h) {
        var f=frame(h,false);var p=player(h,f.cargo());install(h,f,p,false);h.assertTrue(f.toggleFrame(null)==null,"Assembly failed");
        var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(7)).getFirst();var c=w.cargo().cabinet();
        c.inventory().setItem(0,new ItemStack(Items.DIAMOND,14));c.open(p,end(false,0));var old=p.containerMenu;
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE,2));p.setShiftKeyDown(true);
        h.assertTrue(w.cargo().interact(p,InteractionHand.MAIN_HAND,end(false,0)).consumesAction()&&!c.installed()&&p.getMainHandItem().getCount()==2,"Held item bypassed entity removal");
        h.assertTrue(p.containerMenu==p.inventoryMenu&&!old.stillValid(p)&&carried(p,WagonContent.CABINET.get())==1&&dropped(h,w.cargo(),Items.DIAMOND)==14,"Entity removal lost/duplicated contents or retained GUI");
        h.assertTrue(c.remove(p,end(false,0))!=null&&carried(p,WagonContent.CABINET.get())==1&&dropped(h,w.cargo(),Items.DIAMOND)==14,"Repeated entity removal duplicated goods");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=45)
    public static void logout_releases_viewer_and_drawers_finish_in_five_ticks(GameTestHelper h) {
        var f=frame(h,false);var p=player(h,f.cargo());var c=install(h,f,p,false);c.inventory().setItem(0,new ItemStack(Items.DIAMOND,13));
        h.assertTrue(c.open(p,end(false,0))==null&&c.opened(0)&&c.progress(0,0)==0,"Drawer flashed directly to endpoint");
        h.runAtTickTime(6,()->{
            h.assertTrue(c.progress(0,0)==1,"Drawer did not finish opening in five ticks");
            p.containerMenu.setCarried(new ItemStack(Items.EMERALD,6));
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
            h.assertTrue(p.containerMenu==p.inventoryMenu&&!c.opened(0)&&c.progress(0,0)==1&&carried(p,Items.EMERALD)==6&&c.inventory().getItem(0).getCount()==13,"Logout retained viewer, lost cursor, or flashed closing endpoint");
        });
        h.runAtTickTime(12,()->{h.assertTrue(c.progress(0,0)==0,"Drawer did not finish closing after logout");h.succeed();});
    }
    @GameTest(template="assembly_test",timeoutTicks=45)
    public static void tilted_entity_cabinet_uses_visible_end_ray(GameTestHelper h) {
        var f=frame(h,false);var p=player(h,f.cargo());install(h,f,p,false);
        h.assertTrue(f.toggleFrame(null)==null,"Assembly failed");var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(7)).getFirst();
        w.applyPose(new com.sange.tm_wagon.physics.WagonPose(w.position(),29.7F,.14F,.07F));
        Vec3 target=w.pose().point(end(false,0)),eye=target.add(w.pose().vector(new Vec3(-1.6,0,0)));p.setPos(eye.add(0,-p.getEyeHeight(),0));
        Vec3 delta=target.subtract(p.getEyePosition());p.setYRot((float)-Math.toDegrees(Math.atan2(delta.x,delta.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        var hit=w.pick(p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(p.entityInteractionRange())));
        h.assertTrue(hit.isPresent()&&Math.abs(w.pose().local(hit.get()).x-end(false,0).x)<.002,"Ray picked enlarged rotated bounds instead of drawer end");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE));
        h.assertTrue(w.interactAt(p,hit.get().subtract(w.position()),InteractionHand.MAIN_HAND).consumesAction()&&p.containerMenu instanceof ChestMenu,"Tilted entity cabinet end did not open with held item");
        p.closeContainer();h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=45)
    public static void block_seat_and_hammer_return_cabinet_and_contents(GameTestHelper h) {
        var f=frame(h,false);var p=player(h,f.cargo());var c=install(h,f,p,false);c.inventory().setItem(0,new ItemStack(Items.DIAMOND,17));c.open(p,end(false,0));
        f.remove(Set.of(WagonSlot.SEAT),true);c.destroy(true);f.remove(Set.of(WagonSlot.SEAT),true);
        h.assertTrue(!c.installed()&&p.containerMenu==p.inventoryMenu&&dropped(h,f.cargo(),Items.DIAMOND)==17&&dropped(h,f.cargo(),WagonContent.CABINET.get())==1
            &&dropped(h,f.cargo(),WagonContent.PART_ITEMS.get(WagonPart.SINGLE_SEAT).get())==1,"Seat destruction did not return cabinet, seat and contents exactly once");
        f.dismantle(false,true);
        var w=WagonContent.WAGON.get().create(h.getLevel());w.configure(WagonEntity.defaultParts(),Direction.NORTH);w.setPos(f.cargoPose().position());h.getLevel().addFreshEntity(w);
        var tag=new CompoundTag();tag.putBoolean("Installed",true);tag.putInt("Rows",3);var cargo=w.cargo().save(h.getLevel().registryAccess(),false);cargo.put("Cabinet",tag);w.cargo().load(cargo,h.getLevel().registryAccess());
        w.cargo().cabinet().inventory().setItem(0,new ItemStack(Items.IRON_INGOT,21));com.sange.tm_wagon.assembly.DismantlingHammerGameTests.strike(h,w);w.cargo().destroy(true,true);
        h.assertTrue(w.isRemoved()&&dropped(h,w.cargo(),Items.IRON_INGOT)==21&&dropped(h,w.cargo(),WagonContent.CABINET.get())==2,"Hammer lost cabinet or duplicated contents");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=45)
    public static void double_block_seat_returns_one_empty_cabinet_and_all_items(GameTestHelper h) {
        var f=frame(h,true);var p=player(h,f.cargo());var c=install(h,f,p,true);var inventory=c.inventory();
        inventory.setItem(0,new ItemStack(Items.DIAMOND,12));inventory.setItem(53,new ItemStack(Items.IRON_INGOT,28));c.open(p,end(true,1));var old=p.containerMenu;
        f.remove(Set.of(WagonSlot.SEAT),true);f.remove(Set.of(WagonSlot.SEAT),true);
        h.assertTrue(!c.installed()&&inventory.isEmpty()&&p.containerMenu==p.inventoryMenu&&!old.stillValid(p),"Seat removal retained items or an active old GUI");
        h.assertTrue(dropped(h,f.cargo(),WagonContent.CABINET.get())==1&&dropped(h,f.cargo(),WagonContent.PART_ITEMS.get(WagonPart.DOUBLE_SEAT).get())==1
            &&dropped(h,f.cargo(),Items.DIAMOND)==12&&dropped(h,f.cargo(),Items.IRON_INGOT)==28,"Double seat did not drop each component and all contents exactly once");h.succeed();
    }
}
