package com.sange.tm_wagon.client;

import com.sange.tm_wagon.assembly.AssemblyFrameItem;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.*;

public class FrameItemRenderer extends GeoItemRenderer<AssemblyFrameItem> {
    public FrameItemRenderer() { super(new Model());withScale(.32F); }
    @Override public void preRenderPass(RenderPassInfo<GeoRenderState> info,SubmitNodeCollector tasks) { super.preRenderPass(info,tasks);info.poseStack().translate(.5,.5,.5); }
    @Override public void adjustRenderPose(RenderPassInfo<GeoRenderState> info) {}
    private static class Model extends GeoModel<AssemblyFrameItem> {
        private Identifier resource(String path) { return Identifier.fromNamespaceAndPath("tm_wagon",path); }
        @Override public Identifier getModelResource(GeoRenderState state) { return resource("parts/wagon_assembly_frame"); }
        @Override public Identifier getTextureResource(GeoRenderState state) { return resource("textures/block/wagon_assembly_frame.png"); }
        @Override public Identifier getAnimationResource(AssemblyFrameItem item) { return resource("wagon_assembly_frame"); }
    }
}
