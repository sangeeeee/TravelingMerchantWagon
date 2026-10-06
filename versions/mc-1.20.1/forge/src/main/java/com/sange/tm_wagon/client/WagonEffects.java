package com.sange.tm_wagon.client;

import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.WagonPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid=TravelingMerchantWagon.MODID,value=Dist.CLIENT)
public final class WagonEffects {
    private static final Map<UUID,State> states=new HashMap<>();
    private static ClientLevel previousLevel;
    private static final class State {
        Vec3 position;double dustDistance,speed,phase;float sway,oldSway;Rolling sound;
        State(WagonEntity wagon) { position=wagon.position();phase=(wagon.getUUID().getLeastSignificantBits()&65535)*.01; }
        void advance(double distance) {
            speed=Mth.lerp(.35,speed,distance);
            if(speed<.0001)speed=0;
            double ratio=Math.min(1.5,speed/WagonPhysics.FORWARD_SPEED);
            phase+=.22+ratio*.5;
            oldSway=sway;
            // Incommensurate waves give gentle irregular motion without per-frame random jitter.
            double wave=.65*Math.sin(phase)+.35*Math.sin(phase*.637+1.7);
            float target=(float)(Math.toRadians(.65)*Math.pow(ratio/1.5,2)*wave);
            sway=Mth.lerp(.35F,sway,target);
        }
    }
    /** Rendering only: never changes seats, horse placement, camera or collision geometry. */
    public static float sway(WagonEntity wagon,float partialTick) {
        State state=states.get(wagon.getUUID());
        return state==null?0:Mth.lerp(partialTick,state.oldSway,state.sway);
    }
    private static final class Rolling extends AbstractTickableSoundInstance {
        private final WagonEntity wagon;
        private final State state;
        private int quietTicks;
        Rolling(WagonEntity wagon,State state) {
            super(WagonContent.ROLL.get(),SoundSource.NEUTRAL,RandomSource.create());this.wagon=wagon;this.state=state;
            looping=true;delay=0;volume=rollingVolume(state.speed);updatePosition();
        }
        private void updatePosition() { x=wagon.getX();y=wagon.getY()+.6;z=wagon.getZ(); }
        @Override public void tick() {
            if(wagon.isRemoved()) { stop();return; }
            // Short grace period and smoothed speed bridge individual interpolation/network gaps.
            if(state.speed<.001)quietTicks++;else quietTicks=0;
            if(quietTicks>=8) { stop();return; }
            updatePosition();volume=rollingVolume(state.speed);
            pitch=(float)(.78+.30*Math.min(1.5,state.speed/WagonPhysics.FORWARD_SPEED));
        }
    }
    private static float rollingVolume(double speed) {
        double ratio=Math.min(1.5,speed/WagonPhysics.FORWARD_SPEED);
        return (float)Math.min(1,Math.sqrt(ratio)*.82)*1.3F;
    }
    private static final class Dust extends TerrainParticle {
        Dust(ClientLevel level,Vec3 point,Vec3 trail,net.minecraft.world.level.block.state.BlockState state,BlockPos block) {
            super(level,point.x,point.y,point.z,trail.x,.025,trail.z,state,block);
            lifetime=6+random.nextInt(4);quadSize*=.45F;gravity=.3F;
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();
        if(previousLevel!=mc.level) { states.values().forEach(s->{if(s.sound!=null)mc.getSoundManager().stop(s.sound);});states.clear();previousLevel=mc.level; }
        if(mc.level==null||mc.isPaused())return;
        var live=new java.util.HashSet<UUID>();
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof WagonEntity wagon) {
            live.add(wagon.getUUID());State state=states.computeIfAbsent(wagon.getUUID(),id->new State(wagon));
            Vec3 delta=wagon.position().subtract(state.position);state.position=wagon.position();double distance=delta.horizontalDistance();
            boolean grounded=wagon.supportMask()!=0&&distance<=1;
            state.advance(grounded?distance:0);
            boolean audible=mc.player!=null&&mc.player.distanceToSqr(wagon)<48*48;
            if(!audible) {
                if(state.sound!=null) { mc.getSoundManager().stop(state.sound);state.sound=null; }
            } else if(state.speed>.002&&(state.sound==null||state.sound.isStopped())) {
                state.sound=new Rolling(wagon,state);mc.getSoundManager().play(state.sound);
            }
            if(!grounded||distance<.0001)continue;
            state.dustDistance+=distance;
            if(state.dustDistance<.18||mc.player==null||mc.player.distanceToSqr(wagon)>32*32)continue;
            state.dustDistance%=.18;
            for(int i=0;i<4;i++)if((wagon.supportMask()&(1<<i))!=0) {
                Vec3 contact=wagon.wheelCentre(i,wagon.pose()).add(0,-WagonPhysics.radius(i),0);
                var g=WagonPhysics.ground(mc.level,contact,.15,.3,.1);if(!g.present())continue;
                BlockPos block=BlockPos.containing(contact.x,g.height()-.01,contact.z);var material=mc.level.getBlockState(block);
                if(material.isAir()||material.getRenderShape()==net.minecraft.world.level.block.RenderShape.INVISIBLE)continue;
                Vec3 behind=delta.normalize().scale(-.15);
                mc.particleEngine.add(new Dust(mc.level,new Vec3(contact.x,g.height()+.04,contact.z).add(behind),behind.scale(.08),material,block));
            }
        }
        states.entrySet().removeIf(entry->{if(live.contains(entry.getKey()))return false;if(entry.getValue().sound!=null)mc.getSoundManager().stop(entry.getValue().sound);return true;});
    }
    private WagonEffects() {}
}
