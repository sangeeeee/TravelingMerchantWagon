package com.sange.tm_wagon.entity;

import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** One-shot cleanup mailbox for harnesses belonging to unloaded horses. */
public final class HarnessSavedData extends SavedData {
    private final Set<UUID> released=new HashSet<>();
    private static final Codec<HarnessSavedData> CODEC=UUIDUtil.CODEC.listOf().xmap(ids->{
        var data=new HarnessSavedData();data.released.addAll(ids);return data;
    },data->java.util.List.copyOf(data.released));
    private static final SavedDataType<HarnessSavedData> TYPE=new SavedDataType<>(
        Identifier.fromNamespaceAndPath("tm_wagon","released_horses"),HarnessSavedData::new,CODEC,null);
    public static HarnessSavedData get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public void release(UUID horse) { released.add(horse);setDirty(); }
    public boolean consume(UUID horse) { boolean result=released.remove(horse);if(result)setDirty();return result; }
}
