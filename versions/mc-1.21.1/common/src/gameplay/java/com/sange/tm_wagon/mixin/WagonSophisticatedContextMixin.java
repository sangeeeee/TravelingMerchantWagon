package com.sange.tm_wagon.mixin;

import java.util.UUID;
import com.sange.tm_wagon.cargo.BackpackSessions;
import com.sange.tm_wagon.compat.backpack.SophisticatedCargo;
import net.minecraft.world.entity.player.Player;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Native settings and sub-backpacks inherit the same bounded, cargo-specific access check. */
@Pseudo
@Mixin(targets="net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext$Item",remap=false)
public abstract class WagonSophisticatedContextMixin implements BackpackSessions.Context {
    @Shadow @Final protected String handlerName;
    @Shadow @Final protected String identifier;
    @Override public UUID wagonBackpackId(){return SophisticatedCargo.HANDLER.equals(handlerName)?SophisticatedCargo.parse(identifier):null;}
    @Inject(method="canInteractWith",at=@At("HEAD"),cancellable=true,remap=false)
    private void tm_wagon$valid(Player player,CallbackInfoReturnable<Boolean> cir) {
        if(SophisticatedCargo.HANDLER.equals(handlerName))cir.setReturnValue(BackpackSessions.valid(player,wagonBackpackId()));
    }
    @Inject(method="getBackpackWrapper",at=@At("RETURN"),remap=false)
    private void tm_wagon$bind(Player player,CallbackInfoReturnable<IBackpackWrapper> cir) {
        if(SophisticatedCargo.HANDLER.equals(handlerName))SophisticatedCargo.bind(player,identifier,cir.getReturnValue());
    }
    @Inject(method="onUpgradeChanged",at=@At("TAIL"),remap=false)
    private void tm_wagon$dirty(Player player,CallbackInfo ci) {
        if(SophisticatedCargo.HANDLER.equals(handlerName))BackpackSessions.changed(player,wagonBackpackId(),true);
    }
}
