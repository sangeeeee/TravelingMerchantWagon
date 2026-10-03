package com.sange.tm_wagon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sange.tm_wagon.cargo.StrawMatSleep;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only mat sleepers use the wagon's arbitrary yaw and slope instead of cardinal bed rotations. */
@Mixin(LivingEntityRenderer.class)
public abstract class WagonSleepRendererMixin {
    @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",shift=At.Shift.AFTER))
    private void tm_wagon$bodyOffset(LivingEntity entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light,CallbackInfo ci) {
        var p=entity;if(!p.isSleeping())return;
        var pose=StrawMatSleep.sleepingPose(p);if(pose==null)return;
        var direction=p.getBedOrientation();float distance=p.getEyeHeight(net.minecraft.world.entity.Pose.STANDING)-.1F;
        var desired=pose.forward().scale(-distance);
        poses.translate(desired.x+direction.getStepX()*distance,desired.y,desired.z+direction.getStepZ()*distance);
    }
    @Inject(method="setupRotations",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$bodyRotation(LivingEntity entity,PoseStack poses,float bob,float yaw,float partial,float scale,CallbackInfo ci) {
        var p=entity;if(!p.isSleeping()||p.deathTime>0)return;
        var pose=StrawMatSleep.sleepingPose(p);if(pose==null)return;
        poses.mulPose(Axis.YP.rotationDegrees(180-pose.yaw()));
        poses.mulPose(Axis.ZP.rotation(pose.roll()));poses.mulPose(Axis.XP.rotation(pose.pitch()));
        poses.mulPose(Axis.YP.rotationDegrees(270));poses.mulPose(Axis.ZP.rotationDegrees(90));poses.mulPose(Axis.YP.rotationDegrees(270));ci.cancel();
    }
}
