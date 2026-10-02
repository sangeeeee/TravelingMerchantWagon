package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sange.tm_wagon.cargo.CargoCanopy;
import com.sange.tm_wagon.assembly.WagonPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Five baked meshes reused for both lengths and all four independent curtain states. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class CanopyRenderer {
    private static final ModelResourceLocation SHELL=model("shell"),RIB=model("rib"),END=model("end"),CLOSED=model("curtain_closed"),OPEN=model("curtain_open");
    private static ModelResourceLocation model(String name) { return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("tm_wagon","block/canopy_"+name)); }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) { for(var id:new ModelResourceLocation[]{SHELL,RIB,END,CLOSED,OPEN})event.register(id); }
    public static void render(CargoCanopy canopy,WagonPart body,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        if(!canopy.installed())return;
        int colour=FabricColours.tint(canopy.material().colour());
        int rows=body.cargoCapacity()/2;
        for(int row=0;row<rows;row++) {
            double z=boundary(row,rows,canopy,body),end=boundary(row+1,rows,canopy,body);
            draw(colour,SHELL,poses,buffers,light,overlay,z,end-z);
        }
        for(int row=0;row<=rows;row++) {
            double z=boundary(row,rows,canopy,body)+(row==0?.065:row==rows?-.065:0);
            draw(colour,RIB,poses,buffers,light,overlay,z,1);
        }
        draw(colour,END,poses,buffers,light,overlay,CargoCanopy.FRONT+CargoCanopy.THICK/4,1);
        draw(colour,END,poses,buffers,light,overlay,canopy.back(body)-CargoCanopy.THICK-CargoCanopy.THICK/4,1);
        for(boolean front:new boolean[]{true,false})draw(colour,canopy.closed(front)?CLOSED:OPEN,poses,buffers,light,overlay,canopy.curtainZ(body,front),1);
    }
    private static double boundary(int row,int rows,CargoCanopy canopy,WagonPart body) {
        return row==0?CargoCanopy.FRONT:row==rows?canopy.back(body):-1.31+row*.7;
    }
    private static void draw(int colour,ModelResourceLocation id,PoseStack poses,MultiBufferSource buffers,int light,int overlay,double z,double depth) {
        var mc=Minecraft.getInstance();poses.pushPose();poses.translate(-1,CargoCanopy.BASE,z);poses.scale(2,2,(float)depth);
        mc.getBlockRenderer().getModelRenderer().renderModel(poses.last(),buffers.getBuffer(RenderType.cutout()),null,mc.getModelManager().getModel(id),((colour>>16)&255)/255F,((colour>>8)&255)/255F,(colour&255)/255F,light,overlay);
        poses.popPose();
    }
    private CanopyRenderer() {}
}
