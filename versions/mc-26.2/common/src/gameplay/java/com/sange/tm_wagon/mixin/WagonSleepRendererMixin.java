package com.sange.tm_wagon.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sange.tm_wagon.client.SleepRenderAttachment;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(EntityRenderDispatcher.class)
public abstract class WagonSleepRendererMixin {
    @WrapOperation(method="submit",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void tm_wagon$renderSleeper(EntityRenderer renderer,EntityRenderState state,PoseStack poses,SubmitNodeCollector tasks,CameraRenderState camera,Operation<Void> original) {
        var data=(SleepRenderAttachment)state;var pose=data.tm_wagon$sleepPose();
        if(pose==null) { original.call(renderer,state,poses,tasks,camera);return; }
        poses.pushPose();
        try { poses.translate(data.tm_wagon$sleepOffset());poses.mulPose(Axis.YP.rotationDegrees(180-pose.yaw()));poses.mulPose(Axis.ZP.rotation(pose.roll()));poses.mulPose(Axis.XP.rotation(pose.pitch()));poses.mulPose(Axis.YP.rotationDegrees(data.tm_wagon$bedRotation()));original.call(renderer,state,poses,tasks,camera); }
        finally { poses.popPose(); }
    }
}
