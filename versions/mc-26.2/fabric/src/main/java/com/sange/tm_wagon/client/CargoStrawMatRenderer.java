package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;

/** One shared authored mesh; the native straw-bed item and world model remain unchanged. */

public final class CargoStrawMatRenderer {
    private static final Identifier MODEL=StandaloneModels.id("block/cargo_straw_mat");
    private static CachedMesh mesh;
    static void clear() { mesh=null; }
    public static void models(net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.Context event) { StandaloneModels.register(event,MODEL); }
    public static void render(PoseStack poses,SubmitNodeCollector buffers,int light,int overlay) {
        if(mesh==null) {
            var builder=new CachedMesh.Builder();builder.model(StandaloneModels.get(MODEL),new PoseStack().last());mesh=builder.build();
        }
        var selected=mesh;
        buffers.submitCustomGeometry(poses,net.minecraft.client.renderer.Sheets.cutoutBlockItemSheet(),
            (pose,buffer)->selected.render(pose,buffer,light,overlay,-1,-1));
    }
    private CargoStrawMatRenderer() {}
}
