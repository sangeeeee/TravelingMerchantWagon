package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sange.tm_wagon.cargo.WagonCabinet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Six meshes per wood family; drawers move without rebuilding collision or sending tick packets. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class CabinetRenderer {
    private static final ModelResourceLocation[][][] MODELS=new ModelResourceLocation[10][2][3];
    static {
        for(var wood:com.sange.tm_wagon.material.WoodMaterial.values())for(int size=0;size<2;size++)for(int part=0;part<3;part++)MODELS[wood.ordinal()][size][part]=ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("tm_wagon","block/material/cabinet_"+wood.getSerializedName()+"_"+(size==0?"single":"double")+"_"+new String[]{"body","left","right"}[part]));
    }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) { for(var wood:MODELS)for(var size:wood)for(var id:size)event.register(id); }
    public static void render(WagonCabinet cabinet,float tick,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        if(!cabinet.installed())return;
        var models=MODELS[cabinet.material().wood().ordinal()][cabinet.rows()==6?1:0];poses.pushPose();poses.translate(0,0,cabinet.frontOffset());
        double scale=cabinet.box().getXsize()/(cabinet.rows()==6?31.0/16:17.0/16);
        poses.scale((float)scale,1,1);
        draw(models[0],poses,buffers,light,overlay,0,scale);
        draw(models[1],poses,buffers,light,overlay,-.4*cabinet.progress(0,tick)/scale,scale);
        draw(models[2],poses,buffers,light,overlay,.4*cabinet.progress(1,tick)/scale,scale);
        poses.popPose();
    }
    private static void draw(ModelResourceLocation id,PoseStack poses,MultiBufferSource buffers,int light,int overlay,double x,double scale) {
        poses.pushPose();poses.translate(x-.5,1.5,-2.5);
        var mc=Minecraft.getInstance();var model=TextureTiling.model(mc.getModelManager().getModel(id),new net.minecraft.world.phys.Vec3(scale,1,1),new net.minecraft.world.phys.Vec3(1,1,1));
        mc.getBlockRenderer().getModelRenderer().renderModel(poses.last(),buffers.getBuffer(RenderType.cutout()),null,model,1,1,1,light,overlay);
        poses.popPose();
    }
    private CabinetRenderer() {}
}
