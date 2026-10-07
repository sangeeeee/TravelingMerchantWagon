package com.sange.tm_wagon.client;
import com.sange.tm_wagon.entity.WagonEntity;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;

public final class WagonRenderer extends GeoEntityRenderer<WagonEntity,WagonRenderer.State> {
    // Keep reads and writes in one store; GeckoLib 5.5 injects its own writer into the vanilla superclass.
    public static final class State extends EntityRenderState implements GeoRenderState {
        private final java.util.Map<com.geckolib.constant.dataticket.DataTicket<?>,Object> data = new java.util.HashMap<>();
        @Override public java.util.Map<com.geckolib.constant.dataticket.DataTicket<?>,Object> getDataMap() { return data; }
        @Override public <D> void addGeckolibData(com.geckolib.constant.dataticket.DataTicket<D> ticket,D value) { data.put(ticket,value); }
        @Override public boolean hasGeckolibData(com.geckolib.constant.dataticket.DataTicket<?> ticket) { return data.containsKey(ticket); }
    }
    public WagonRenderer(EntityRendererProvider.Context context) { super(context,new Model());shadowRadius=1.5F; }
    @Override public State createRenderState(WagonEntity entity,Void ignored) { return new State(); }
    @Override public void addRenderData(WagonEntity w,Void ignored,State state,float tick) {
        state.addGeckolibData(WagonVisual.DATA,new WagonVisual(w.parts(),w.materials(),w.cargo(),net.minecraft.util.Mth.rotLerp(tick,w.yRotO,w.getYRot()),
            w.renderPitch(tick),w.renderRoll(tick)+WagonEffects.sway(w,tick),w.renderSteering(tick),w.renderShaftPitch(tick),
            new float[]{w.renderWheel(0,tick),w.renderWheel(1,tick),w.renderWheel(2,tick),w.renderWheel(3,tick)},w.cargo().gateProgress(tick),0,false));
    }
    @Override protected void applyRotations(RenderPassInfo<State> info,PoseStack poses,float scale) { rotate(info.renderState().getGeckolibData(WagonVisual.DATA),poses); }
    static void rotate(WagonVisual v,PoseStack poses) {
        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-v.yaw()));poses.translate(0,1.5,0);
        poses.mulPose(com.mojang.math.Axis.ZP.rotation(v.roll()));poses.mulPose(com.mojang.math.Axis.XP.rotation(v.pitch()));poses.translate(0,-1.5,0);
    }
    @Override public void adjustModelBonesForRender(RenderPassInfo<State> info,BoneSnapshots bones) { info.renderState().getGeckolibData(WagonVisual.DATA).bones(bones); }
    @Override public void submitRenderTasks(RenderPassInfo<State> info,OrderedSubmitNodeCollector tasks,net.minecraft.client.renderer.rendertype.RenderType type) { WagonVisual.submit(info,tasks,type,info.renderState().getGeckolibData(WagonVisual.DATA)); }
    @Override public void submit(State state,PoseStack poses,SubmitNodeCollector tasks,CameraRenderState camera) {
        super.submit(state,poses,tasks,camera);var visual=state.getGeckolibData(WagonVisual.DATA);
        poses.pushPose();rotate(visual,poses);CargoRenderer.render(visual.cargo(),state.getPartialTick(),poses,tasks,state.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,camera);poses.popPose();
    }
    private static final class Model extends GeoModel<WagonEntity> {
        public Identifier getModelResource(GeoRenderState state) { return WagonVisual.model(state); }
        public Identifier getTextureResource(GeoRenderState state) { return MaterialRenderer.ATLAS; }
        public Identifier getAnimationResource(WagonEntity entity) { return Identifier.fromNamespaceAndPath("tm_wagon","assembly"); }
    }
}
