package com.sange.tm_wagon.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/** One NBT compound on each entity, saved by the entity data mixin. */
public interface EntityData {
    CompoundTag tm_wagon$data();
    static CompoundTag of(Entity entity) { return ((EntityData)entity).tm_wagon$data(); }
}
