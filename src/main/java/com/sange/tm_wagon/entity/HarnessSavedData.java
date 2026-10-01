package com.sange.tm_wagon.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Refunds are made immediately; unloaded horses consume this cleanup mailbox on return. */
public final class HarnessSavedData extends SavedData {
    private final Set<UUID> released=new HashSet<>();
    public static HarnessSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(HarnessSavedData::new,HarnessSavedData::load),"tm_wagon_released_horses");
    }
    private static HarnessSavedData load(CompoundTag tag,HolderLookup.Provider registries) {
        var data=new HarnessSavedData();
        for (String key:tag.getAllKeys()) try { data.released.add(UUID.fromString(key)); } catch(IllegalArgumentException ignored) {}
        return data;
    }
    public void release(UUID horse) { released.add(horse);setDirty(); }
    public boolean consume(UUID horse) { boolean result=released.remove(horse);if(result)setDirty();return result; }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        released.forEach(id->tag.putBoolean(id.toString(),true));return tag;
    }
}
