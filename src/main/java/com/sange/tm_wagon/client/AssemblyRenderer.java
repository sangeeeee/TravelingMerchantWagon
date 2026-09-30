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
    public AssemblyRenderer() { super(new Model()); }
    @Override public void render(AssemblyFrameBlockEntity frame, float tick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        if (frame.has(WagonSlot.BODY)) super.render(frame,tick,poses,buffers,light,overlay);
    }
    @Override public int getViewDistance() { return 128; }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(AssemblyFrameBlockEntity frame) {
        return new net.minecraft.world.phys.AABB(frame.getBlockPos()).inflate(7,3,7);
    }

    private static class Model extends GeoModel<AssemblyFrameBlockEntity> {
        private ResourceLocation resource(String path) { return ResourceLocation.fromNamespaceAndPath(TravelingMerchantWagon.MODID,path); }
        @Override public ResourceLocation getModelResource(AssemblyFrameBlockEntity frame) {
            String seat = frame.part(WagonSlot.SEAT) == WagonPart.DOUBLE_SEAT ? "double_seat" : "single_seat";
            String shafts = frame.part(WagonSlot.SHAFTS) == WagonPart.DOUBLE_HORSE_SHAFTS ? "double_horse" : "single_horse";
            return resource("geo/assembly/"+seat+"_"+shafts+".geo.json");
        }
        @Override public ResourceLocation getTextureResource(AssemblyFrameBlockEntity frame) { return resource("textures/entity/wagon.png"); }
        @Override public ResourceLocation getAnimationResource(AssemblyFrameBlockEntity frame) { return resource("animations/assembly.animation.json"); }
        @Override public void setCustomAnimations(AssemblyFrameBlockEntity frame, long id, AnimationState<AssemblyFrameBlockEntity> state) {
            // Geo models are shared between instances; reset every optional bone.
            hidden("seat",!frame.has(WagonSlot.SEAT)); hidden("shafts",!frame.has(WagonSlot.SHAFTS));
            hidden("front_left_wheel",!frame.has(WagonSlot.FRONT_LEFT));
            hidden("front_right_wheel",!frame.has(WagonSlot.FRONT_RIGHT));
            hidden("rear_left_wheel",!frame.has(WagonSlot.REAR_LEFT));
            hidden("rear_right_wheel",!frame.has(WagonSlot.REAR_RIGHT));
        }
        private void hidden(String name, boolean hide) { getBone(name).ifPresent(bone -> bone.setHidden(hide)); }
    }
}
