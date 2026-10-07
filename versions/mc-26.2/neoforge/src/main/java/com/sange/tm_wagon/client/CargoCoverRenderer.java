package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sange.tm_wagon.assembly.WagonPart;
import com.sange.tm_wagon.cargo.CargoCover;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Shared baked meshes reused across every row count, body size and wagon pose. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class CargoCoverRenderer {
    private static final Identifier SHEET=model("cargo_cover_sheet"),BACK_HEM=model("cargo_cover_back_hem"),ROLL=model("cargo_cover_roll");
    private static final java.util.Map<WagonPart,CachedMesh[]> CACHE=new java.util.EnumMap<>(WagonPart.class);
    static void clear() { CACHE.clear(); }
    private static Identifier model(String name) { return StandaloneModels.id("block/"+name); }
    @SubscribeEvent public static void models(ModelEvent.RegisterStandalone event) { StandaloneModels.register(event,SHEET);StandaloneModels.register(event,BACK_HEM);StandaloneModels.register(event,ROLL); }
    public static void render(CargoCover cover,WagonPart body,PoseStack poses,SubmitNodeCollector buffers,int light,int overlay) {
        if(!cover.installed())return;
        int colour=FabricColours.tint(cover.material().colour());
        var states=CACHE.computeIfAbsent(body,b->new CachedMesh[b.rows()+1]);
        var mesh=states[cover.openRows()];
        if(mesh==null)states[cover.openRows()]=mesh=bake(cover,body);
        var selected=mesh;buffers.submitCustomGeometry(poses,net.minecraft.client.renderer.Sheets.cutoutBlockItemSheet(),(pose,buffer)->selected.render(pose,buffer,light,overlay,-1,colour));
    }
    private static CachedMesh bake(CargoCover cover,WagonPart body) {
        var builder=new CachedMesh.Builder();
        for(int row=cover.openRows();row<body.rows();row++) {
            double front=cover.boundary(row,body),back=cover.boundary(row+1,body);
            double referenceDepth=row==0?.73375:row==body.rows()-1?.85375:.7;
            append(builder,SHEET,-CargoCover.halfWidth(body),CargoCover.Y,front,CargoCover.halfWidth(body)*2,1,back-front,referenceDepth);
        }
        if(cover.openRows()<body.rows())
            append(builder,BACK_HEM,-CargoCover.halfWidth(body),CargoCover.Y,cover.back(body)-.016,CargoCover.halfWidth(body)*2,1,.016,.016);
        if(cover.openRows()>0) {
            double r=cover.radius();
            append(builder,ROLL,-CargoCover.halfWidth(body),CargoCover.TOP,cover.rollZ(body)-r,CargoCover.halfWidth(body)*2,r*2,r*2,r*2);
        }
        return builder.build();
    }
    private static void append(CachedMesh.Builder builder,Identifier id,
            double x,double y,double z,double sx,double sy,double sz,double referenceDepth) {
        var mc=Minecraft.getInstance();var poses=new PoseStack();poses.translate(x,y,z);poses.scale((float)sx,(float)sy,(float)sz);
        var model=TextureTiling.model(StandaloneModels.get(id),new net.minecraft.world.phys.Vec3(sx,sy,sz),new net.minecraft.world.phys.Vec3(CargoCover.halfWidth(WagonPart.CARGO_BODY)*2,sy,referenceDepth));
        builder.model(model,poses.last());
    }
    private CargoCoverRenderer() {}
}
