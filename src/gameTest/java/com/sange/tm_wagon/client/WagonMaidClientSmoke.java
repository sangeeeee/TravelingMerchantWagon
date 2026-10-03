package com.sange.tm_wagon.client;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.cargo.StrawMatSleep;
import com.sange.tm_wagon.compat.maid.WagonMaidExtension;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Optional real-client check of native maid rendering and the shared one-shot mat session packets. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class WagonMaidClientSmoke {
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        if(Boolean.getBoolean("tm_wagon.maidClientSmoke"))Scenario.tick(event);
    }
    private static final class Scenario {

    private static boolean opened;
    private static int ticks,loading;
    private static volatile String failure;
    private static WagonEntity wagon;
    private static EntityMaid maid;
    public static void tick(ClientTickEvent.Pre event) {
        if(!Boolean.getBoolean("tm_wagon.maidClientSmoke"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();
        if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
            opened=true;mc.createWorldOpenFlows().openWorld("repro",()->{throw new IllegalStateException("No maid test world");});return;
        }
        if(failure!=null)throw new IllegalStateException(failure);
        if(mc.player==null||mc.level==null||mc.screen!=null) { if(++loading>1600)throw new IllegalStateException("Maid client loading timeout");return; }
        ticks++;
        if(ticks==1)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var level=p.serverLevel();
            p.stopRiding();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(5,84,-4);
            var area=new net.minecraft.world.phys.AABB(-10,75,-10,10,95,10);
            for(var w:level.getEntitiesOfClass(WagonEntity.class,area))w.discard();
            for(var m:level.getEntitiesOfClass(EntityMaid.class,area))m.discard();
            for(var at:BlockPos.betweenClosed(-8,81,-8,8,91,8))level.setBlock(at,Blocks.AIR.defaultBlockState(),3);
            for(var at:BlockPos.betweenClosed(-8,80,-8,8,80,8))level.setBlock(at,Blocks.STONE.defaultBlockState(),3);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.setDayTime(1000);
            wagon=WagonContent.WAGON.get().create(level);var parts=WagonEntity.defaultParts();parts.put(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);
            wagon.configure(parts,Direction.NORTH);wagon.setPos(.5,81,.5);level.addFreshEntity(wagon);
            level.setBlock(wagon.blockPosition(),WagonContent.FRAME.get().defaultBlockState(),3);
            var atMat=wagon.pose().point(wagon.cargo().centreAt(8).add(-2,1,0));p.teleportTo(atMat.x,atMat.y,atMat.z);
            require(wagon.cargo().place(8,new ItemStack(WagonContent.STRAW_MAT.get()),p)==null,"Mat setup failed");
            var tag=wagon.cargo().save(level.registryAccess(),false);var cover=new net.minecraft.nbt.CompoundTag();cover.putBoolean("Installed",true);tag.put("Cover",cover);
            wagon.cargo().load(tag,level.registryAccess());wagon.cargoGeometryChanged();
            maid=EntityMaid.TYPE.create(level);maid.setPos(3,81,-1);maid.tame(p);maid.setHomeModeEnable(false);
            maid.setTask(TaskManager.findTask(WagonMaidExtension.TASK).orElseThrow());maid.setSchedule(MaidSchedule.DAY);level.addFreshEntity(maid);
        });
        if(ticks==45) {
            require(mc.level.getEntity(maid.getId()) instanceof EntityMaid m&&m.getVehicle() instanceof WagonEntity w&&w.passengerSeat(m)==1,"Client did not observe companion boarding");
            server(mc,()->((net.minecraft.server.level.ServerLevel)maid.level()).setDayTime(17000));
        }
        if(ticks==80) {
            require(mc.level.getEntity(maid.getId()) instanceof EntityMaid m&&m.isSleeping()&&StrawMatSleep.sleepingPose(m)!=null,"Client maid sleep/session was not synchronized");
            server(mc,()->{
                require(maid.isSleeping(),"Server maid did not stay asleep");maid.setNoAi(true);maid.stopSleeping();
                require(!maid.isSleeping()&&maid.getPose()==Pose.STANDING&&maid.getY()>wagon.getY()+2.25,"Covered mat wake did not stand above cloth");
                require(maid.level().noCollision(maid,maid.getBoundingBox().deflate(.0001)),"Maid wake position obstructed");
            });
        }
        if(ticks==100) {
            require(mc.level.getEntity(maid.getId()) instanceof EntityMaid m&&!m.isSleeping()&&StrawMatSleep.sleepingPose(m)==null,"Client maid wake/session was not cleared");
            com.mojang.logging.LogUtils.getLogger().info("MAID_CLIENT_PASS: autonomous boarding, rendered mat sleep/session sync and safe covered-mat wake");mc.stop();
        }
    }
    private static void server(Minecraft mc,Runnable action) {
        mc.getSingleplayerServer().execute(()->{try { action.run(); }catch(Throwable error) { failure=error.toString(); }});
    }
    private static void require(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }
    }
}
