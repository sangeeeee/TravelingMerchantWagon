package com.sange.tm_wagon.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tm_wagon.compat.maid.WagonMaidExtension;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The generic owner-vehicle AI could otherwise choose the driver's seat or undo autonomous boarding. */
@Pseudo
@Mixin(targets="com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerVehicleTask",remap=false)
public abstract class WagonMaidFollowVehicleMixin {
    @Inject(method="checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Z",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$companionVehicle(ServerLevel level,EntityMaid maid,CallbackInfoReturnable<Boolean> callback) {
        var owner=maid.getOwner();
        if(WagonMaidExtension.selected(maid)||maid.getVehicle() instanceof WagonEntity
            ||owner!=null&&owner.getControlledVehicle() instanceof WagonEntity)callback.setReturnValue(false);
    }
}
