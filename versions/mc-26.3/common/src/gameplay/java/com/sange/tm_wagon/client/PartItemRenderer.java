package com.sange.tm_wagon.client;

import com.google.gson.JsonParser;
import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.assembly.WagonPart;
import com.sange.tm_wagon.assembly.WagonSlot;
import com.sange.tm_wagon.assembly.WagonPartItem;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.Identifier;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoItemRenderer;

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
    @Override public void preRenderPass(com.geckolib.renderer.base.RenderPassInfo<com.geckolib.renderer.base.GeoRenderState> info,net.minecraft.client.renderer.SubmitNodeCollector tasks) { super.preRenderPass(info,tasks);info.poseStack().translate(.5,.5,.5); }
    @Override public void adjustRenderPose(com.geckolib.renderer.base.RenderPassInfo<com.geckolib.renderer.base.GeoRenderState> info) {}
    @Override public void addRenderData(WagonPartItem item,RenderData data,com.geckolib.renderer.base.GeoRenderState state,float tick) {
        state.addGeckolibData(WagonVisual.DATA,new WagonVisual(java.util.Map.of(WagonSlot.BODY,item.part()),java.util.Map.of(WagonSlot.BODY,com.sange.tm_wagon.material.WagonMaterial.forPart(item.part(),data.itemStack())),null,0,0,0,0,0,new float[4],0,0,false));
    }
    @Override public void submitRenderTasks(com.geckolib.renderer.base.RenderPassInfo<com.geckolib.renderer.base.GeoRenderState> info,net.minecraft.client.renderer.OrderedSubmitNodeCollector tasks,net.minecraft.client.renderer.rendertype.RenderType type) {
        WagonVisual.submitItem(info,tasks,type,info.renderState().getGeckolibData(WagonVisual.DATA));
    }
    private static class Model extends GeoModel<WagonPartItem> {
        private Identifier resource(String path) { return Identifier.fromNamespaceAndPath(TravelingMerchantWagon.MODID,path); }
        @Override public Identifier getModelResource(com.geckolib.renderer.base.GeoRenderState state) { return resource("parts/"+state.getGeckolibData(WagonVisual.DATA).body().id+""); }
        @Override public Identifier getTextureResource(com.geckolib.renderer.base.GeoRenderState state) { return MaterialRenderer.ATLAS; }
        @Override public Identifier getAnimationResource(WagonPartItem item) { return resource("assembly"); }
    }
}
