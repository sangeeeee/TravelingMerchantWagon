package com.sange.tm_wagon.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sange.tm_wagon.compat.SableAssemblyGuard;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets="dev.ryanhcode.sable.api.SubLevelAssemblyHelper",remap=false)
public abstract class WagonSableAssemblyMixin {
    @WrapMethod(method="moveBlocks",remap=false)
    private static void tm_wagon$rejectAssembly(ServerLevel level,AssemblyTransform transform,Iterable<BlockPos> blocks,Operation<Void> original) {
        var allowed=SableAssemblyGuard.beforeMove(level,transform,blocks);
        // Sable's mover expects a nonempty iterable when locating destination plots.
        if(!allowed.isEmpty())original.call(level,transform,allowed);
    }
}
