package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.client.CargoChestVisuals;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChestBlockEntity.class)
public abstract class WagonChestLidMixin {
    @Inject(method="getOpenNess",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$cargoLid(float tick,CallbackInfoReturnable<Float> result) {
        var openness=CargoChestVisuals.openness((ChestBlockEntity)(Object)this,tick);
        if(openness!=null)result.setReturnValue(openness);
    }
}
