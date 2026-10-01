package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.entity.WagonPlatform;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class WagonStandingPlayerMixin {
    @Inject(method="noBlocksAround",at=@At("RETURN"),cancellable=true)
    private void tm_wagon$platformIsGround(Entity entity,CallbackInfoReturnable<Boolean> callback) {
        if(callback.getReturnValue()&&WagonPlatform.supportedByWagon(entity))callback.setReturnValue(false);
    }
}
