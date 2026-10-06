package com.sange.tm_wagon;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.cargo.*;
import com.sange.tm_wagon.compat.BackpackCompat;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.*;
import tschipp.carryon.common.carry.*;

/** Exercise the native event/block/item order, rather than calling installation directly. */
@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class InteractionGameTests {
    static ServerPlayer player(GameTestHelper h) {
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"wagon-interact"));
        p.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        p.getInventory().clearContent();p.setShiftKeyDown(false);p.tickCount=100;
        return p;
    }
    static AssemblyFrameBlockEntity body(GameTestHelper h,WagonPart body,Direction facing) {
        var f=PortGameTests.frame(h);
        h.getLevel().setBlock(f.getBlockPos(),f.getBlockState().setValue(AssemblyFrameBlock.FACING,facing),3);f.initializeFrame();
        h.assertTrue(f.install(WagonSlot.BODY,body,null,new ItemStack(WagonContent.PART_ITEMS.get(body).get()))==null,"Body fixture failed");return f;
    }
    static void look(ServerPlayer p,Vec3 point,Vec3 position) {
        p.setPos(position);var delta=point.subtract(p.getEyePosition());
        p.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
        p.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));
    }
    static InteractionResult click(GameTestHelper h,AssemblyFrameBlockEntity f,ServerPlayer p,Vec3 local,Direction face) {
        var point=f.cargoPose().point(local);var pos=BlockPos.containing(point);
        h.assertTrue(AssemblyFrameBlockEntity.find(h.getLevel(),pos)==f,"Hit has no proxy: "+local);
        return p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(point,face,pos,false));
    }
    static void carryKey(ServerPlayer p,boolean down) {
        var data=CarryOnDataManager.getCarryData(p);data.setKeyPressed(down);CarryOnDataManager.setCarryData(p,data);p.tickCount+=2;
    }

    @GameTest(template="assembly_test")
    public static void every_required_component_uses_native_item_placement(GameTestHelper h) {
        var p=player(h);
        for(var facing:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST})for(var size:new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY}) {
            var f=body(h,size,facing);
            for(var slot:WagonSlot.values())if(slot!=WagonSlot.BODY) {
                var part=slot==WagonSlot.SEAT?WagonPart.DOUBLE_SEAT:slot==WagonSlot.SHAFTS?WagonPart.DOUBLE_HORSE_SHAFTS:slot.accepts(WagonPart.SMALL_WHEEL)?WagonPart.SMALL_WHEEL:WagonPart.LARGE_WHEEL;
                var target=slot.position(f.getBlockPos(),facing,size);var at=target;var face=Direction.UP;
                if(AssemblyFrameBlockEntity.find(h.getLevel(),at)!=f) {
                    for(var direction:Direction.values())if(AssemblyFrameBlockEntity.find(h.getLevel(),target.relative(direction.getOpposite()))==f) {at=target.relative(direction.getOpposite());face=direction;break;}
                }
                h.assertTrue(AssemblyFrameBlockEntity.find(h.getLevel(),at)==f,"No attachment face: "+slot+" / "+size);
                var point=Vec3.atCenterOf(at).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
                look(p,point,point.add(0,2,3));p.setShiftKeyDown(false);
                var stack=new ItemStack(WagonContent.PART_ITEMS.get(part).get(),2);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var result=p.gameMode.useItemOn(p,h.getLevel(),stack,InteractionHand.MAIN_HAND,new BlockHitResult(point,face,at,false));
                h.assertTrue(result.consumesAction()&&f.part(slot)==part,"Placement intercepted: "+slot+" / "+size+" / "+facing);
                h.assertTrue(stack.getCount()==1&&!f.switching(),"Component click consumed twice or toggled lift");
            }f.dismantle(false,true);
        }h.succeed();
    }

    @GameTest(template="assembly_test")
    public static void block_cargo_native_place_open_and_unload(GameTestHelper h) {
        var f=body(h,WagonPart.CARGO_BODY,Direction.NORTH);var p=player(h);var hold=f.cargo();
        var local=hold.centreAt(0).add(0,.001,0);look(p,f.cargoPose().point(local),f.cargoPose().point(local.add(0,1,2)));
        var stack=new ItemStack(Items.CHEST,2);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        click(h,f,p,local,Direction.UP);
        h.assertTrue(hold.entry(0)!=null&&stack.getCount()==1&&!f.switching(),"Cargo placement fell through to world placement/lift");
        hold.entry(0).inventory.setItem(1,new ItemStack(Items.DIAMOND,7));p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
        local=hold.centreAt(0).add(0,.68,0);look(p,f.cargoPose().point(local),f.cargoPose().point(local.add(0,1,2)));
        click(h,f,p,local,Direction.UP);
        h.assertTrue(p.containerMenu!=p.inventoryMenu,"Held item blocked cargo menu");p.closeContainer();
        p.setShiftKeyDown(true);click(h,f,p,local,Direction.UP);
        h.assertTrue(hold.entry(0)==null&&p.getMainHandItem().is(Items.STICK),"Sneaking item removal fell through or replaced held item");h.succeed();
    }

    @GameTest(template="assembly_test")
    public static void optional_components_and_tailgate_keep_native_gestures(GameTestHelper h) {
        var f=body(h,WagonPart.CARGO_BODY,Direction.NORTH);var p=player(h);var hold=f.cargo();
        var wall=WagonGeometry.partBoxes(f.cargoBody()).stream().filter(b->b.minX>0&&b.maxY>1.8).findFirst().orElseThrow();
        var side=new Vec3(wall.maxX,1.9,0);look(p,f.cargoPose().point(side),f.cargoPose().point(side.add(2,0,0)));
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(WagonContent.CARGO_COVER.get()));click(h,f,p,side,Direction.EAST);
        h.assertTrue(hold.cover().installed(),"Cover installation intercepted");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));p.setShiftKeyDown(true);click(h,f,p,side,Direction.EAST);
        h.assertTrue(!hold.cover().installed(),"Held item blocked cover removal");p.setShiftKeyDown(false);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(WagonContent.CANOPY.get()));click(h,f,p,side,Direction.EAST);
        h.assertTrue(hold.canopy().installed(),"Canopy installation intercepted");
        p.setShiftKeyDown(true);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));click(h,f,p,side,Direction.EAST);
        h.assertTrue(!hold.canopy().installed(),"Canopy removal intercepted");p.setShiftKeyDown(false);
        h.assertTrue(f.install(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT,null,new ItemStack(WagonContent.PART_ITEMS.get(WagonPart.DOUBLE_SEAT).get()))==null,"Seat fixture failed");
        var b=hold.cabinet().box();var cabinetSide=new Vec3(b.maxX,1.7,b.getCenter().z);
        look(p,f.cargoPose().point(cabinetSide),f.cargoPose().point(cabinetSide.add(2,0,0)));
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(WagonContent.CABINET.get()));click(h,f,p,cabinetSide,Direction.EAST);
        h.assertTrue(hold.cabinet().installed(),"Cabinet installation intercepted");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));click(h,f,p,cabinetSide,Direction.EAST);
        h.assertTrue(p.containerMenu!=p.inventoryMenu,"Cabinet open intercepted");p.closeContainer();p.setShiftKeyDown(true);click(h,f,p,cabinetSide,Direction.EAST);
        h.assertTrue(!hold.cabinet().installed(),"Cabinet removal intercepted");
        var gate=hold.tailBox().getCenter();look(p,f.cargoPose().point(gate),f.cargoPose().point(gate.add(0,0,2)));p.setShiftKeyDown(false);
        click(h,f,p,gate,Direction.SOUTH);h.assertTrue(hold.gateMoving()&&!f.switching(),"Tailgate click intercepted/toggled assembly lift");h.succeed();
    }

    @GameTest(template="assembly_test")
    public static void carryon_native_block_event_owns_cargo_before_world_handler(GameTestHelper h) {
        if(!ModList.get().isLoaded("carryon")){h.succeed();return;}
        var f=body(h,WagonPart.CARGO_BODY,Direction.NORTH);var hold=f.cargo();var p=player(h);
        var be=((net.minecraft.world.level.block.EntityBlock)Blocks.CHEST).newBlockEntity(BlockPos.ZERO,Blocks.CHEST.defaultBlockState());be.setLevel(h.getLevel());((net.minecraft.world.Container)be).setItem(3,new ItemStack(Items.DIAMOND,17));
        var data=CarryOnDataManager.getCarryData(p);data.setBlock(Blocks.CHEST.defaultBlockState(),be);CarryOnDataManager.setCarryData(p,data);p.tickCount+=2;
        var local=hold.centreAt(0).add(0,.001,0);look(p,f.cargoPose().point(local),f.cargoPose().point(local.add(0,1,2)));
        click(h,f,p,local,Direction.UP);
        h.assertTrue(!CarryOnDataManager.getCarryData(p).isCarrying()&&hold.entry(0)!=null&&hold.entry(0).inventory.getItem(3).getCount()==17,"Carry On placed world block or lost cargo data");
        p.tickCount+=2;carryKey(p,true);p.setShiftKeyDown(true);local=hold.centreAt(0).add(0,.68,0);look(p,f.cargoPose().point(local),f.cargoPose().point(local.add(-1,0,0)));
        click(h,f,p,local,Direction.UP);
        data=CarryOnDataManager.getCarryData(p);h.assertTrue(data.isCarrying()&&hold.entry(0)==null,"Cargo unload consumed Carry On gesture");
        h.assertTrue(((net.minecraft.world.Container)data.getBlockEntity(BlockPos.ZERO)).getItem(3).getCount()==17,"Carried inventory lost");
        data.clear();CarryOnDataManager.setCarryData(p,data);p.tickCount+=2;h.assertTrue(!PickupHandler.tryPickUpBlock(p,f.getBlockPos(),h.getLevel(),(s,at)->true),"Frame picked up");
        for(var at:f.layout().keySet())h.assertTrue(!PickupHandler.tryPickUpBlock(p,at,h.getLevel(),(s,pos)->true),"Proxy picked up");
        var w=PortGameTests.wagon(h,WagonPart.CARGO_BODY);h.assertTrue(!PickupHandler.tryPickupEntity(p,w,e->true),"Whole wagon picked up");h.succeed();
    }

    @GameTest(template="assembly_test")
    public static void backpack_gestures_use_native_block_events(GameTestHelper h) {
        var f=body(h,WagonPart.CARGO_BODY,Direction.NORTH);var hold=f.cargo();var p=player(h);
        for(String id:new String[]{"travelersbackpack:standard","sophisticatedbackpacks:backpack"}) {
            var stack=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(id)));
            if(!ModList.get().isLoaded(id.substring(0,id.indexOf(':'))))continue;
            h.assertTrue(BackpackCompat.matches(stack),"Loaded backpack test item was not recognized: "+id);
            var local=hold.centreAt(0).add(0,.001,0);look(p,f.cargoPose().point(local),f.cargoPose().point(local.add(0,1,2)));
            p.setShiftKeyDown(true);p.setItemInHand(InteractionHand.MAIN_HAND,stack);click(h,f,p,local,Direction.UP);
            h.assertTrue(hold.entry(0)!=null&&stack.isEmpty(),"Sneak backpack placement fell through: "+id);
            p.setShiftKeyDown(false);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
            local=hold.centreAt(0).add(0,.3,0);look(p,f.cargoPose().point(local),f.cargoPose().point(local.add(0,1,2)));click(h,f,p,local,Direction.UP);
            h.assertTrue(p.containerMenu!=p.inventoryMenu,"Cargo backpack cannot open: "+id);p.closeContainer();
            p.setShiftKeyDown(true);click(h,f,p,local,Direction.UP);
            h.assertTrue(hold.entry(0)==null&&p.getMainHandItem().is(Items.STICK),"Backpack unload replaced held item: "+id);
        }h.succeed();
    }

    @GameTest(template="assembly_test")
    public static void carryon_custom_key_policy_and_cloth_fallback(GameTestHelper h) {
        if(!ModList.get().isLoaded("carryon")){h.succeed();return;}
        var f=body(h,WagonPart.CARGO_BODY,Direction.NORTH);var hold=f.cargo();var p=player(h);
        var local=hold.centreAt(0).add(0,.68,0);look(p,f.cargoPose().point(local),f.cargoPose().point(local.add(-1,0,0)));
        h.assertTrue(hold.place(0,new ItemStack(Items.BARREL),p)==null,"Barrel setup failed");
        carryKey(p,true);p.setShiftKeyDown(false);click(h,f,p,local,Direction.UP);
        h.assertTrue(CarryOnDataManager.getCarryData(p).isCarrying()&&hold.entry(0)==null,"Custom carry key without sneak did not pick up");
        var data=CarryOnDataManager.getCarryData(p);data.clear();CarryOnDataManager.setCarryData(p,data);p.tickCount+=2;
        var config=tschipp.carryon.Constants.COMMON_CONFIG;var old=config.blacklist.forbiddenTiles;
        try {
            config.blacklist.forbiddenTiles=new String[]{"minecraft:barrel"};tschipp.carryon.common.config.ListHandler.initConfigLists();
            h.assertTrue(hold.place(0,new ItemStack(Items.BARREL),p)==null,"Denied barrel setup failed");
            carryKey(p,true);p.setShiftKeyDown(true);click(h,f,p,local,Direction.UP);
            h.assertTrue(!CarryOnDataManager.getCarryData(p).isCarrying()&&hold.entry(0)==null,"Policy rejection did not fall back to unload");
        }finally {config.blacklist.forbiddenTiles=old;tschipp.carryon.common.config.ListHandler.initConfigLists();}
        var saved=hold.save(h.getLevel().registryAccess(),false);var cover=new net.minecraft.nbt.CompoundTag();cover.putBoolean("Installed",true);cover.putInt("OpenRows",hold.rows());saved.put("Cover",cover);hold.load(saved,h.getLevel().registryAccess());
        h.assertTrue(f.cargoGeometryChanged()==null,"Cover geometry fixture failed");
        var roll=hold.cover().rollBox(f.cargoBody());local=new Vec3(0,roll.maxY,roll.getCenter().z);
        look(p,f.cargoPose().point(local),f.cargoPose().point(local.add(0,1,-1)));carryKey(p,true);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setShiftKeyDown(true);
        click(h,f,p,local,Direction.UP);
        h.assertTrue(hold.cover().openRows()==hold.rows()-1&&!CarryOnDataManager.getCarryData(p).isCarrying(),"Carry key intercepted cloth gesture");h.succeed();
    }

    @GameTest(template="assembly_test")
    public static void maid_release_has_priority_over_cart_interaction(GameTestHelper h) {
        if(!ModList.get().isLoaded("touhou_little_maid")){h.succeed();return;}
        var f=body(h,WagonPart.CARGO_BODY,Direction.NORTH);var hold=f.cargo();var p=player(h);
        var maid=com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.TYPE.create(h.getLevel());maid.setOwnerUUID(p.getUUID());
        var stored=com.github.tartaricacid.touhoulittlemaid.init.InitItems.SMART_SLAB_HAS_MAID.get().getDefaultInstance();
        com.github.tartaricacid.touhoulittlemaid.item.ItemSmartSlab.storeMaidData(stored,maid);var id=maid.getUUID();
        var local=hold.centreAt(0).add(0,.001,0);look(p,f.cargoPose().point(local),f.cargoPose().point(local.add(0,1,2)));p.setItemInHand(InteractionHand.MAIN_HAND,stored);
        click(h,f,p,local,Direction.UP);
        h.assertTrue(h.getLevel().getEntity(id) instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid&&p.getMainHandItem().is(com.github.tartaricacid.touhoulittlemaid.init.InitItems.SMART_SLAB_EMPTY.get()),"Cart consumed filled slab without releasing maid");
        var live=h.getLevel().getEntity(id);live.discard();h.succeed();
    }
}
