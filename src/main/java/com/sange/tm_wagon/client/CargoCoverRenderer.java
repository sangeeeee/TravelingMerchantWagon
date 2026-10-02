package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sange.tm_wagon.assembly.WagonPart;
import com.sange.tm_wagon.cargo.CargoCover;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Two baked meshes reused across every row count, body size and wagon pose. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class CargoCoverRenderer {
    private static final ModelResourceLocation SHEET=model("cargo_cover_sheet"),ROLL=model("cargo_cover_roll");
    private static ModelResourceLocation model(String name) { return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("tm_wagon","block/"+name)); }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) { event.register(SHEET);event.register(ROLL); }
    public static void render(CargoCover cover,WagonPart body,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        if(!cover.installed())return;
        for(int row=cover.openRows();row<body.cargoCapacity()/2;row++) {
            double front=cover.boundary(row,body),back=cover.boundary(row+1,body);
            draw(SHEET,poses,buffers,light,overlay,-CargoCover.HALF_WIDTH,CargoCover.Y,front,CargoCover.HALF_WIDTH*2,1,back-front);
        }
        if(cover.openRows()>0) {
            double r=cover.radius();
            draw(ROLL,poses,buffers,light,overlay,-CargoCover.HALF_WIDTH,CargoCover.TOP,cover.rollZ(body)-r,CargoCover.HALF_WIDTH*2,r*2,r*2);
        }
    }
    private static void draw(ModelResourceLocation id,PoseStack poses,MultiBufferSource buffers,int light,int overlay,
            double x,double y,double z,double sx,double sy,double sz) {
        var mc=Minecraft.getInstance();poses.pushPose();poses.translate(x,y,z);poses.scale((float)sx,(float)sy,(float)sz);
        mc.getBlockRenderer().getModelRenderer().renderModel(poses.last(),buffers.getBuffer(RenderType.cutout()),null,
            mc.getModelManager().getModel(id),1,1,1,light,overlay);
        poses.popPose();
    }
    private CargoCoverRenderer() {}
}
