package com.sange.tm_wagon.platform;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.cargo.*;
import com.sange.tm_wagon.entity.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.world.InteractionResult;
public final class FabricEvents {
    public static void register(){
        // Moving mats have no bed block at their sleeping position. Keep vanilla
        // sleep validation, without marking an unrelated world block occupied.
        var sleep=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tm_wagon","mat_sleep");
        net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.ALLOW_BED.addPhaseOrdering(sleep,net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.ALLOW_BED.register(sleep,(entity,pos,state,vanilla)->
            StrawMatSleep.validSleep(entity)?InteractionResult.SUCCESS:InteractionResult.PASS);
        net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.SET_BED_OCCUPATION_STATE.register((entity,pos,state,occupied)->
            StrawMatSleep.matSleeper(entity));
        var interaction=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tm_wagon","interaction");
        UseBlockCallback.EVENT.addPhaseOrdering(interaction,net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.addPhaseOrdering(interaction,net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(interaction,CargoInteractions::block);
        UseEntityCallback.EVENT.register(interaction,(player,world,hand,entity,hit)->{
            var result=com.sange.tm_wagon.compat.MaidCompat.capture(player,entity,hand);
            if(result!=InteractionResult.PASS)return result;
            return HorseHarness.interact(player,entity,hand)?InteractionResult.SUCCESS:InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(!(entity instanceof WagonEntity wagon))return InteractionResult.PASS;
            if(player.getMainHandItem().is(WagonContent.DISMANTLING_HAMMER.get())){
                if(!world.isClientSide)wagon.dismantle(player);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.FAIL;
        });
        var dismantle=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tm_wagon","dismantle");
        // Let claim/protection callbacks reject breaking before removing a whole part.
        PlayerBlockBreakEvents.BEFORE.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE,dismantle);
        PlayerBlockBreakEvents.BEFORE.register(dismantle,(world,player,pos,state,be)->{
            // Virtual cargo permission checks must never dismantle the assembly below it.
            if(world.getBlockState(pos)!=state)return true;
            if(state.getBlock() instanceof AssemblyFrameBlock block){block.onDestroyedByPlayer(state,world,pos,player,true,state.getFluidState());return false;}
            if(state.getBlock() instanceof AssemblyPartBlock block){block.onDestroyedByPlayer(state,world,pos,player,true,state.getFluidState());return false;}
            return true;
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{WagonSpatialIndex.joined(entity,world);HorseHarness.joining(entity,world);});
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{HorseHarness.leaving(entity);WagonSpatialIndex.left(entity);StrawMatSleep.leave(entity);});
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->HorseHarness.death(entity));
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((be,world)->{
            if(be instanceof AssemblyFrameBlockEntity f)f.onLoaded();else if(be instanceof AssemblyCellBlockEntity c)c.onLoaded();
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((be,world)->{if(be instanceof AssemblyFrameBlockEntity f)f.onUnloaded();});
        ServerTickEvents.END_WORLD_TICK.register(world->{
            StrawMatSleep.followAfterLevel(world);
            for(var player:world.players())BackpackSessions.tick(player);
        });
        ServerTickEvents.END_SERVER_TICK.register(com.sange.tm_wagon.compat.StructureAssemblyGuard::afterTick);
        ServerWorldEvents.UNLOAD.register((server,world)->{StrawMatSleep.unload(world);WagonSpatialIndex.unloaded(world);BackpackSessions.unload(world);});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->com.sange.tm_wagon.handbook.HandbookGifts.giveOnFirstJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{WagonCabinet.loggedOut(handler.player);BackpackSessions.logout(handler.player);});
        ServerPlayerEvents.COPY_FROM.register((oldPlayer,newPlayer,alive)->{
            EntityData.of(newPlayer).merge(EntityData.of(oldPlayer).copy());
        });
        EntityTrackingEvents.START_TRACKING.register((entity,player)->{if(entity instanceof net.minecraft.world.entity.LivingEntity living)StrawMatSleep.track(player,living);});
    }
    private FabricEvents(){}
}
