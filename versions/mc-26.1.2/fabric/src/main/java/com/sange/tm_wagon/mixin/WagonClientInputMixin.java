package com.sange.tm_wagon.mixin;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Minecraft.class)
public abstract class WagonClientInputMixin {
    @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$use(CallbackInfo ci){if(com.sange.tm_wagon.client.AssemblyFrameInput.blockUse())ci.cancel();}
    @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$attack(CallbackInfoReturnable<Boolean> ci){if(com.sange.tm_wagon.client.WagonAttackInput.blockAttack())ci.setReturnValue(false);}
    @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$continue(boolean held,CallbackInfo ci){if(held&&com.sange.tm_wagon.client.WagonAttackInput.blockAttack())ci.cancel();}
}
