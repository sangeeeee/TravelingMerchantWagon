package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.assembly.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;
import tschipp.carryon.common.carry.*;
import tschipp.carryon.common.config.ListHandler;
import tschipp.carryon.Constants;

@GameTestHolder("tm_wagon_carryon")
@PrefixGameTestTemplate(false)
public class CarryOnGameTests {
    private static boolean available(GameTestHelper h) {
        boolean loaded=ModList.get().isLoaded("carryon");
        if(Boolean.getBoolean("tm_wagon.requireCarryOnTest"))h.assertTrue(loaded,"Carry On missing");
        if(!loaded)h.succeed();return loaded;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,CargoHold hold,boolean shift,boolean carryKey) {
        var p=CargoGameTests.serverPlayer(h,hold);p.setGameMode(GameType.SURVIVAL);
        p.setPos(hold.owner().cargoPose().point(hold.centreAt(0).add(-1.1,0,0)));
        p.setShiftKeyDown(shift);var data=CarryOnDataManager.getCarryData(p);data.setKeyPressed(carryKey);data.setTick(-1);return p;
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void carryon_cargo_container_transfer_and_world_placement(GameTestHelper h) {
        if(!available(h))return;
        var hold=CargoGameTests.wagon(h).cargo();var p=player(h,hold,true,true);
        h.assertTrue(hold.place(0,new ItemStack(Items.CHEST),p)==null,"Place cargo failed");
        var entry=hold.entry(0);entry.inventory.setItem(26,new ItemStack(Items.DIAMOND,17));
        CargoMenus.open(hold,entry,p);
        hold.interact(p,InteractionHand.MAIN_HAND,hold.centreAt(0).add(0,.68,0));
        var data=CarryOnDataManager.getCarryData(p);
        h.assertTrue(data.isCarrying(CarryOnData.CarryType.BLOCK)&&hold.entry(0)==null,"Shift did not prefer Carry On");
        h.assertTrue(p.containerMenu==p.inventoryMenu,"Old cargo menu remains open");
        var be=data.getBlockEntity(BlockPos.ZERO,h.getLevel().registryAccess());
        h.assertTrue(((net.minecraft.world.Container)be).getItem(26).getCount()==17,"Carried inventory lost");
        h.assertTrue(hold.take(0,p)!=null,"Cargo still has a second owner");
        var pos=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),3);
        p.setPos(Vec3.atCenterOf(pos).add(0,0,1));p.tickCount+=2;
        h.assertTrue(PlacementHandler.tryPlaceBlock(p,pos,Direction.UP,(at,state)->true),"Native Carry On placement failed");
        h.assertTrue(!CarryOnDataManager.getCarryData(p).isCarrying()&&((net.minecraft.world.Container)h.getLevel().getBlockEntity(pos.above())).getItem(26).getCount()==17,"World placement lost contents");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void carryon_custom_key_and_dynamic_policy_fallback(GameTestHelper h) {
        if(!available(h))return;
        var hold=CargoGameTests.wagon(h).cargo();var p=player(h,hold,false,true);
        h.assertTrue(hold.place(0,new ItemStack(Items.BARREL),p)==null,"Place barrel failed");
        hold.interact(p,InteractionHand.MAIN_HAND,hold.centreAt(0).add(0,.68,0));
        h.assertTrue(CarryOnDataManager.getCarryData(p).isCarrying()&&hold.entry(0)==null,"Custom key without sneak cannot pick up");
        CarryOnDataManager.getCarryData(p).clear();p.getInventory().clearContent();p.setShiftKeyDown(true);
        h.assertTrue(hold.place(0,new ItemStack(Items.BARREL),p)==null,"Place second barrel failed");
        CarryOnDataManager.getCarryData(p).setKeyPressed(false);
        hold.interact(p,InteractionHand.MAIN_HAND,hold.centreAt(0).add(0,.68,0));
        h.assertTrue(!CarryOnDataManager.getCarryData(p).isCarrying()&&hold.entry(0)==null&&p.getInventory().items.stream().anyMatch(item->item.is(Items.BARREL)),"Sneak fallback: entry="+hold.entry(0)+", carrying="+CarryOnDataManager.getCarryData(p).isCarrying()+", inventory="+p.getInventory().items);
        p.getInventory().clearContent();CarryOnDataManager.getCarryData(p).setKeyPressed(true);
        var config=Constants.COMMON_CONFIG;var old=config.blacklist.forbiddenTiles;
        try {
            config.blacklist.forbiddenTiles=new String[]{"minecraft:barrel"};ListHandler.initConfigLists();
            h.assertTrue(hold.place(0,new ItemStack(Items.BARREL),p)==null,"Place denied barrel failed");
            hold.interact(p,InteractionHand.MAIN_HAND,hold.centreAt(0).add(0,.68,0));
            h.assertTrue(!CarryOnDataManager.getCarryData(p).isCarrying()&&hold.entry(0)==null,"Blacklist did not fall back to unloading");
        } finally {config.blacklist.forbiddenTiles=old;ListHandler.initConfigLists();}
        p.getInventory().clearContent();CarryOnDataManager.getCarryData(p).setKeyPressed(true);
        boolean oldWhitelist=config.settings.useWhitelistBlocks;var oldAllowed=config.whitelist.allowedBlocks;
        try {
            config.settings.useWhitelistBlocks=true;config.whitelist.allowedBlocks=new String[]{"minecraft:chest"};ListHandler.initConfigLists();
            h.assertTrue(hold.place(0,new ItemStack(Items.BARREL),p)==null,"Place whitelist test failed");
            h.assertTrue(!CarryOnCargo.pickup(hold,hold.entry(0),p)&&hold.entry(0)!=null,"Whitelist bypassed");
            config.whitelist.allowedBlocks=new String[]{"minecraft:barrel"};ListHandler.initConfigLists();
            h.assertTrue(CarryOnCargo.pickup(hold,hold.entry(0),p)&&hold.entry(0)==null,"Updated whitelist ignored");
        } finally {config.settings.useWhitelistBlocks=oldWhitelist;config.whitelist.allowedBlocks=oldAllowed;ListHandler.initConfigLists();}
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void carryon_wagon_and_proxies_never_pick_up(GameTestHelper h) {
        if(!available(h))return;
        var wagon=CargoGameTests.wagon(h);var p=player(h,wagon.cargo(),true,true);
        h.assertTrue(!PickupHandler.tryPickupEntity(p,wagon,e->true)&&!wagon.isRemoved(),"Whole wagon picked up");
        wagon.discard();var frame=CargoGameTests.frame(h);p.setPos(Vec3.atCenterOf(frame.getBlockPos()));
        h.assertTrue(!PickupHandler.tryPickUpBlock(p,frame.getBlockPos(),h.getLevel(),(s,at)->true),"Frame picked up");
        for(var at:frame.layout().keySet())h.assertTrue(!PickupHandler.tryPickUpBlock(p,at,h.getLevel(),(s,pos)->true),"Part proxy picked up");
        h.assertTrue(!CarryOnDataManager.getCarryData(p).isCarrying(),"Structure entered carry data");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void carryon_block_form_and_workstation_data(GameTestHelper h) {
        if(!available(h))return;
        var hold=CargoGameTests.frame(h).cargo();var p=player(h,hold,false,true);
        h.assertTrue(hold.place(0,new ItemStack(Items.FURNACE),p)==null,"Place furnace failed");
        var entry=hold.entry(0);entry.inventory.setItem(0,new ItemStack(Items.IRON_ORE,8));entry.inventory.setItem(1,new ItemStack(Items.COAL,4));entry.burn=123;entry.cook=45;
        h.assertTrue(CarryOnCargo.pickup(hold,entry,p)&&hold.entry(0)==null,"Block-form pickup failed");
        var be=CarryOnDataManager.getCarryData(p).getBlockEntity(BlockPos.ZERO,h.getLevel().registryAccess());var nbt=be.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertTrue(nbt.getShort("BurnTime")==123&&nbt.getShort("CookTime")==45&&((net.minecraft.world.Container)be).getItem(0).getCount()==8,"Furnace state lost");h.succeed();
    }

    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void carryon_failed_transfer_keeps_single_inventory(GameTestHelper h) {
        if(!available(h))return;
        var wagon=CargoGameTests.wagon(h);boolean[] reject={false};
        var owner=new CargoOwner() {
            final CargoHold hold=new CargoHold(this);
            public net.minecraft.world.level.Level cargoLevel(){return h.getLevel();}
            public com.sange.tm_wagon.physics.WagonPose cargoPose(){return wagon.pose();}
            public boolean cargoLive(){return true;}
            public boolean cargoBusy(){return false;}
            public String cargoGeometryChanged(){return reject[0]?"test:reject":null;}
            public void cargoChanged(boolean visible){}
            public CargoHold cargo(){return hold;}
        };
        var hold=owner.cargo();var p=player(h,hold,true,true);
        h.assertTrue(hold.place(0,new ItemStack(Items.CHEST),p)==null,"Place failed");
        var entry=hold.entry(0);entry.inventory.setItem(0,new ItemStack(Items.DIAMOND,23));reject[0]=true;
        hold.interact(p,InteractionHand.MAIN_HAND,hold.centreAt(0).add(0,.68,0));
        h.assertTrue(hold.entry(0)==entry&&entry.inventory.getItem(0).getCount()==23&&!CarryOnDataManager.getCarryData(p).isCarrying(),"Failed commit lost or duplicated ownership");
        reject[0]=false;p.tickCount+=2;
        hold.interact(p,InteractionHand.MAIN_HAND,hold.centreAt(0).add(0,.68,0));
        h.assertTrue(hold.entry(0)==null&&CarryOnDataManager.getCarryData(p).isCarrying(),"Retry failed");
        reject[0]=true;p.tickCount+=2;CarryOnCargo.place(hold,0,p);
        h.assertTrue(hold.entry(0)==null&&CarryOnDataManager.getCarryData(p).isCarrying(),"Failed reverse commit consumed source");
        var be=CarryOnDataManager.getCarryData(p).getBlockEntity(BlockPos.ZERO,h.getLevel().registryAccess());
        h.assertTrue(((net.minecraft.world.Container)be).getItem(0).getCount()==23,"Reverse rollback lost inventory");
        reject[0]=false;CarryOnCargo.place(hold,0,p);
        h.assertTrue(hold.entry(0)!=null&&hold.entry(0).inventory.getItem(0).getCount()==23&&!CarryOnDataManager.getCarryData(p).isCarrying(),"Reverse retry failed");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void carryon_cover_keeps_normal_sneak_interaction(GameTestHelper h) {
        if(!available(h))return;
        var frame=CargoGameTests.frame(h);var hold=frame.cargo();var p=player(h,hold,true,true);
        var saved=hold.save(h.getLevel().registryAccess(),false);var cover=new CompoundTag();cover.putBoolean("Installed",true);cover.putInt("OpenRows",hold.rows());saved.put("Cover",cover);hold.load(saved,h.getLevel().registryAccess());
        h.assertTrue(hold.cover().installed(),"Cover test setup failed");
        var roll=hold.cover().rollBox(hold.owner().cargoBody());var hit=roll.getCenter();p.setPos(hold.owner().cargoPose().point(hit.add(-1,1,0)));
        hold.interact(p,InteractionHand.MAIN_HAND,hit);
        h.assertTrue(hold.cover().openRows()==hold.rows()-1&&!CarryOnDataManager.getCarryData(p).isCarrying(),"Carry key swallowed cloth interaction");h.succeed();
    }

    private static void carriedBlock(GameTestHelper h,net.minecraft.server.level.ServerPlayer p,net.minecraft.world.level.block.state.BlockState state,int diamonds) {
        net.minecraft.world.level.block.entity.BlockEntity be=state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock factory?factory.newBlockEntity(BlockPos.ZERO,state):null;
        if(be!=null){be.setLevel(h.getLevel());if(be instanceof net.minecraft.world.Container container&&diamonds>0)container.setItem(0,new ItemStack(Items.DIAMOND,diamonds));}
        var data=CarryOnDataManager.getCarryData(p);data.clear();data.setBlock(state,be);CarryOnDataManager.setCarryData(p,data);p.tickCount+=2;
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void carryon_places_into_selected_slot_and_roundtrips_inventory(GameTestHelper h) {
        if(!available(h))return;
        var hold=CargoGameTests.wagon(h).cargo();var p=player(h,hold,false,false);
        carriedBlock(h,p,Blocks.CHEST.defaultBlockState(),19);
        hold.interact(p,InteractionHand.MAIN_HAND,hold.centreAt(3).add(0,.01,0));
        h.assertTrue(!CarryOnDataManager.getCarryData(p).isCarrying()&&hold.entry(0)==null&&hold.entry(3)!=null,"Carried chest did not use selected slot");
        var entry=hold.entry(3);h.assertTrue(entry.inventory.getItem(0).getCount()==19,"Placed inventory lost");
        var tag=entry.save(h.getLevel().registryAccess(),false);var loaded=CargoEntry.load(hold,tag,h.getLevel().registryAccess());
        h.assertTrue(loaded.inventory.getItem(0).getCount()==19&&!loaded.item.has(net.minecraft.core.component.DataComponents.CONTAINER),"Saved inventory has two owners");
        p.setPos(hold.position(entry).add(-1.1,0,0));p.tickCount+=2;CarryOnDataManager.getCarryData(p).setKeyPressed(true);
        h.assertTrue(CarryOnCargo.pickup(hold,entry,p)&&hold.entry(3)==null,"Cannot carry placed chest back");
        var be=CarryOnDataManager.getCarryData(p).getBlockEntity(BlockPos.ZERO,h.getLevel().registryAccess());
        h.assertTrue(((net.minecraft.world.Container)be).getItem(0).getCount()==19,"Repeated transfer duplicated/lost inventory");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void carryon_block_click_places_cargo_before_world_handler(GameTestHelper h) {
        if(!available(h))return;
        var frame=CargoGameTests.frame(h);var hold=frame.cargo();var p=player(h,hold,false,false);
        carriedBlock(h,p,Blocks.BARREL.defaultBlockState(),13);
        var point=frame.cargoPose().point(hold.centreAt(0).add(0,.01,0));var at=BlockPos.containing(point);
        var hit=new net.minecraft.world.phys.BlockHitResult(point,Direction.UP,at,false);
        var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,at,hit);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        h.assertTrue(event.isCanceled()&&hold.entry(0)!=null&&hold.entry(0).inventory.getItem(0).getCount()==13&&!CarryOnDataManager.getCarryData(p).isCarrying(),"Carry On world handler intercepted cargo slot");
        h.assertTrue(h.getLevel().getBlockState(at).getBlock()!=Blocks.BARREL&&h.getLevel().getBlockState(at.above()).getBlock()!=Blocks.BARREL,"Placed full block beside wagon");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void carryon_disallowed_and_occupied_slots_keep_carried_source(GameTestHelper h) {
        if(!available(h))return;
        var hold=CargoGameTests.wagon(h).cargo();var p=player(h,hold,false,false);
        for(var block:java.util.List.of(Blocks.RED_BED,Blocks.OAK_DOOR,WagonContent.FRAME.get())) {
            carriedBlock(h,p,block.defaultBlockState(),0);
            hold.interact(p,InteractionHand.MAIN_HAND,hold.centreAt(0).add(0,.01,0));
            h.assertTrue(hold.entry(0)==null&&CarryOnDataManager.getCarryData(p).isCarrying(),"Forbidden cargo consumed: "+block);
        }
        CarryOnDataManager.getCarryData(p).clear();h.assertTrue(hold.place(0,new ItemStack(Items.STONE),p)==null,"Setup occupied slot failed");
        carriedBlock(h,p,Blocks.CHEST.defaultBlockState(),7);p.setShiftKeyDown(true);
        hold.interact(p,InteractionHand.MAIN_HAND,hold.centreAt(0).add(0,.68,0));
        h.assertTrue(hold.entry(0).state.is(Blocks.STONE)&&CarryOnDataManager.getCarryData(p).isCarrying(),"Occupied slot unloaded or source consumed");
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void carryon_components_cannot_be_carried_even_with_all_blocks_enabled(GameTestHelper h) {
        if(!available(h))return;
        var hold=CargoGameTests.wagon(h).cargo();var p=player(h,hold,true,true);boolean before=Constants.COMMON_CONFIG.settings.pickupAllBlocks;
        try {
            Constants.COMMON_CONFIG.settings.pickupAllBlocks=true;
            h.assertTrue(hold.place(0,new ItemStack(WagonContent.STOOL.get()),p)==null,"Stool setup failed");
            h.assertTrue(hold.place(5,new ItemStack(WagonContent.STRAW_MAT.get()),p)==null,"Mat setup failed");
            h.assertTrue(!CarryOnCargo.pickup(hold,hold.entry(0),p)&&!CarryOnCargo.pickup(hold,hold.entry(5),p)&&!CarryOnDataManager.getCarryData(p).isCarrying(),"Component exposed as a world block");
            hold.interact(p,InteractionHand.MAIN_HAND,hold.centreAt(0).add(0,.5,0));
            h.assertTrue(hold.entry(0)==null&&!CarryOnDataManager.getCarryData(p).isCarrying(),"Normal stool unloading was disabled");
        } finally {Constants.COMMON_CONFIG.settings.pickupAllBlocks=before;}h.succeed();
    }
}
