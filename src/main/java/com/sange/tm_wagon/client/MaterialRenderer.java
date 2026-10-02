package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.material.WagonMaterial;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.renderer.GeoRenderer;

/** One packed texture, immutable per-material UV caches, no whole-wagon combinations. */
public final class MaterialRenderer {
    public static final ResourceLocation ATLAS=ResourceLocation.fromNamespaceAndPath("tm_wagon","textures/entity/component_atlas.png");
    private record Baked(GeoCube cube,boolean wool) {}
    @SuppressWarnings("unchecked") private static final Map<GeoCube,Baked>[] CACHE=new Map[101];
    public static void clear() { java.util.Arrays.fill(CACHE,null); }
    public static WagonSlot slot(String bone) {
        return switch(bone) {
            case "seat"->WagonSlot.SEAT;case "shafts"->WagonSlot.SHAFTS;
            case "front_left_wheel"->WagonSlot.FRONT_LEFT;case "front_right_wheel"->WagonSlot.FRONT_RIGHT;
            case "rear_left_wheel"->WagonSlot.REAR_LEFT;case "rear_right_wheel"->WagonSlot.REAR_RIGHT;
            default->bone.startsWith("frame_")?null:WagonSlot.BODY;
        };
    }
    private static int index(WagonPart part,WagonMaterial material) {
        if(part==null||!WagonMaterial.wooden(part))return 100;
        int kind=switch(part) {
            case CARGO_BODY->0;case LONG_CARGO_BODY->1;case SINGLE_SEAT->2;case DOUBLE_SEAT->3;
            case SMALL_WHEEL->4;case LARGE_WHEEL->5;case SINGLE_WOODEN_SEAT->6;case DOUBLE_WOODEN_SEAT->7;
            default->throw new IllegalArgumentException("Unmapped material part");
        };return kind*10+material.wood().ordinal();
    }
    public static void cubes(GeoRenderer<?> renderer,PoseStack poses,GeoBone bone,VertexConsumer buffer,int light,int overlay,int colour,WagonPart part,WagonMaterial material) {
        if(bone.isHidden())return;
        int tile=index(part,material);var cache=CACHE[tile];if(cache==null)CACHE[tile]=cache=new IdentityHashMap<>();
        for(GeoCube cube:bone.getCubes()) {
            var baked=cache.get(cube);if(baked==null) { baked=bake(cube,tile,part!=null&&WagonMaterial.cushioned(part));cache.put(cube,baked); }
            poses.pushPose();renderer.renderCube(poses,baked.cube,buffer,light,overlay,baked.wool?FastColor.ARGB32.multiply(colour,material.colour().getTextureDiffuseColor()):colour);poses.popPose();
        }
    }
    private static Baked bake(GeoCube cube,int tile,boolean dyeable) {
        var quads=new GeoQuad[cube.quads().length];boolean wool=dyeable;
        float x=(tile%16)*64,y=(tile/16)*64;
        for(int q=0;q<quads.length;q++) {
            var quad=cube.quads()[q];if(quad==null)continue;var vertices=new GeoVertex[quad.vertices().length];
            for(int v=0;v<vertices.length;v++) {
                var original=quad.vertices()[v];float u=original.texU()*64,t=original.texV()*64;
                wool&=u>=48-.001&&u<=64+.001&&t>=16-.001&&t<=32+.001;
                vertices[v]=original.withUVs((x+u)/1024,(y+t)/512);
            }
            quads[q]=new GeoQuad(vertices,quad.normal(),quad.direction());
        }
        return new Baked(new GeoCube(quads,cube.pivot(),cube.rotation(),cube.size(),cube.inflate(),cube.mirror()),wool);
    }
    private MaterialRenderer() {}
}
