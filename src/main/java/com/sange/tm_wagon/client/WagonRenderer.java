package com.sange.tm_wagon.client;

import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class WagonRenderer extends GeoEntityRenderer<WagonEntity> {
    private static final java.util.Set<String> VISIBLE=java.util.Set.of("seat","shafts","front_left_wheel","front_right_wheel","rear_left_wheel","rear_right_wheel");
    public WagonRenderer(EntityRendererProvider.Context context) { super(context,new Model()); shadowRadius=1.5F; }
    @Override public void render(WagonEntity wagon,float yaw,float tick,com.mojang.blaze3d.vertex.PoseStack poses,
            net.minecraft.client.renderer.MultiBufferSource buffers,int light) {
        super.render(wagon,yaw,tick,poses,buffers,light);
        poses.pushPose();applyRotations(wagon,poses,0,yaw,tick,1);
        CargoRenderer.render(wagon.cargo(),tick,poses,buffers,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);poses.popPose();
    }
    @Override protected void applyRotations(WagonEntity wagon,com.mojang.blaze3d.vertex.PoseStack poses,
            float age,float yaw,float tick,float scale) {
        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-net.minecraft.util.Mth.rotLerp(tick,wagon.yRotO,wagon.getYRot())));
        poses.translate(0,1.5,0);
        poses.mulPose(com.mojang.math.Axis.ZP.rotation(wagon.renderRoll(tick)));
        poses.mulPose(com.mojang.math.Axis.XP.rotation(wagon.renderPitch(tick)));
        poses.translate(0,-1.5,0);
    }
    @Override public void renderRecursively(com.mojang.blaze3d.vertex.PoseStack poses,WagonEntity wagon,
            software.bernie.geckolib.cache.object.GeoBone bone,net.minecraft.client.renderer.RenderType type,
            net.minecraft.client.renderer.MultiBufferSource buffers,com.mojang.blaze3d.vertex.VertexConsumer buffer,
            boolean reRender,float tick,int light,int overlay,int colour) {
        if (bone.getName().equals("frame_root")) bone.setHidden(true);
        else if (VISIBLE.contains(bone.getName())) bone.setHidden(false);
        switch(bone.getName()) {
            case "front_axle" -> bone.setRotY(-wagon.renderSteering(tick));
            case "shafts" -> bone.setRotX(wagon.renderShaftPitch(tick));
            case "front_left_wheel" -> bone.setRotX(wagon.renderWheel(0,tick));
            case "front_right_wheel" -> bone.setRotX(wagon.renderWheel(1,tick));
            case "rear_left_wheel" -> bone.setRotX(wagon.renderWheel(2,tick));
            case "rear_right_wheel" -> bone.setRotX(wagon.renderWheel(3,tick));
            case "tailgate" -> bone.setRotX((float)Math.PI*wagon.cargo().gateProgress(tick));
        }
        super.renderRecursively(poses,wagon,bone,type,buffers,buffer,reRender,tick,light,overlay,colour);
    }
    public static class Model extends GeoModel<WagonEntity> {
        private ResourceLocation resource(String path) { return ResourceLocation.fromNamespaceAndPath(TravelingMerchantWagon.MODID,path); }
        @Override public ResourceLocation getModelResource(WagonEntity wagon) {
            String seat=wagon.parts().getOrDefault(WagonSlot.SEAT,WagonPart.SINGLE_SEAT).id;
            String shaft=wagon.parts().get(WagonSlot.SHAFTS)==WagonPart.DOUBLE_HORSE_SHAFTS ? "double_horse" : "single_horse";
            return resource("geo/assembly/"+wagon.cargoBody().modelPrefix()+seat+"_"+shaft+".geo.json");
        }
        @Override public ResourceLocation getTextureResource(WagonEntity wagon) { return resource("textures/entity/wagon.png"); }
        @Override public ResourceLocation getAnimationResource(WagonEntity wagon) { return resource("animations/assembly.animation.json"); }
        @Override public void setCustomAnimations(WagonEntity wagon,long id,AnimationState<WagonEntity> state) {
            float tick=state.getPartialTick();
            getBone("front_axle").ifPresent(b->b.setRotY(-wagon.renderSteering(tick)));
            getBone("shafts").ifPresent(b->b.setRotX(wagon.renderShaftPitch(tick)));
            getBone("tailgate").ifPresent(b->b.setRotX((float)Math.PI*wagon.cargo().gateProgress(tick)));
            String[] wheelNames={"front_left_wheel","front_right_wheel","rear_left_wheel","rear_right_wheel"};
            for(int i=0;i<4;i++) { final int index=i;getBone(wheelNames[i]).ifPresent(b->b.setRotX(wagon.renderWheel(index,tick))); }
            getBone("frame_root").ifPresent(b -> b.setHidden(true));
            for (String name : new String[]{"seat","shafts","front_left_wheel","front_right_wheel","rear_left_wheel","rear_right_wheel"})
                getBone(name).ifPresent(b -> b.setHidden(false));
        }
    }
}
