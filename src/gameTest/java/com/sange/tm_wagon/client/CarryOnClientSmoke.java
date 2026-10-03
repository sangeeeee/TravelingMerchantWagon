package com.sange.tm_wagon.client;

import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import tschipp.carryon.common.carry.CarryOnDataManager;
import tschipp.carryon.client.keybinds.CarryOnKeybinds;

/** Actual client packets/attachments and the mod's unbound/remapped key handling. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class CarryOnClientSmoke {
    private static boolean opened;
    private static int ticks,loading;
    private static volatile String failure;
    private static WagonEntity wagon;
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        if(!Boolean.getBoolean("tm_wagon.carryOnClientSmoke"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();
        if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
            opened=true;mc.createWorldOpenFlows().openWorld("repro",()->{throw new IllegalStateException("No Carry On test world");});return;
        }
        if(failure!=null)throw new IllegalStateException(failure);
        if(mc.player==null||mc.level==null||mc.screen!=null){if(++loading>1600)throw new IllegalStateException("Carry On client loading timeout");return;}
        ticks++;
        if(ticks==1)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var level=p.serverLevel();
            var initial=CarryOnDataManager.getCarryData(p);initial.clear();CarryOnDataManager.setCarryData(p,initial);
            p.stopRiding();p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();p.getAbilities().flying=true;p.onUpdateAbilities();
            for(var entity:level.getEntitiesOfClass(WagonEntity.class,new net.minecraft.world.phys.AABB(-10,75,-10,10,95,10)))entity.discard();
            for(var at:BlockPos.betweenClosed(-6,80,-6,6,80,6))level.setBlock(at,Blocks.STONE.defaultBlockState(),3);
            wagon=WagonContent.WAGON.get().create(level);wagon.configure(WagonEntity.defaultParts(),Direction.NORTH);wagon.setPos(.5,81,.5);level.addFreshEntity(wagon);
            var point=wagon.pose().point(wagon.cargo().centreAt(0).add(-1.1,.1,0));p.teleportTo(point.x,point.y,point.z);
            require(wagon.cargo().place(0,new ItemStack(Items.CHEST),p)==null,"Setup chest failed");wagon.cargo().entry(0).inventory.setItem(26,new ItemStack(Items.DIAMOND,17));
        });
        if(ticks==20){CarryOnKeybinds.carryKey.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);mc.options.keyShift.setDown(true);}
        if(ticks==26)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();require(CarryOnDataManager.getCarryData(p).isKeyPressed(),"Default sneak key not synced");
            var point=wagon.pose().point(wagon.cargo().centreAt(0).add(-1.1,.1,0));p.teleportTo(point.x,point.y,point.z);
            wagon.cargo().interact(p,InteractionHand.MAIN_HAND,wagon.cargo().centreAt(0).add(0,.68,0));
            require(CarryOnDataManager.getCarryData(p).isCarrying(),"Server pickup rejected: remaining="+wagon.cargo().entry(0)+", hand="+p.getMainHandItem()+", tick="+p.tickCount+", carryTick="+CarryOnDataManager.getCarryData(p).getTick());
        });
        if(ticks==35){
            mc.options.keyShift.setDown(false);var data=CarryOnDataManager.getCarryData(mc.player);
            require(data.isCarrying(),"Carried block did not reach client");
            var be=data.getBlockEntity(BlockPos.ZERO,mc.level.registryAccess());require(((net.minecraft.world.Container)be).getItem(26).getCount()==17,"Client inventory NBT missing");
            server(mc,()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var d=CarryOnDataManager.getCarryData(p);d.clear();CarryOnDataManager.setCarryData(p,d);
                require(wagon.cargo().place(0,new ItemStack(Items.BARREL),p)==null,"Setup barrel failed");});
        }
        if(ticks==45){CarryOnKeybinds.carryKey.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT));CarryOnKeybinds.carryKey.setDown(true);}
        if(ticks==51)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();require(!p.isShiftKeyDown()&&CarryOnDataManager.getCarryData(p).isKeyPressed(),"Remapped key not independent of sneak");
            var point=wagon.pose().point(wagon.cargo().centreAt(0).add(-1.1,.1,0));p.teleportTo(point.x,point.y,point.z);
            wagon.cargo().interact(p,InteractionHand.MAIN_HAND,wagon.cargo().centreAt(0).add(0,.68,0));
            require(CarryOnDataManager.getCarryData(p).isCarrying(),"Server pickup rejected: remaining="+wagon.cargo().entry(0)+", hand="+p.getMainHandItem()+", tick="+p.tickCount+", carryTick="+CarryOnDataManager.getCarryData(p).getTick());
        });
        if(ticks==65){require(CarryOnDataManager.getCarryData(mc.player).isCarrying(),"Remapped key did not pick up barrel");CarryOnKeybinds.carryKey.setDown(false);
            com.mojang.logging.LogUtils.getLogger().info("CARRYON_CLIENT_PASS: native default/remapped keys, carried block synchronization and inventory");mc.stop();}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void server(Minecraft mc,Runnable action){mc.getSingleplayerServer().execute(()->{try{action.run();}catch(Throwable e){failure=e.toString();com.mojang.logging.LogUtils.getLogger().error("Carry On client test failed",e);}});}
}
