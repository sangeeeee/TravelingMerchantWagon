package com.sange.tm_wagon.compat;

import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Only new/loaded assembly cells are checked; no per-tick scan of structures. */
@EventBusSubscriber(modid="tm_wagon")
public final class StructureAssemblyGuard {
    private static final ConcurrentLinkedQueue<BlockEntity> PENDING=new ConcurrentLinkedQueue<>();
    public static void loaded(BlockEntity entity) {
        if(entity.getLevel() instanceof ServerLevel&&StructureCollision.available())PENDING.add(entity);
    }
    public static void placed(ServerLevel level,BlockPos pos) {
        if(StructureCollision.available())SableAssemblyGuard.rejectPlaced(level,pos);
    }
    @SubscribeEvent public static void afterTick(ServerTickEvent.Post event) {
        BlockEntity entity;
        while((entity=PENDING.poll())!=null) {
            if(entity.isRemoved()||!(entity.getLevel() instanceof ServerLevel level)
                ||level.getServer()!=event.getServer()||!level.hasChunkAt(entity.getBlockPos())
                ||level.getBlockEntity(entity.getBlockPos())!=entity)continue;
            // A normal item placement has finished consuming its item, and command /
            // structure imports have finished loading their block-entity contents.
            SableAssemblyGuard.rejectPlaced(level,entity.getBlockPos());
        }
    }
    private StructureAssemblyGuard() {}
}
