package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.assembly.AssemblyFrameItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class FrameItemRenderer extends GeoItemRenderer<AssemblyFrameItem> {
    public FrameItemRenderer() { super(new Model()); withScale(.32F); }
    @Override public void preRender(PoseStack poses,AssemblyFrameItem item,BakedGeoModel model,MultiBufferSource sources,
            VertexConsumer buffer,boolean reRender,float tick,int light,int overlay,float red,float green,float blue,float alpha) {
        this.itemRenderTranslations = new org.joml.Matrix4f(poses.last().pose());
        if (!reRender) poses.translate(.5,.5,.5);
        scaleModelForRender(scaleWidth,scaleHeight,poses,item,model,reRender,tick,light,overlay);
    }
    private static class Model extends GeoModel<AssemblyFrameItem> {
        private ResourceLocation resource(String path) { return new ResourceLocation(TravelingMerchantWagon.MODID,path); }
        @Override public ResourceLocation getModelResource(AssemblyFrameItem item) { return resource("geo/parts/wagon_assembly_frame.geo.json"); }
        @Override public ResourceLocation getTextureResource(AssemblyFrameItem item) { return resource("textures/block/wagon_assembly_frame.png"); }
        @Override public ResourceLocation getAnimationResource(AssemblyFrameItem item) { return resource("animations/wagon_assembly_frame.animation.json"); }
    }
}
