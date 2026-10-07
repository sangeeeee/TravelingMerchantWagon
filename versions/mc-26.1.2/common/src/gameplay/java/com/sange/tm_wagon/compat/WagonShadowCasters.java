package com.sange.tm_wagon.compat;

import com.sange.tm_wagon.assembly.AssemblyFrameBlockEntity;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;

/** Client-loaded assembly roots only. Never scans chunks or changes world lighting. */
public final class WagonShadowCasters {
    private static final Set<AssemblyFrameBlockEntity> ROOTS=Collections.newSetFromMap(new WeakHashMap<>());
    public static void loaded(AssemblyFrameBlockEntity frame) {
        if(frame.getLevel()!=null&&frame.getLevel().isClientSide())ROOTS.add(frame);
    }
    public static void removed(AssemblyFrameBlockEntity frame) {
        if(frame.getLevel()!=null&&frame.getLevel().isClientSide())ROOTS.remove(frame);
    }
    public static void visit(Level level,java.util.function.Consumer<AssemblyFrameBlockEntity> visitor) {
        for(var frame:ROOTS)if(!frame.isRemoved()&&frame.getLevel()==level)visitor.accept(frame);
    }
    private WagonShadowCasters() {}
}
