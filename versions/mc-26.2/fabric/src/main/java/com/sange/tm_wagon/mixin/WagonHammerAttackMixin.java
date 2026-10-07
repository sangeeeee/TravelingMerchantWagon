package com.sange.tm_wagon.mixin;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.assembly.WagonContent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Player.class)
public abstract class WagonHammerAttackMixin {
    @Inject(method="attack",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$hammer(Entity target,CallbackInfo ci){
        if(!(target instanceof WagonEntity wagon))return;
        Player player=(Player)(Object)this;
        if(!player.level().isClientSide()&&player.getMainHandItem().is(WagonContent.DISMANTLING_HAMMER.get()))wagon.dismantle(player);
        ci.cancel();
    }
}
