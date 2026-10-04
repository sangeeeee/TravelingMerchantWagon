package com.sange.tm_wagon.client;

import com.mojang.logging.LogUtils;
import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Native key binding -> drive payload -> server physics -> client visuals/audio. Disposable world only. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class DrivingClientSmoke {
    private static boolean opened;
    private static int ticks,loading,id;
    private static volatile String failure;
    private static double maximumSway;
    private static Object sound;
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        if(!Boolean.getBoolean("tm_wagon.drivingSmokeTest"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();
        if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
            opened=true;mc.createWorldOpenFlows().openWorld("repro",()->{throw new IllegalStateException("Cannot open driving fixture");});return;
        }
        if(failure!=null)throw new IllegalStateException(failure);
        if(mc.player==null||mc.level==null||mc.screen!=null) {
            if(++loading>1600)throw new IllegalStateException("Driving fixture loading timed out");return;
        }
        mc.setWindowActive(true);ticks++;
        mc.options.keyUp.setDown(ticks>=40&&ticks<120||ticks>=200&&ticks<220);
        mc.options.keySprint.setDown(ticks==80);
        mc.options.keyDown.setDown(ticks>=180&&ticks<220);
        if(ticks==1)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var level=p.serverLevel();p.stopRiding();
            if(p.isSleeping())p.stopSleepInBed(true,true);
            for(var e:level.getEntities(p,new AABB(-12,78,-90,12,95,20)))e.discard();
            for(int x=-8;x<=8;x++)for(int z=-85;z<=16;z++) {
                level.setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),3);
                for(int y=81;y<89;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
            }
            p.setGameMode(GameType.CREATIVE);p.teleportTo(.5,81,10.5);
            var w=WagonContent.WAGON.get().create(level);w.configure(WagonEntity.defaultParts(),Direction.NORTH);
            w.setPos(.5,81,10.5);level.addFreshEntity(w);id=w.getId();require(p.startRiding(w),"Driver failed to board");
            var horse=EntityType.HORSE.create(level);horse.setNoAi(true);horse.setPos(w.horsePosition(0));level.addFreshEntity(horse);horse.setLeashedTo(p,true);
            require(w.attachHorse(p,horse,0)==null,"Horse fixture failed");
        });
        if(ticks==39)require(mc.player.getVehicle() instanceof WagonEntity w&&w.driver()==mc.player,"Driver did not synchronize");
        if(ticks==60)checkSpeed(mc,.10,.20,false);
        if(ticks==78)checkSpeed(mc,.23,.24,false);
        if(ticks==115) {
            checkSpeed(mc,.345,.355,true);
            sound=rollingSound();require(sound!=null,"Rolling loop was never created");
            require(mc.getSoundManager().isActive((net.minecraft.client.resources.sounds.SoundInstance)sound),"Rolling loop is not audible/decoded");
        }
        if(ticks>50&&ticks<120&&mc.level.getEntity(id) instanceof WagonEntity w) {
            maximumSway=Math.max(maximumSway,Math.abs(WagonEffects.sway(w,.5F)));
            require(Math.abs(WagonEffects.sway(w,.5F))<Math.toRadians(.66),"Visual sway exceeded its bound");
        }
        if(ticks==119)require(sound==rollingSound(),"Rolling loop restarted during continuous motion");
        if(ticks==130)checkSpeed(mc,.20,.31,false);
        if(ticks==175) {
            checkSpeed(mc,0,.0001,false);
            require(maximumSway>.001,"Moving wagon never swayed");
        }
        if(ticks==210)server(mc,()->{
            var w=(WagonEntity)mc.getSingleplayerServer().overworld().getEntity(id);
            require(w.getDeltaMovement().z>.058&&w.getDeltaMovement().z<.059,"Reverse speed/sign incorrect");
        });
        if(ticks==265) {
            require(!mc.getSoundManager().isActive((net.minecraft.client.resources.sounds.SoundInstance)rollingSound()),"Parked wagon kept playing audio");
            LogUtils.getLogger().info("DRIVING_CLIENT_PASS: native sprint tap, 1.5x latch, acceleration/coast/reverse, bounded cosmetic sway, decoded stable rolling loop and stop");mc.stop();
        }
    }
    private static Object rollingSound() {
        try {
            var f=WagonEffects.class.getDeclaredField("states");f.setAccessible(true);
            var states=(java.util.Map<?,?>)f.get(null);var w=Minecraft.getInstance().level.getEntity(id);
            var state=states.get(w.getUUID());if(state==null)return null;
            var s=state.getClass().getDeclaredField("sound");s.setAccessible(true);return s.get(state);
        }catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static void checkSpeed(Minecraft mc,double min,double max,boolean boosted) {
        server(mc,()->{
            var w=(WagonEntity)mc.getSingleplayerServer().overworld().getEntity(id);double speed=w.getDeltaMovement().horizontalDistance();
            require(speed>=min&&speed<=max&&w.boostedDrive()==boosted,"Speed/boost out of range: "+speed+" boost="+w.boostedDrive()+" expected="+min+".."+max);
        });
    }
    private static void server(Minecraft mc,Runnable task) { mc.getSingleplayerServer().execute(()->{try { task.run(); }catch(Throwable error) { failure=error.toString(); }}); }
    private static void require(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }
}
