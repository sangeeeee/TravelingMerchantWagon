package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sange.tm_wagon.cargo.WagonCabinet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import net.minecraft.resources.Identifier;

/** Six meshes per wood family; drawers move without rebuilding collision or sending tick packets. */

public final class CabinetRenderer {
    private static final Identifier[][][] MODELS=new Identifier[com.sange.tm_wagon.material.WoodMaterial.values().length][2][3];
    static {
        for(var wood:com.sange.tm_wagon.material.WoodMaterial.values())for(int size=0;size<2;size++)for(int part=0;part<3;part++)MODELS[wood.ordinal()][size][part]=StandaloneModels.id("block/material/cabinet_"+wood.getSerializedName()+"_"+(size==0?"single":"double")+"_"+new String[]{"body","left","right"}[part]);
    }
    public static void models(net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.Context event) { for(var wood:MODELS)for(var size:wood)for(var id:size)StandaloneModels.register(event,id); }
    public static void render(WagonCabinet cabinet,float tick,PoseStack poses,SubmitNodeCollector buffers,int light,int overlay) {
        if(!cabinet.installed())return;
        var models=MODELS[cabinet.material().wood().ordinal()][cabinet.rows()==6?1:0];poses.pushPose();poses.translate(0,0,cabinet.frontOffset());
        double scale=cabinet.box().getXsize()/(cabinet.rows()==6?31.0/16:17.0/16);
        poses.scale((float)scale,1,1);
        draw(models[0],poses,buffers,light,overlay,0,scale);
        draw(models[1],poses,buffers,light,overlay,-.4*cabinet.progress(0,tick)/scale,scale);
        draw(models[2],poses,buffers,light,overlay,.4*cabinet.progress(1,tick)/scale,scale);
        poses.popPose();
    }
    private record Key(Identifier id,double scale) {}
    private static final java.util.Map<Key,CachedMesh> CACHE=new java.util.HashMap<>();
    public static void clear() { CACHE.clear(); }
    private static void draw(Identifier id,PoseStack poses,SubmitNodeCollector buffers,int light,int overlay,double x,double scale) {
        poses.pushPose();poses.translate(x-.5,1.5,-2.5);
        var mesh=CACHE.computeIfAbsent(new Key(id,scale),key->{
            var model=TextureTiling.model(StandaloneModels.get(id),new net.minecraft.world.phys.Vec3(scale,1,1),new net.minecraft.world.phys.Vec3(1,1,1));
            var builder=new CachedMesh.Builder();builder.model(model,new PoseStack().last());return builder.build();
        });
        buffers.submitCustomGeometry(poses,net.minecraft.client.renderer.Sheets.cutoutBlockItemSheet(),(pose,buffer)->mesh.render(pose,buffer,light,overlay,-1,-1));
        poses.popPose();
    }
    private CabinetRenderer() {}
}
