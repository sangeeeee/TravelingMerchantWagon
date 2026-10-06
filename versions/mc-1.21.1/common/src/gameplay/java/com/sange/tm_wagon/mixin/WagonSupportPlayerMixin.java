package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.entity.WagonSupport;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla checks only blocks for floating; a wagon's collision top also provides ground. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class WagonSupportPlayerMixin {
    @Inject(method="noBlocksAround",at=@At("RETURN"),cancellable=true)
    private void tm_wagon$wagonIsGround(Entity entity,CallbackInfoReturnable<Boolean> callback) {
        if(callback.getReturnValue()&&WagonSupport.supportedByWagon(entity))callback.setReturnValue(false);
    }
}
