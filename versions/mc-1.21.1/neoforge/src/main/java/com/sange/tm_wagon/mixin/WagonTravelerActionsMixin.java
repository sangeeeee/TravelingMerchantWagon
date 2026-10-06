package com.sange.tm_wagon.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Settings navigation uses cargo-backed native menus instead of a player inventory/world position. */
@Pseudo
@Mixin(targets="com.tiviacz.travelersbackpack.common.ServerActions",remap=false)
public abstract class WagonTravelerActionsMixin {
    @Inject(method="openBackpackSettings",at=@At("HEAD"),cancellable=true,remap=false)
    private static void tm_wagon$settings(ServerPlayer player,int entityId,boolean open,CallbackInfo ci) {
        if(com.sange.tm_wagon.compat.backpack.TravelerCargo.settings(player,entityId,open))ci.cancel();
    }
}
