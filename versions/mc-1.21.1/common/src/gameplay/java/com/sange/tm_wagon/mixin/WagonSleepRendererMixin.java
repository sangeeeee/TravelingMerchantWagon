package com.sange.tm_wagon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sange.tm_wagon.cargo.StrawMatSleep;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Apply the wagon frame before any renderer, including the maid's independent Gecko path. */
@Mixin(EntityRenderDispatcher.class)
public abstract class WagonSleepRendererMixin {
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void tm_wagon$renderSleeper(EntityRenderer<Entity> renderer,Entity entity,float yaw,float partial,
            PoseStack poses,MultiBufferSource buffers,int light,Operation<Void> original) {
        var pose=entity instanceof LivingEntity living&&living.isSleeping()&&living.deathTime==0
            ?StrawMatSleep.sleepingPose(living,partial):null;
        if(pose==null) { original.call(renderer,entity,yaw,partial,poses,buffers,light);return; }
        var living=(LivingEntity)entity;
        var offset=StrawMatSleep.sleepingPoint(living,partial).subtract(StrawMatSleep.renderPosition(entity,partial));
        poses.pushPose();
        try {
            poses.translate(offset.x,offset.y,offset.z);
            poses.mulPose(Axis.YP.rotationDegrees(180-pose.yaw()));
            poses.mulPose(Axis.ZP.rotation(pose.roll()));poses.mulPose(Axis.XP.rotation(pose.pitch()));
            if(StrawMatSleep.reversed(living))poses.mulPose(Axis.YP.rotationDegrees(180));
            // Undo the cardinal bed frame. The renderer still owns its native lying pose,
            // model scale and head offset, now expressed in the wagon's moving frame.
            poses.mulPose(Axis.YP.rotationDegrees(living.getBedOrientation().toYRot()-180));
            original.call(renderer,entity,yaw,partial,poses,buffers,light);
        } finally { poses.popPose(); }
    }
}
