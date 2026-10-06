package com.sange.tm_wagon.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tm_wagon.compat.maid.WagonMaidBehavior;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native beds remain in charge whenever the combined nearest-bed search chooses one. */
@Pseudo
@Mixin(targets="com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidBedTask",remap=false)
public abstract class WagonMaidBedMixin {
    @Inject(method="checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Z",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$keepMatTarget(ServerLevel level,EntityMaid maid,CallbackInfoReturnable<Boolean> callback) {
        if(WagonMaidBehavior.approachingMat(maid))callback.setReturnValue(false);
    }
}
