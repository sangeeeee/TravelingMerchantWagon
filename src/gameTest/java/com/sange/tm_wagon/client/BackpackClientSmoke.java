package com.sange.tm_wagon.client;

import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.cargo.*;
import com.sange.tm_wagon.compat.BackpackCompat;
import com.sange.tm_wagon.compat.backpack.TravelerCargo;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Exercises the real native screens and client/server slot and settings packets. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class BackpackClientSmoke {
    private static boolean opened;
    private static int ticks,loading;
    private static volatile String failure;
    private static WagonEntity wagon;
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        if(!Boolean.getBoolean("tm_wagon.backpackClientSmoke"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();
        if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
            opened=true;mc.createWorldOpenFlows().openWorld("repro",()->{throw new IllegalStateException("No backpack test world");});return;
        }
        if(failure!=null)throw new IllegalStateException(failure);
        if(mc.player==null||mc.level==null){if(++loading>1600)throw new IllegalStateException("Backpack client loading timeout");return;}
        if(ticks==0&&mc.screen!=null)return;
        ticks++;
        if(ticks==1)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var level=p.serverLevel();
            p.stopRiding();p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getAbilities().flying=true;p.getAbilities().mayfly=true;p.onUpdateAbilities();
            for(var entity:level.getEntitiesOfClass(WagonEntity.class,new net.minecraft.world.phys.AABB(-10,75,-10,10,95,10)))entity.discard();
            for(var at:BlockPos.betweenClosed(-6,80,-6,6,80,6))level.setBlock(at,Blocks.STONE.defaultBlockState(),3);
            wagon=WagonContent.WAGON.get().create(level);wagon.configure(WagonEntity.defaultParts(),Direction.NORTH);wagon.setPos(.5,81,.5);level.addFreshEntity(wagon);
            var point=wagon.pose().point(wagon.cargo().centreAt(0).add(-1.1,0,0));p.teleportTo(point.x,point.y,point.z);
            require(wagon.cargo().place(0,BackpackGameTests.filled(level,"travelersbackpack:standard"),p)==null,"Traveler setup failed");
            var bag=BackpackGameTests.filled(level,"sophisticatedbackpacks:backpack");
            var storage=net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper.fromStack(bag).getContentsUuid().orElseThrow();
            require(wagon.cargo().place(2,bag,p)==null,"Sophisticated setup failed");
            require(storage.equals(net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper.fromStack(wagon.cargo().entry(2).item).getContentsUuid().orElseThrow()),"Survival placement duplicated native storage ID");
            if(net.neoforged.fml.ModList.get().isLoaded("carryon")){var data=tschipp.carryon.common.carry.CarryOnDataManager.getCarryData(p);data.clear();data.setKeyPressed(false);}
        });
        if(ticks==20)server(mc,()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();BackpackCompat.open(wagon.cargo(),wagon.cargo().entry(0),p);});
        if(ticks==40) {
            require(mc.screen instanceof com.tiviacz.travelersbackpack.client.screens.BackpackScreen&&mc.player.containerMenu instanceof TravelerCargo.Menu,"Traveler native screen missing: "+mc.screen);
            var menu=(TravelerCargo.Menu)mc.player.containerMenu;require(menu.getWrapper().getStorage().getStackInSlot(0).getCount()==17,"Traveler initial contents missing");
            mc.gameMode.handleInventoryMouseClick(menu.containerId,0,0,ClickType.PICKUP,mc.player);
        }
        if(ticks==50) {
            require(mc.player.containerMenu.getCarried().getCount()==17,"Traveler slot pickup packet failed");
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,0,0,ClickType.PICKUP,mc.player);
        }
        if(ticks==60)travelerSettings(mc,true);
        if(ticks==75) {
            require(mc.screen instanceof com.tiviacz.travelersbackpack.client.screens.BackpackSettingsScreen&&mc.player.containerMenu instanceof TravelerCargo.Settings,"Traveler settings screen failed");
            travelerSettings(mc,false);
        }
        if(ticks==90) {
            require(mc.player.containerMenu instanceof TravelerCargo.Menu,"Traveler settings return failed");
            server(mc,()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();require(BackpackGameTests.contents(p.serverLevel(),wagon.cargo().entry(0).item)==17,"Traveler GUI lost/duplicated storage");BackpackCompat.open(wagon.cargo(),wagon.cargo().entry(2),p);});
        }
        if(ticks==110) {
            require(mc.screen instanceof net.p3pp3rf1y.sophisticatedbackpacks.client.gui.BackpackScreen,"Sophisticated native screen missing: "+mc.screen);
            require(wagon.cargo().entry(2).id.equals(BackpackSessions.id(mc.player.containerMenu)),"Sophisticated client cargo context missing");
            require(mc.player.containerMenu.getSlot(0).getItem().getCount()==17,"Sophisticated initial storage missing");
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,0,0,ClickType.PICKUP,mc.player);
        }
        if(ticks==125) {
            require(mc.player.containerMenu.getCarried().getCount()==17,"Sophisticated pickup packet failed");
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,0,0,ClickType.PICKUP,mc.player);
        }
        if(ticks==140)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            ((net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer)p.containerMenu).openSettings();
        });
        if(ticks==155) {
            require(mc.player.containerMenu instanceof net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackSettingsContainerMenu,"Sophisticated settings failed");
            PacketDistributor.sendToServer(new net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackOpenPayload());
        }
        if(ticks==170) {
            require(mc.player.containerMenu instanceof net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer,"Sophisticated settings return failed");
            server(mc,()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                require(BackpackGameTests.contents(p.serverLevel(),wagon.cargo().entry(2).item)==17,"Sophisticated GUI lost/duplicated storage");
                require(wagon.cargo().take(2,p)==null,"Unload failed");require(p.containerMenu==p.inventoryMenu,"Removed backpack menu remains open");
            });
        }
        if(ticks==190) {
            require(mc.player.containerMenu==mc.player.inventoryMenu&&BackpackSessions.stack(mc.player,wagon.cargo().entry(2)==null?java.util.UUID.randomUUID():wagon.cargo().entry(2).id).isEmpty(),"Removed backpack remains active");
            com.mojang.logging.LogUtils.getLogger().info("BACKPACK_CLIENT_PASS: native storage/settings screens, real slot/settings packets, persistence and close-on-unload");mc.stop();
        }
    }
    private static void travelerSettings(Minecraft mc,boolean open) {
        var tag=new CompoundTag();tag.putInt("ActionType",10);tag.putInt("Arg0",mc.player.getId());tag.putBoolean("Arg1",open);
        PacketDistributor.sendToServer(new com.tiviacz.travelersbackpack.network.ServerboundActionTagPacket(tag));
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void server(Minecraft mc,Runnable action){mc.getSingleplayerServer().execute(()->{try{action.run();}catch(Throwable e){failure=e.toString();com.mojang.logging.LogUtils.getLogger().error("Backpack client test failed",e);}});}
}
