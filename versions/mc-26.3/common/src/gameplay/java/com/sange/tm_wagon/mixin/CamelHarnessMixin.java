package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.entity.HorseHarness;
import net.minecraft.world.entity.animal.camel.Camel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the camel's ordinary idle animations without letting its AI sit in the harness. */
@Mixin(Camel.class)
public abstract class CamelHarnessMixin {
    @Inject(method="sitDown",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$standInHarness(CallbackInfo callback) {
        if(HorseHarness.attached((Camel)(Object)this))callback.cancel();
    }
}
