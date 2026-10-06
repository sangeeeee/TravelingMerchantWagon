package com.sange.tm_wagon.mixin;
import com.sange.tm_wagon.entity.HorseHarness;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Mob.class)
public abstract class HorseLeashMixin {
    @Inject(method="tickLeash",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$customHarness(CallbackInfo ci) {if((Object)this instanceof AbstractHorse horse&&HorseHarness.attached(horse))ci.cancel();}
}
