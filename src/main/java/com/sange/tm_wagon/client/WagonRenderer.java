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
    @Override protected void applyRotations(WagonEntity wagon,com.mojang.blaze3d.vertex.PoseStack poses,
            float age,float yaw,float tick,float scale) {
        // GeckoLib's default non-living entity path supplies yaw=0; use the same
        // orientation as the block renderer and compound collision geometry.
        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(switch(wagon.facing()) {
            case NORTH -> 0; case EAST -> 270; case SOUTH -> 180; case WEST -> 90; default -> 0;
        }));
    }
    @Override public void renderRecursively(com.mojang.blaze3d.vertex.PoseStack poses,WagonEntity wagon,
            software.bernie.geckolib.cache.object.GeoBone bone,net.minecraft.client.renderer.RenderType type,
            net.minecraft.client.renderer.MultiBufferSource buffers,com.mojang.blaze3d.vertex.VertexConsumer buffer,
            boolean reRender,float tick,int light,int overlay,int colour) {
        if (bone.getName().equals("frame_root")) bone.setHidden(true);
        else if (VISIBLE.contains(bone.getName())) bone.setHidden(false);
        super.renderRecursively(poses,wagon,bone,type,buffers,buffer,reRender,tick,light,overlay,colour);
    }
    public static class Model extends GeoModel<WagonEntity> {
        private ResourceLocation resource(String path) { return ResourceLocation.fromNamespaceAndPath(TravelingMerchantWagon.MODID,path); }
        @Override public ResourceLocation getModelResource(WagonEntity wagon) {
            String seat=wagon.parts().get(WagonSlot.SEAT)==WagonPart.DOUBLE_SEAT ? "double_seat" : "single_seat";
            String shaft=wagon.parts().get(WagonSlot.SHAFTS)==WagonPart.DOUBLE_HORSE_SHAFTS ? "double_horse" : "single_horse";
            return resource("geo/assembly/"+seat+"_"+shaft+".geo.json");
        }
        @Override public ResourceLocation getTextureResource(WagonEntity wagon) { return resource("textures/entity/wagon.png"); }
        @Override public ResourceLocation getAnimationResource(WagonEntity wagon) { return resource("animations/assembly.animation.json"); }
        @Override public void setCustomAnimations(WagonEntity wagon,long id,AnimationState<WagonEntity> state) {
            getBone("frame_root").ifPresent(b -> b.setHidden(true));
            for (String name : new String[]{"seat","shafts","front_left_wheel","front_right_wheel","rear_left_wheel","rear_right_wheel"})
                getBone(name).ifPresent(b -> b.setHidden(false));
        }
    }
}
