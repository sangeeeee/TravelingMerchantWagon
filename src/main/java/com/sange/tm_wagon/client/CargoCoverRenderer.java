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

/** Shared baked meshes reused across every row count, body size and wagon pose. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class CargoCoverRenderer {
    private static final ModelResourceLocation SHEET=model("cargo_cover_sheet"),BACK_HEM=model("cargo_cover_back_hem"),ROLL=model("cargo_cover_roll");
    private static ModelResourceLocation model(String name) { return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("tm_wagon","block/"+name)); }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) { event.register(SHEET);event.register(BACK_HEM);event.register(ROLL); }
    public static void render(CargoCover cover,WagonPart body,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        if(!cover.installed())return;
        int colour=FabricColours.tint(cover.material().colour());
        for(int row=cover.openRows();row<body.rows();row++) {
            double front=cover.boundary(row,body),back=cover.boundary(row+1,body);
            double referenceDepth=row==0?.73375:row==body.rows()-1?.85375:.7;
            draw(colour,SHEET,poses,buffers,light,overlay,-CargoCover.halfWidth(body),CargoCover.Y,front,CargoCover.halfWidth(body)*2,1,back-front,referenceDepth);
        }
        if(cover.openRows()<body.rows())
            draw(colour,BACK_HEM,poses,buffers,light,overlay,-CargoCover.halfWidth(body),CargoCover.Y,cover.back(body)-.016,CargoCover.halfWidth(body)*2,1,.016,.016);
        if(cover.openRows()>0) {
            double r=cover.radius();
            draw(colour,ROLL,poses,buffers,light,overlay,-CargoCover.halfWidth(body),CargoCover.TOP,cover.rollZ(body)-r,CargoCover.halfWidth(body)*2,r*2,r*2,r*2);
        }
    }
    private static void draw(int colour,ModelResourceLocation id,PoseStack poses,MultiBufferSource buffers,int light,int overlay,
            double x,double y,double z,double sx,double sy,double sz,double referenceDepth) {
        var mc=Minecraft.getInstance();poses.pushPose();poses.translate(x,y,z);poses.scale((float)sx,(float)sy,(float)sz);
        var model=TextureTiling.model(mc.getModelManager().getModel(id),new net.minecraft.world.phys.Vec3(sx,sy,sz),new net.minecraft.world.phys.Vec3(CargoCover.halfWidth(WagonPart.CARGO_BODY)*2,sy,referenceDepth));
        mc.getBlockRenderer().getModelRenderer().renderModel(poses.last(),buffers.getBuffer(RenderType.cutout()),null,
            model,((colour>>16)&255)/255F,((colour>>8)&255)/255F,(colour&255)/255F,light,overlay);
        poses.popPose();
    }
    private CargoCoverRenderer() {}
}
