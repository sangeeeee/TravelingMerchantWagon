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
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid=TravelingMerchantWagon.MODID,value=Dist.CLIENT)
public final class WagonEffects {
    private static final Map<UUID,State> states=new HashMap<>();
    private static ClientLevel previousLevel;
    private static final class State {
        Vec3 position;double dustDistance;Rolling sound;
        State(Vec3 p) { position=p; }
    }
    private static final class Rolling extends AbstractTickableSoundInstance {
        private final WagonEntity wagon;
        private Vec3 previous;
        Rolling(WagonEntity wagon) {
            super(WagonContent.ROLL.get(),SoundSource.NEUTRAL,RandomSource.create());this.wagon=wagon;
            looping=true;delay=0;volume=.35F;previous=wagon.position();x=wagon.getX();y=wagon.getY()+.6;z=wagon.getZ();
        }
        @Override public void tick() {
            double distance=wagon.position().subtract(previous).horizontalDistance();previous=wagon.position();
            if(wagon.isRemoved()||wagon.supportMask()==0||distance<.0001) { stop();return; }
            x=wagon.getX();y=wagon.getY()+.6;z=wagon.getZ();
            volume=(float)Math.min(.45,.08+distance*2);pitch=(float)Math.min(1.1,.8+distance);
        }
    }
    private static final class Dust extends TerrainParticle {
        Dust(ClientLevel level,Vec3 point,Vec3 trail,net.minecraft.world.level.block.state.BlockState state,BlockPos block) {
            super(level,point.x,point.y,point.z,trail.x,.025,trail.z,state,block);
            lifetime=6+random.nextInt(4);quadSize*=.45F;gravity=.3F;
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(previousLevel!=mc.level) { states.values().forEach(s->{if(s.sound!=null)mc.getSoundManager().stop(s.sound);});states.clear();previousLevel=mc.level; }
        if(mc.level==null||mc.isPaused())return;
        var live=new java.util.HashSet<UUID>();
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof WagonEntity wagon) {
            live.add(wagon.getUUID());State state=states.computeIfAbsent(wagon.getUUID(),id->new State(wagon.position()));
            Vec3 delta=wagon.position().subtract(state.position);state.position=wagon.position();double distance=delta.horizontalDistance();
            if(distance<.0001||distance>1||wagon.supportMask()==0) {
                if(state.sound!=null) { mc.getSoundManager().stop(state.sound);state.sound=null; }continue;
            }
            if(state.sound==null||state.sound.isStopped()) { state.sound=new Rolling(wagon);mc.getSoundManager().play(state.sound); }
            state.dustDistance+=distance;
            if(state.dustDistance<.18||mc.player==null||mc.player.distanceToSqr(wagon)>32*32)continue;
            state.dustDistance%=.18;
            for(int i=0;i<4;i++)if((wagon.supportMask()&(1<<i))!=0) {
                Vec3 contact=wagon.wheelCentre(i,wagon.pose()).add(0,-WagonPhysics.radius(i),0);
                var g=WagonPhysics.ground(mc.level,contact,.15,.3,.1);if(!g.present())continue;
                BlockPos block=BlockPos.containing(contact.x,g.height()-.01,contact.z);var material=mc.level.getBlockState(block);
                if(material.isAir()||!material.shouldSpawnTerrainParticles())continue;
                Vec3 behind=delta.normalize().scale(-.15);
                mc.particleEngine.add(new Dust(mc.level,new Vec3(contact.x,g.height()+.04,contact.z).add(behind),behind.scale(.08),material,block));
            }
        }
        states.entrySet().removeIf(entry->{if(live.contains(entry.getKey()))return false;if(entry.getValue().sound!=null)mc.getSoundManager().stop(entry.getValue().sound);return true;});
    }
    private WagonEffects() {}
}
