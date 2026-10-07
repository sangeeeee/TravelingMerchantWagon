package com.sange.tm_wagon.entity;

import com.sange.tm_wagon.TravelingMerchantWagon;
import java.util.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Index only wagons by every chunk touched by their bounds. Vanilla searches
 * entity origin sections with a two-block margin, which can miss long shafts. */
@EventBusSubscriber(modid=TravelingMerchantWagon.MODID)
public final class WagonSpatialIndex {
    private static final Map<Level,Index> LEVELS=new java.util.concurrent.ConcurrentHashMap<>();
    private static class Index {
        final Map<Long,Set<WagonEntity>> buckets=new HashMap<>();
        final Map<WagonEntity,Set<Long>> membership=new IdentityHashMap<>();
    }
    private static long key(int x,int z) { return net.minecraft.world.level.ChunkPos.pack(x,z); }
    private static int chunk(double coordinate) { return net.minecraft.util.Mth.floor(coordinate)>>4; }
    private static Set<Long> keys(AABB bounds) {
        Set<Long> keys=new HashSet<>();
        for (int x=chunk(bounds.minX);x<=chunk(bounds.maxX);x++) for (int z=chunk(bounds.minZ);z<=chunk(bounds.maxZ);z++) keys.add(key(x,z));
        return keys;
    }
    public static void update(WagonEntity wagon) {
        Index index=LEVELS.get(wagon.level());
        if (index==null || !index.membership.containsKey(wagon)) return;
        Set<Long> next=keys(wagon.getBoundingBox());
        if (next.equals(index.membership.get(wagon))) return;
        remove(index,wagon);index.membership.put(wagon,next);
        next.forEach(k -> index.buckets.computeIfAbsent(k,ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(wagon));
    }
    private static void remove(Index index,WagonEntity wagon) {
        Set<Long> previous=index.membership.remove(wagon);
        if (previous!=null) for (long k : previous) {
            Set<WagonEntity> bucket=index.buckets.get(k);bucket.remove(wagon);if (bucket.isEmpty()) index.buckets.remove(k);
        }
    }
    public static Collection<WagonEntity> candidates(Level level,AABB bounds) {
        Index index=LEVELS.get(level);
        if (index==null || index.buckets.isEmpty()) return List.of();
        Set<WagonEntity> result=Collections.newSetFromMap(new IdentityHashMap<>());
        // Large command/AI queries should not iterate millions of empty chunks.
        long width=(long)chunk(bounds.maxX)-chunk(bounds.minX)+1;
        long depth=(long)chunk(bounds.maxZ)-chunk(bounds.minZ)+1;
        if (width*depth>index.buckets.size()) {
            for (WagonEntity wagon : index.membership.keySet()) if (!wagon.isRemoved()&&wagon.getBoundingBox().intersects(bounds)) result.add(wagon);
        } else for (int x=chunk(bounds.minX);x<=chunk(bounds.maxX);x++) for (int z=chunk(bounds.minZ);z<=chunk(bounds.maxZ);z++) {
            Set<WagonEntity> bucket=index.buckets.get(key(x,z));
            if (bucket!=null) for (WagonEntity wagon : bucket) if (!wagon.isRemoved()&&wagon.getBoundingBox().intersects(bounds)) result.add(wagon);
        }
        return result;
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void joined(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof WagonEntity wagon && !event.isCanceled()) {
            Index index=LEVELS.computeIfAbsent(event.getLevel(),ignored -> new Index());index.membership.put(wagon,Set.of());update(wagon);
        }
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof WagonEntity wagon) { Index index=LEVELS.get(event.getLevel());if (index!=null) remove(index,wagon); }
    }
    @SubscribeEvent public static void unloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) LEVELS.remove(level);
    }
    private WagonSpatialIndex() {}
}
