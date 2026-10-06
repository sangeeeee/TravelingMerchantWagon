package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.assembly.AssemblyFrameBlock;
import com.sange.tm_wagon.assembly.AssemblyPartBlock;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.entity.CargoSeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.Function;
import java.util.function.BiFunction;

/** Reject only pickup, leaving the original right-click event free for wagon interactions. */
@Pseudo
@Mixin(targets="tschipp.carryon.common.carry.PickupHandler",remap=false)
public abstract class WagonCarryOnPickupMixin {
    @Inject(method="tryPickupEntity",at=@At("HEAD"),cancellable=true,remap=false)
    private static void tm_wagon$protectEntity(ServerPlayer player,Entity target,Function<Entity,Boolean> callback,CallbackInfoReturnable<Boolean> cir) {
        if(target instanceof WagonEntity||target instanceof CargoSeatEntity)cir.setReturnValue(false);
    }
    @Inject(method="tryPickUpBlock",at=@At("HEAD"),cancellable=true,remap=false)
    private static void tm_wagon$protectStructure(ServerPlayer player,BlockPos pos,Level level,BiFunction<BlockState,BlockPos,Boolean> callback,CallbackInfoReturnable<Boolean> cir) {
        var block=level.getBlockState(pos).getBlock();
        if(block instanceof AssemblyFrameBlock||block instanceof AssemblyPartBlock)cir.setReturnValue(false);
    }
}
