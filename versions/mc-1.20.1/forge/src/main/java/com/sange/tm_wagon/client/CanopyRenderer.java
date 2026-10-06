package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sange.tm_wagon.cargo.CargoCanopy;
import com.sange.tm_wagon.assembly.WagonPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;

/** Cached shell per body size, with independently selected front/rear curtain meshes. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class CanopyRenderer {
    private static final ResourceLocation SHELL=model("shell"),RIB=model("rib"),END=model("end"),CLOSED=model("curtain_closed"),OPEN=model("curtain_open");
    private record Meshes(CachedMesh shell,CachedMesh frontOpen,CachedMesh frontClosed,CachedMesh backOpen,CachedMesh backClosed) {}
    private static final java.util.Map<WagonPart,Meshes> CACHE=new java.util.EnumMap<>(WagonPart.class);
    static void clear() { CACHE.clear(); }
    private static ResourceLocation model(String name) { return new ResourceLocation("tm_wagon","block/canopy_"+name); }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) { for(var id:new ResourceLocation[]{SHELL,RIB,END,CLOSED,OPEN})event.register(id); }
    public static void render(CargoCanopy canopy,WagonPart body,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        if(!canopy.installed())return;
        int colour=FabricColours.tint(canopy.material().colour());
        var mesh=CACHE.computeIfAbsent(body,b->bake(canopy,b));
        var buffer=buffers.getBuffer(RenderType.cutout());
        mesh.shell.render(poses.last(),buffer,light,overlay,-1,colour);
        (canopy.closed(true)?mesh.frontClosed:mesh.frontOpen).render(poses.last(),buffer,light,overlay,-1,colour);
        (canopy.closed(false)?mesh.backClosed:mesh.backOpen).render(poses.last(),buffer,light,overlay,-1,colour);
    }
    private static Meshes bake(CargoCanopy canopy,WagonPart body) {
        var builder=new CachedMesh.Builder();int rows=body.rows();
        for(int row=0;row<rows;row++) {
            double z=boundary(row,rows,canopy,body),end=boundary(row+1,rows,canopy,body);
            double referenceDepth=row==0?.73375:row==rows-1?.713125:.7;
            append(builder,body.widthScale(),SHELL,z,end-z,referenceDepth);
        }
        for(int row=0;row<=rows;row++) {
            double z=boundary(row,rows,canopy,body)+(row==0?.065:row==rows?-.065:0);
            append(builder,body.widthScale(),RIB,z,1,1);
        }
        append(builder,body.widthScale(),END,com.sange.tm_wagon.cargo.CargoCover.front(body)+CargoCanopy.THICK/4,1,1);
        append(builder,body.widthScale(),END,canopy.back(body)-CargoCanopy.THICK-CargoCanopy.THICK/4,1,1);
        return new Meshes(builder.build(),curtain(canopy,body,true,false),curtain(canopy,body,true,true),curtain(canopy,body,false,false),curtain(canopy,body,false,true));
    }
    private static CachedMesh curtain(CargoCanopy canopy,WagonPart body,boolean front,boolean closed) {
        var builder=new CachedMesh.Builder();
        append(builder,body.widthScale(),closed?CLOSED:OPEN,canopy.curtainZ(body,front),1,1);
        return builder.build();
    }
    private static double boundary(int row,int rows,CargoCanopy canopy,WagonPart body) {
        return row==0?com.sange.tm_wagon.cargo.CargoCover.front(body):row==rows?canopy.back(body):body.firstRowZ()-.35+row*.7;
    }
    private static void append(CachedMesh.Builder builder,double width,ResourceLocation id,double z,double depth,double referenceDepth) {
        var mc=Minecraft.getInstance();var local=new PoseStack();local.translate(-width,CargoCanopy.BASE,z);local.scale((float)(2*width),2,(float)depth);
        var model=TextureTiling.model(mc.getModelManager().getModel(id),new net.minecraft.world.phys.Vec3(2*width,2,depth),new net.minecraft.world.phys.Vec3(2,2,referenceDepth));
        builder.model(model,local.last());
    }
    private CanopyRenderer() {}
}
