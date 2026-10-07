package com.sange.tm_wagon.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.sange.tm_wagon.cargo.StrawMatSleep;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class WagonSleepServerPlayerMixin extends Player {
    protected WagonSleepServerPlayerMixin(Level level,BlockPos pos,float yaw,GameProfile profile) { super(level,profile); }
    @Inject(method="startSleepInBed",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$skipRespawnForMat(net.minecraft.world.level.block.AbstractBedBlock bedBlock,net.minecraft.world.level.block.state.BlockState bedState,net.minecraft.world.attribute.BedRule rule,BlockPos pos,CallbackInfoReturnable<Either<BedSleepingProblem,Unit>> ci) {
        if(!StrawMatSleep.nativeStart(this,pos))return;
        var player=(ServerPlayer)(Object)this;
        var result=super.startSleepInBed(bedBlock,bedState,rule,pos);
        result.ifRight(unit->{player.awardStat(Stats.SLEEP_IN_BED);CriteriaTriggers.SLEPT_IN_BED.trigger(player);});
        player.level().updateSleepingPlayerList();ci.setReturnValue(result);
    }
}
