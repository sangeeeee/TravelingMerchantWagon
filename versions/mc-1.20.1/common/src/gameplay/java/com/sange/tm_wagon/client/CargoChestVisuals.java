package com.sange.tm_wagon.client;

import com.sange.tm_wagon.cargo.CargoEntry;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Native mod BE types select their own renderer/texture; visuals never own cargo contents. */
public final class CargoChestVisuals {
    private static final Map<ChestBlockEntity,WeakReference<CargoEntry>> ENTRIES=new WeakHashMap<>();
    public static BlockEntity create(CargoEntry entry) {
        var be=((EntityBlock)entry.state.getBlock()).newBlockEntity(BlockPos.ZERO,entry.state);
        if(!(be instanceof ChestBlockEntity chest))throw new IllegalStateException("Unsupported cargo chest renderer");
        ENTRIES.put(chest,new WeakReference<>(entry));return be;
    }
    public static Float openness(ChestBlockEntity chest,float tick) {
        var reference=ENTRIES.get(chest);var entry=reference==null?null:reference.get();
        return entry==null?null:entry.lid(tick);
    }
    private CargoChestVisuals(){}
}
