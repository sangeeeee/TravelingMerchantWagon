package com.sange.tm_wagon.compat;

import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Only new/loaded assembly cells are checked; no per-tick scan of structures. */
public final class StructureAssemblyGuard {
    private static final ConcurrentLinkedQueue<BlockEntity> PENDING=new ConcurrentLinkedQueue<>();
    public static void loaded(BlockEntity entity) {
        if(entity.getLevel() instanceof ServerLevel&&StructureCollision.available())PENDING.add(entity);
    }
    public static void placed(ServerLevel level,BlockPos pos) {
        if(StructureCollision.available())SableAssemblyGuard.rejectPlaced(level,pos);
    }
    public static void afterTick(net.minecraft.server.MinecraftServer server) {
        BlockEntity entity;
        while((entity=PENDING.poll())!=null) {
            if(entity.isRemoved()||!(entity.getLevel() instanceof ServerLevel level)
                ||level.getServer()!=server||!level.hasChunkAt(entity.getBlockPos())
                ||level.getBlockEntity(entity.getBlockPos())!=entity)continue;
            // A normal item placement has finished consuming its item, and command /
            // structure imports have finished loading their block-entity contents.
            SableAssemblyGuard.rejectPlaced(level,entity.getBlockPos());
        }
    }
    private StructureAssemblyGuard() {}
}
