package com.sange.tm_wagon.client;

import com.google.gson.JsonParser;
import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.assembly.WagonPart;
import com.sange.tm_wagon.assembly.WagonPartItem;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class PartItemRenderer extends GeoItemRenderer<WagonPartItem> {
    public PartItemRenderer(WagonPart part) {
        super(new Model());
        try (var stream = PartItemRenderer.class.getResourceAsStream("/assets/tm_wagon/parts.json")) {
            if (stream == null) throw new IllegalStateException("Missing part item dimensions");
            float scale = JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject(part.id).get("scale").getAsFloat();
            withScale(scale);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    @Override public void preRender(com.mojang.blaze3d.vertex.PoseStack poseStack, WagonPartItem item,
            software.bernie.geckolib.cache.object.BakedGeoModel model, net.minecraft.client.renderer.MultiBufferSource sources,
            com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean reRender, float partialTick, int light, int overlay, float red,float green,float blue,float alpha) {
        this.itemRenderTranslations = new org.joml.Matrix4f(poseStack.last().pose());
        // The inventory center must not be multiplied by the miniature scale.
        if (!reRender) poseStack.translate(.5,.5,.5);
        scaleModelForRender(this.scaleWidth,this.scaleHeight,poseStack,item,model,reRender,partialTick,light,overlay);
    }
    @Override public void renderCubesOfBone(com.mojang.blaze3d.vertex.PoseStack poses,software.bernie.geckolib.cache.object.GeoBone bone,
            com.mojang.blaze3d.vertex.VertexConsumer buffer,int light,int overlay,float red,float green,float blue,float alpha) {
        var part=getAnimatable().part();var stack=getCurrentItemStack();
        MaterialRenderer.cubes(this,poses,bone,buffer,light,overlay,ColourMath.pack(red,green,blue,alpha),part,com.sange.tm_wagon.material.WagonMaterial.forPart(part,stack));
    }
    private static class Model extends GeoModel<WagonPartItem> {
        private ResourceLocation resource(String path) { return new ResourceLocation(TravelingMerchantWagon.MODID,path); }
        @Override public ResourceLocation getModelResource(WagonPartItem item) { return resource("geo/parts/"+item.part().id+".geo.json"); }
        @Override public ResourceLocation getTextureResource(WagonPartItem item) { return MaterialRenderer.ATLAS; }
        @Override public ResourceLocation getAnimationResource(WagonPartItem item) { return resource("animations/assembly.animation.json"); }
    }
}
