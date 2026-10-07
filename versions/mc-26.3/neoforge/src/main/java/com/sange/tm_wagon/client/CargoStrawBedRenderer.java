package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** One shared authored mesh; the native straw-bed item and world model remain unchanged. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class CargoStrawBedRenderer {
    private static final Identifier MODEL=StandaloneModels.id("block/cargo_straw_bed");
    private static CachedMesh mesh;
    static void clear() { mesh=null; }
    @SubscribeEvent public static void models(ModelEvent.RegisterStandalone event) { StandaloneModels.register(event,MODEL); }
    public static void render(PoseStack poses,SubmitNodeCollector buffers,int light,int overlay) {
        if(mesh==null) {
            var builder=new CachedMesh.Builder();builder.model(StandaloneModels.get(MODEL),new PoseStack().last());mesh=builder.build();
        }
        var selected=mesh;
        buffers.submitCustomGeometry(poses,net.minecraft.client.renderer.Sheets.cutoutBlockItemSheet(),
            (pose,buffer)->selected.render(pose,buffer,light,overlay,-1,-1));
    }
    private CargoStrawBedRenderer() {}
}
