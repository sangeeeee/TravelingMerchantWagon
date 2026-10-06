package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.compat.BackpackCompat;
import com.sange.tm_wagon.compat.backpack.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.fabricmc.loader.api.FabricLoader;
import tschipp.carryon.Constants;
import tschipp.carryon.common.carry.*;
import tschipp.carryon.common.config.ListHandler;

public final class BackpackGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
    private static final String[] TYPES={"travelersbackpack:standard","sophisticatedbackpacks:backpack"};
    private static boolean available(GameTestHelper h) {
        h.assertTrue(FabricLoader.getInstance().isModLoaded("travelersbackpack")&&FabricLoader.getInstance().isModLoaded("sophisticatedbackpacks"),"Backpack test dependencies missing");return true;
    }
    public static ItemStack filled(ServerLevel level,String type) {
        var stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(type)));
        if(stack.isEmpty())throw new IllegalStateException("Missing backpack "+type);
        if(BackpackCompat.traveler(stack))
            new com.tiviacz.travelersbackpack.inventory.BackpackWrapper(stack,1,null,level).getStorage().setStackInSlot(0,new ItemStack(Items.DIAMOND,17));
        else net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper.fromStack(stack).getInventoryHandler().setStackInSlot(0,new ItemStack(Items.DIAMOND,17));
        var tag=new CompoundTag();tag.putString("Marker","kept");stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Kept backpack"));return stack;
    }
    public static int contents(ServerLevel level,ItemStack stack) {
        return BackpackCompat.traveler(stack)
            ?new com.tiviacz.travelersbackpack.inventory.BackpackWrapper(stack,1,null,level).getStorage().getStackInSlot(0).getCount()
            :net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper.fromStack(stack).getInventoryHandler().getStackInSlot(0).getCount();
    }
    private static ServerPlayer player(GameTestHelper h,CargoHold hold,boolean sneak,boolean carry) {
        var p=CargoGameTests.serverPlayer(h,hold);p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.setShiftKeyDown(sneak);
        p.setPos(hold.owner().cargoPose().point(hold.centreAt(0).add(-1.1,0,0)));
        if(FabricLoader.getInstance().isModLoaded("carryon")){var data=CarryOnDataManager.getCarryData(p);data.clear();data.setKeyPressed(carry);data.setTick(-1);p.setAttached(tschipp.carryon.CarryOnFabricMod.CARRY_ON_DATA_ATTACHMENT_TYPE,data);}
        return p;
    }
    private static void click(CargoHold hold,ServerPlayer p,int slot,boolean occupied) {
        hold.interact(p,InteractionHand.MAIN_HAND,hold.centreAt(slot).add(0,occupied?.6:.01,0));
    }
    private static void check(GameTestHelper h,ItemStack stack,int expected) {
        h.assertTrue(!stack.isEmpty()&&contents(h.getLevel(),stack)==expected,"Backpack contents changed: "+stack);
        h.assertTrue("kept".equals(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("Marker")),"Custom data lost");
        h.assertTrue("Kept backpack".equals(stack.getHoverName().getString()),"Name lost");
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=40)
    public void both_forms_sneak_place_open_with_any_hand_and_unload(GameTestHelper h) {
        if(!available(h))return;
        var wagon=CargoGameTests.wagon(h);
        for(int form=0;form<2;form++) {
            CargoHold hold=form==0?wagon.cargo():CargoGameTests.frame(h).cargo();
            var p=player(h,hold,true,false);
            for(String type:TYPES) {
                p.setShiftKeyDown(true);p.setItemInHand(InteractionHand.MAIN_HAND,filled(h.getLevel(),type));
                click(hold,p,0,false);
                h.assertTrue(p.getMainHandItem().isEmpty()&&hold.entry(0)!=null,"Sneak placement failed for "+type+" form "+form);
                var entry=hold.entry(0);check(h,entry.item,17);
                p.setShiftKeyDown(false);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK,7));click(hold,p,0,true);
                h.assertTrue(entry.id.equals(BackpackSessions.id(p.containerMenu))&&p.containerMenu.stillValid(p),"Native menu failed for "+type);
                h.assertTrue(p.getMainHandItem().is(Items.STICK)&&p.getMainHandItem().getCount()==7,"Opening replaced held item");
                p.setShiftKeyDown(true);click(hold,p,0,true);
                h.assertTrue(hold.entry(0)==null&&p.containerMenu==p.inventoryMenu,"Unload retained menu/entry");
                var returned=p.getInventory().items.stream().filter(BackpackCompat::matches).findFirst().orElse(ItemStack.EMPTY);check(h,returned,17);
                h.assertTrue(p.getMainHandItem().is(Items.STICK),"Unload replaced held item");p.getInventory().clearContent();
            }
            if(form==0)wagon.discard();
        }h.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=35)
    public void native_menu_edits_settings_and_save_reload(GameTestHelper h) {
        available(h);var hold=CargoGameTests.wagon(h).cargo();var p=player(h,hold,false,false);
        for(String type:TYPES) {
            h.assertTrue(hold.place(0,filled(h.getLevel(),type),p)==null,"Place failed");var entry=hold.entry(0);
            BackpackCompat.open(hold,entry,p);
            if(p.containerMenu instanceof TravelerCargo.Menu menu) {
                menu.getWrapper().getStorage().setStackInSlot(0,new ItemStack(Items.DIAMOND,23));
                com.tiviacz.travelersbackpack.common.ServerActions.openBackpackSettings(p,p.getId(),true);
                h.assertTrue(p.containerMenu instanceof TravelerCargo.Settings,"Traveler settings used wrong context");
                com.tiviacz.travelersbackpack.common.ServerActions.openBackpackSettings(p,p.getId(),false);
                h.assertTrue(p.containerMenu instanceof TravelerCargo.Menu,"Traveler settings return failed");
            } else {
                var context=((net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer)p.containerMenu).getBackpackContext();
                context.getBackpackWrapper(p).getInventoryHandler().setStackInSlot(0,new ItemStack(Items.DIAMOND,23));
            }
            p.closeContainer();var saved=hold.save(h.getLevel().registryAccess(),false);hold.load(saved,h.getLevel().registryAccess());
            check(h,hold.entry(0).item,23);h.assertTrue(hold.take(0,p)==null,"Take failed");p.getInventory().clearContent();
        }h.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=35)
    public void full_inventory_drops_single_intact_backpack_without_replacing_hand(GameTestHelper h) {
        available(h);var hold=CargoGameTests.wagon(h).cargo();var p=player(h,hold,true,false);
        for(int i=0;i<TYPES.length;i++) {
            String type=TYPES[i];int slot=i*2;
            h.assertTrue(hold.place(slot,filled(h.getLevel(),type),p)==null,"Place failed");
            for(int inventorySlot=0;inventorySlot<p.getInventory().items.size();inventorySlot++)p.getInventory().items.set(inventorySlot,new ItemStack(Items.STONE,64));
            click(hold,p,slot,true);h.assertTrue(hold.entry(slot)==null&&p.getMainHandItem().is(Items.STONE),"Full inventory unload failed/replaced hand");
        }
        // Native custom item entities can be installed by a deferred server task.
        h.runAfterDelay(2,()->{
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,net.minecraft.world.phys.AABB.ofSize(hold.owner().cargoPose().position(),16,16,16),e->BackpackCompat.matches(e.getItem()));
            h.assertTrue(drops.size()==2,"Dropped "+drops.size()+" backpack owners");for(var drop:drops)check(h,drop.getItem(),17);h.succeed();
        });
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=35)
    public void destruction_and_transfer_keep_one_owner_and_close_menus(GameTestHelper h) {
        available(h);var w=CargoGameTests.wagon(h);var hold=w.cargo();var p=player(h,hold,false,false);
        for(int i=0;i<TYPES.length;i++)h.assertTrue(hold.place(i*2,filled(h.getLevel(),TYPES[i]),p)==null,"Place failed");
        var entry=hold.entry(0);BackpackCompat.open(hold,entry,p);
        var target=new CargoHold(hold.owner());hold.transferTo(target);
        h.assertTrue(hold.empty()&&p.containerMenu==p.inventoryMenu&&target.entry(0)==entry,"Transfer retained source/menu");
        BackpackCompat.open(target,entry,p);
        ((TravelerCargo.Menu)p.containerMenu).getWrapper().getStorage().setStackInSlot(0,new ItemStack(Items.DIAMOND,23));p.closeContainer();
        check(h,target.entry(0).item,23);target.destroy(true);
        h.runAfterDelay(2,()->{
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,w.getBoundingBox().inflate(8),e->BackpackCompat.matches(e.getItem()));
            h.assertTrue(target.empty()&&drops.size()==2,"Destruction lost/duplicated backpacks: "+drops.size());
            for(var drop:drops)check(h,drop.getItem(),BackpackCompat.traveler(drop.getItem())?23:17);h.succeed();
        });
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=35)
    public void carryon_priority_remapped_key_and_round_trip(GameTestHelper h) {
        available(h);h.assertTrue(FabricLoader.getInstance().isModLoaded("carryon"),"Carry On test dependency missing");
        var hold=CargoGameTests.wagon(h).cargo();var p=player(h,hold,true,true);var config=Constants.COMMON_CONFIG;
        boolean old=config.settings.useWhitelistBlocks;var allowed=config.whitelist.allowedBlocks;
        try {
            config.settings.useWhitelistBlocks=true;config.whitelist.allowedBlocks=TYPES;ListHandler.initConfigLists();
            for(String type:TYPES) {
                h.assertTrue(hold.place(0,filled(h.getLevel(),type),p)==null,"Place failed");p.getInventory().clearContent();
                click(hold,p,0,true);var data=CarryOnDataManager.getCarryData(p);
                h.assertTrue(CarryOnDataManager.getCarryData(p).isCarrying()&&hold.entry(0)==null&&p.getInventory().items.stream().noneMatch(BackpackCompat::matches),"Carry On priority failed for "+type);
                p.tickCount+=2;click(hold,p,2,false);
                h.assertTrue(!CarryOnDataManager.getCarryData(p).isCarrying()&&hold.entry(2)!=null,"Carried placement failed for "+type);check(h,hold.entry(2).item,17);
                p.tickCount+=2;p.setShiftKeyDown(false);click(hold,p,2,true);
                h.assertTrue(CarryOnDataManager.getCarryData(p).isCarrying()&&hold.entry(2)==null,"Remapped Carry On key failed");
                p.tickCount+=2;click(hold,p,0,false);check(h,hold.entry(0).item,17);
                p.setShiftKeyDown(true);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));p.tickCount+=2;click(hold,p,0,true);
                h.assertTrue(!CarryOnDataManager.getCarryData(p).isCarrying()&&hold.entry(0)==null&&p.getMainHandItem().is(Items.STICK),"Occupied hands did not fall back to unloading");
                check(h,p.getInventory().items.stream().filter(BackpackCompat::matches).findFirst().orElse(ItemStack.EMPTY),17);p.getInventory().clearContent();
            }
        } finally {config.settings.useWhitelistBlocks=old;config.whitelist.allowedBlocks=allowed;ListHandler.initConfigLists();}h.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=35)
    public void carryon_denied_policy_falls_back_to_item_unload(GameTestHelper h) {
        available(h);var hold=CargoGameTests.wagon(h).cargo();var p=player(h,hold,true,true);var config=Constants.COMMON_CONFIG;
        boolean old=config.settings.useWhitelistBlocks;var allowed=config.whitelist.allowedBlocks;
        try {
            config.settings.useWhitelistBlocks=true;config.whitelist.allowedBlocks=new String[]{"minecraft:chest"};ListHandler.initConfigLists();
            for(String type:TYPES) {
                h.assertTrue(hold.place(0,filled(h.getLevel(),type),p)==null,"Place failed");click(hold,p,0,true);
                h.assertTrue(hold.entry(0)==null&&!CarryOnDataManager.getCarryData(p).isCarrying(),"Denied policy bypassed");
                check(h,p.getInventory().items.stream().filter(BackpackCompat::matches).findFirst().orElse(ItemStack.EMPTY),17);p.getInventory().clearContent();
            }
        } finally {config.settings.useWhitelistBlocks=old;config.whitelist.allowedBlocks=allowed;ListHandler.initConfigLists();}h.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=35)
    public void blocked_placement_does_not_consume_source(GameTestHelper h) {
        available(h);var hold=CargoGameTests.wagon(h).cargo();var p=player(h,hold,true,false);
        var pos=BlockPos.containing(hold.owner().cargoPose().point(hold.centreAt(0).add(0,.2,0)));
        h.getLevel().setBlock(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        for(String type:TYPES) {
            var stack=filled(h.getLevel(),type);p.setItemInHand(InteractionHand.MAIN_HAND,stack);click(hold,p,0,false);
            h.assertTrue(hold.entry(0)==null&&p.getMainHandItem()==stack,"Failed placement consumed source");check(h,stack,17);
        }h.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=35)
    public void creative_placement_clones_sophisticated_storage(GameTestHelper h) {
        available(h);var hold=CargoGameTests.wagon(h).cargo();var p=player(h,hold,true,false);p.getAbilities().instabuild=true;
        var stack=filled(h.getLevel(),TYPES[1]);
        var source=net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper.fromStack(stack);
        var id=source.getContentsUuid().orElseThrow();h.assertTrue(hold.place(0,stack,p)==null,"Creative placement failed");
        var placed=net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper.fromStack(hold.entry(0).item);
        h.assertTrue(stack.getCount()==1&&!id.equals(placed.getContentsUuid().orElseThrow()),"Creative storage identity duplicated");
        placed.getInventoryHandler().setStackInSlot(0,new ItemStack(Items.DIAMOND,23));
        check(h,stack,17);check(h,hold.entry(0).item,23);h.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=35)
    public void cover_blocks_backpack_gui_and_carryon(GameTestHelper h) {
        available(h);var hold=CargoGameTests.wagon(h).cargo();var p=player(h,hold,false,true);
        h.assertTrue(hold.place(0,filled(h.getLevel(),TYPES[0]),p)==null,"Traveler placement failed");
        h.assertTrue(hold.place(2,filled(h.getLevel(),TYPES[1]),p)==null,"Sophisticated placement failed");
        h.assertTrue(hold.cover().install(new ItemStack(com.sange.tm_wagon.assembly.WagonContent.CARGO_COVER.get()),p,new net.minecraft.world.phys.Vec3(-1.15625,1.9,.5))==null,"Cover installation failed");
        p.setPos(hold.owner().cargoPose().point(new net.minecraft.world.phys.Vec3(0,3.2,0)));
        for(int slot:new int[]{0,2}) {
            BackpackCompat.open(hold,hold.entry(slot),p);
            h.assertTrue(p.containerMenu==p.inventoryMenu,"Opened through cloth");
            h.assertTrue(!CarryOnCargo.pickup(hold,hold.entry(slot),p)&&hold.entry(slot)!=null,"Carried through cloth");
        }h.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=35)
    public void carryon_world_placement_and_pickup_keep_native_backpack_data(GameTestHelper h) {
        available(h);var hold=CargoGameTests.wagon(h).cargo();var p=player(h,hold,true,true);var config=Constants.COMMON_CONFIG;
        boolean old=config.settings.useWhitelistBlocks;var allowed=config.whitelist.allowedBlocks;
        try {
            config.settings.useWhitelistBlocks=true;config.whitelist.allowedBlocks=TYPES;ListHandler.initConfigLists();
            for(String type:TYPES) {
                var key=CarryOnDataManager.getCarryData(p);key.setKeyPressed(true);p.setAttached(tschipp.carryon.CarryOnFabricMod.CARRY_ON_DATA_ATTACHMENT_TYPE,key);
                p.setPos(hold.owner().cargoPose().point(hold.centreAt(0).add(-1.1,0,0)));
                h.assertTrue(hold.place(0,filled(h.getLevel(),type),p)==null,"Initial placement failed");
                p.tickCount+=2;
                h.assertTrue(CarryOnCargo.pickup(hold,hold.entry(0),p)&&CarryOnDataManager.getCarryData(p).isCarrying(),"Pickup failed: "+type+", key="+CarryOnDataManager.getCarryData(p).isKeyPressed()+", hands="+p.getMainHandItem()+"/"+p.getOffhandItem()+", carry="+CarryOnDataManager.getCarryData(p).isCarrying());
                var at=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlock(at,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                p.setPos(net.minecraft.world.phys.Vec3.atCenterOf(at).add(0,0,1));p.tickCount+=2;
                h.assertTrue(PlacementHandler.tryPlaceBlock(p,at,net.minecraft.core.Direction.UP,(pos,state)->true),"Carry On world placement failed for "+type);
                var be=h.getLevel().getBlockEntity(at.above());check(h,BackpackCompat.fromBlockEntity(be,BackpackCompat.blockItem(be.getBlockState().getBlock()),h.getLevel().registryAccess()),17);
                p.tickCount+=2;var keyAgain=CarryOnDataManager.getCarryData(p);keyAgain.setKeyPressed(true);p.setAttached(tschipp.carryon.CarryOnFabricMod.CARRY_ON_DATA_ATTACHMENT_TYPE,keyAgain);h.assertTrue(PickupHandler.tryPickUpBlock(p,at.above(),h.getLevel(),(s,pos)->true),"Native world pickup failed");
                p.tickCount+=2;p.setPos(hold.owner().cargoPose().point(hold.centreAt(0).add(-1.1,0,0)));CarryOnCargo.place(hold,0,p);
                check(h,hold.entry(0).item,17);h.assertTrue(!CarryOnDataManager.getCarryData(p).isCarrying(),"Carried source retained");
                h.assertTrue(hold.take(0,p)==null,"Removal failed");p.getInventory().clearContent();
            }
        } finally {config.settings.useWhitelistBlocks=old;config.whitelist.allowedBlocks=allowed;ListHandler.initConfigLists();}h.succeed();
    }
}
