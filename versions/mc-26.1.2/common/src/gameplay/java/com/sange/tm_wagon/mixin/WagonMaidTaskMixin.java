package com.sange.tm_wagon.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tm_wagon.compat.maid.WagonMaidBehavior;
import com.sange.tm_wagon.compat.maid.WagonMaidExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leave the companion seat before native task selection rebuilds the maid's brain. */
@Pseudo
@Mixin(targets="com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidTaskManager",remap=false)
public abstract class WagonMaidTaskMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private EntityMaid maid;
    @Inject(method="setTask",at=@At("HEAD"))
    private void tm_wagon$leaveSeat(IMaidTask task,CallbackInfo callback) {
        if(!maid.level().isClientSide()&&WagonMaidExtension.selected(maid)&&!maid.getTask().getUid().equals(task.getUid()))
            WagonMaidBehavior.leaveCompanionSeat(maid);
    }
}
