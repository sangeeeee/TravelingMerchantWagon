package com.sange.tm_wagon.client;
import com.sange.tm_wagon.assembly.*;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;

public final class AssemblyRenderer extends GeoBlockRenderer<AssemblyFrameBlockEntity,AssemblyRenderer.State> {
    // Keep reads and writes in one store; GeckoLib 5.5 injects its own writer into the vanilla superclass.
    public static final class State extends BlockEntityRenderState implements GeoRenderState {
        private final java.util.Map<com.geckolib.constant.dataticket.DataTicket<?>,Object> data = new java.util.HashMap<>();
        @Override public java.util.Map<com.geckolib.constant.dataticket.DataTicket<?>,Object> getDataMap() { return data; }
        @Override public <D> void addGeckolibData(com.geckolib.constant.dataticket.DataTicket<D> ticket,D value) { data.put(ticket,value); }
        @Override public boolean hasGeckolibData(com.geckolib.constant.dataticket.DataTicket<?> ticket) { return data.containsKey(ticket); }
    }
    public AssemblyRenderer(BlockEntityRendererProvider.Context context) { super(context,new Model()); }
    @Override public State createRenderState() { return new State(); }
    @Override public void addRenderData(AssemblyFrameBlockEntity f,Void ignored,State state,float tick) {
        state.addGeckolibData(WagonVisual.DATA,new WagonVisual(f.parts(),f.materials(),f.cargo(),f.facing().toYRot(),0,0,0,0,new float[4],f.cargo().gateProgress(tick),f.frameProgress(tick),true));
    }
    @Override public void adjustModelBonesForRender(RenderPassInfo<State> info,BoneSnapshots bones) { info.renderState().getGeckolibData(WagonVisual.DATA).bones(bones); }
    @Override public void submitRenderTasks(RenderPassInfo<State> info,OrderedSubmitNodeCollector tasks,net.minecraft.client.renderer.rendertype.RenderType type) { WagonVisual.submit(info,tasks,type,info.renderState().getGeckolibData(WagonVisual.DATA)); }
    @Override public void submit(State state,PoseStack poses,SubmitNodeCollector tasks,CameraRenderState camera) {
        super.submit(state,poses,tasks,camera);var v=state.getGeckolibData(WagonVisual.DATA);
        if(v.parts().containsKey(WagonSlot.BODY)) {
            poses.pushPose();poses.translate(.5,0,.5);WagonRenderer.rotate(v,poses);
            CargoRenderer.render(v.cargo(),state.getPartialTick(),poses,tasks,state.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,camera);poses.popPose();
        }
    }
    @Override public boolean shouldRenderOffScreen() { return true; }
    private static final class Model extends GeoModel<AssemblyFrameBlockEntity> {
        public Identifier getModelResource(GeoRenderState state) { return WagonVisual.model(state); }
        public Identifier getTextureResource(GeoRenderState state) { return MaterialRenderer.ATLAS; }
        public Identifier getAnimationResource(AssemblyFrameBlockEntity entity) { return Identifier.fromNamespaceAndPath("tm_wagon","assembly"); }
    }
}
