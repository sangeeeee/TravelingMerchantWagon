package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.assembly.AssemblyFrameBlockEntity;
import com.sange.tm_wagon.assembly.WagonPart;
import com.sange.tm_wagon.assembly.WagonSlot;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class AssemblyRenderer extends GeoBlockRenderer<AssemblyFrameBlockEntity> {
    private static final java.util.Set<String> MOVING_BONES=java.util.Set.of("front_axle","shafts","front_left_wheel","front_right_wheel","rear_left_wheel","rear_right_wheel");
    public AssemblyRenderer() { super(new Model()); }
    @Override public void render(AssemblyFrameBlockEntity frame, float tick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        super.render(frame,tick,poses,buffers,light,overlay);
    }
    @Override public void renderRecursively(PoseStack poses,AssemblyFrameBlockEntity frame,
            software.bernie.geckolib.cache.object.GeoBone bone,net.minecraft.client.renderer.RenderType type,
            MultiBufferSource buffers,com.mojang.blaze3d.vertex.VertexConsumer buffer,boolean reRender,
            float tick,int light,int overlay,int colour) {
        // GeckoLib may reuse an animation evaluation within the same tick.
        // Reapply the authoritative pose immediately before drawing the frame.
        if (bone.getName().equals("frame_root")) { bone.setHidden(false); ((Model)getGeoModel()).applyFramePose(frame,tick); }
        if(MOVING_BONES.contains(bone.getName())) {
            var initial=bone.getInitialSnapshot();bone.updateRotation(initial.getRotX(),initial.getRotY(),initial.getRotZ());
        }
        super.renderRecursively(poses,frame,bone,type,buffers,buffer,reRender,tick,light,overlay,colour);
    }
    @Override public int getViewDistance() { return 128; }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(AssemblyFrameBlockEntity frame) {
        return new net.minecraft.world.phys.AABB(frame.getBlockPos()).inflate(7,3,7);
    }

    private static class Model extends GeoModel<AssemblyFrameBlockEntity> {
        private ResourceLocation resource(String path) { return ResourceLocation.fromNamespaceAndPath(TravelingMerchantWagon.MODID,path); }
        @Override public ResourceLocation getModelResource(AssemblyFrameBlockEntity frame) {
            if (!frame.has(WagonSlot.BODY)) return resource("geo/wagon_assembly_frame.geo.json");
            String seat = frame.part(WagonSlot.SEAT) == WagonPart.DOUBLE_SEAT ? "double_seat" : "single_seat";
            String shafts = frame.part(WagonSlot.SHAFTS) == WagonPart.DOUBLE_HORSE_SHAFTS ? "double_horse" : "single_horse";
            return resource("geo/assembly/"+seat+"_"+shafts+".geo.json");
        }
        @Override public ResourceLocation getTextureResource(AssemblyFrameBlockEntity frame) { return resource("textures/entity/wagon.png"); }
        @Override public ResourceLocation getAnimationResource(AssemblyFrameBlockEntity frame) { return resource("animations/assembly.animation.json"); }
        @Override public void setCustomAnimations(AssemblyFrameBlockEntity frame, long id, AnimationState<AssemblyFrameBlockEntity> state) {
            hidden("frame_root",false);
            applyFramePose(frame,state.getPartialTick());
            // Geo models are shared between instances; reset every optional bone.
            hidden("seat",!frame.has(WagonSlot.SEAT)); hidden("shafts",!frame.has(WagonSlot.SHAFTS));
            hidden("front_left_wheel",!frame.has(WagonSlot.FRONT_LEFT));
            hidden("front_right_wheel",!frame.has(WagonSlot.FRONT_RIGHT));
            hidden("rear_left_wheel",!frame.has(WagonSlot.REAR_LEFT));
            hidden("rear_right_wheel",!frame.has(WagonSlot.REAR_RIGHT));
        }
        private void applyFramePose(AssemblyFrameBlockEntity frame,float partialTick) {
            double progress=frame.frameProgress(partialTick);
            double delta=Math.toRadians((com.sange.tm_wagon.assembly.FrameMotion.EXTENDED_ANGLE-com.sange.tm_wagon.assembly.FrameMotion.FOLDED_ANGLE)*progress);
            // Use the server timeline after animation evaluation: reversals and
            // late chunk loads keep the same pose on every client.
            getBone("frame_grid_platform").ifPresent(b -> b.setPosY((float)com.sange.tm_wagon.assembly.FrameMotion.platformOffset(progress)));
            for (String end : new String[]{"front","rear"}) for (String side : new String[]{"left","right"}) {
                double sign=side.equals("left")?-1:1;
                getBone("frame_"+end+"_"+side+"_lower").ifPresent(b -> b.setRotZ(b.getInitialSnapshot().getRotZ()+(float)(sign*delta)));
                getBone("frame_"+end+"_"+side+"_upper").ifPresent(b -> b.setRotZ(b.getInitialSnapshot().getRotZ()-(float)(sign*2*delta)));
            }
        }
        private void hidden(String name, boolean hide) { getBone(name).ifPresent(bone -> bone.setHidden(hide)); }
    }
}
