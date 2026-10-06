package com.sange.tm_wagon.mixin;
import com.sange.tm_wagon.client.WagonWorldBlockPicking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class WagonWorldBlockPickingMixin {
    @Inject(method="pick",at=@At("TAIL"))
    private void tm_wagon$visibleWorldOutline(float partial,CallbackInfo ci) {
        var mc=Minecraft.getInstance();var camera=mc.getCameraEntity();if(camera==null||mc.gameMode==null)return;
        mc.hitResult=WagonWorldBlockPicking.resolve(camera,mc.gameMode.getPickRange(),partial,mc.hitResult);
        if(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult)mc.crosshairPickEntity=null;
    }
}
