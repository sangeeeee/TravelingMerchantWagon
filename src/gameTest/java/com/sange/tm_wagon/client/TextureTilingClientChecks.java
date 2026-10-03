package com.sange.tm_wagon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

/** Verifies fractional tiles, reversed/rotated UVs, atlas isolation and immutable mesh reuse. */
final class TextureTilingClientChecks {
    static void verify() {
        var quad=new GeoQuad(new GeoVertex[]{
            new GeoVertex(new Vector3f(0,0,0),.75F,.875F),new GeoVertex(new Vector3f(3.6F,0,0),.75F,.5F),
            new GeoVertex(new Vector3f(3.6F,2.4F,0),.625F,.5F),new GeoVertex(new Vector3f(0,2.4F,0),.625F,.875F)
        },new Vector3f(0,0,1),Direction.SOUTH);
        var faces=TextureTiling.geo(quad,new Vec3(1,1,1),new Vec3(1/2.4,1/1.2,1));
        require(faces.size()==6,"Non-integer repeat count lost a partial tile");
        double area=0;
        for(var face:faces) {
            var v=face.vertices();var a=v[0].position();var b=v[1].position();var d=v[3].position();
            area+=new Vector3f(b).sub(a).cross(new Vector3f(d).sub(a)).length();
            require(face.normal()==quad.normal()&&face.direction()==quad.direction(),"Tiling changed face orientation");
            for(var vertex:v)require(vertex.texU()>=.625F-1e-6&&vertex.texU()<=.75F+1e-6&&vertex.texV()>=.5F-1e-6&&vertex.texV()<=.875F+1e-6,"Tiled UV escaped its original material rectangle");
            double density=Math.abs(v[1].texV()-v[0].texV())/a.distance(b);
            require(Math.abs(density-.375/1.5)<1e-6,"Stretched face still magnifies its texels");
        }
        require(Math.abs(area-3.6*2.4)<1e-5,"Tiling changed the model footprint or left gaps");
        var cropped=TextureTiling.geo(quad,new Vec3(.6,1,1),new Vec3(1,1,1));
        require(cropped.size()==1&&Math.abs(cropped.getFirst().vertices()[1].texV()-(.875-.375*.6))<1e-6,"Smaller faces squeeze instead of crop the reference texture");
        require(TextureTiling.geo(quad,new Vec3(1,1,1),new Vec3(1,1,1)).getFirst()==quad,"Ordinary faces were needlessly rebuilt");
        var mc=Minecraft.getInstance();var id=ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("tm_wagon","block/canopy_shell"));
        var original=mc.getModelManager().getModel(id);var actual=new Vec3(3.5,2,.7);var reference=new Vec3(2,2,.7);
        var tiled=TextureTiling.model(original,actual,reference);
        require(tiled==TextureTiling.model(original,actual,reference),"Native meshes were rebuilt every frame");
        for(Direction side:new Direction[]{null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})
            for(var source:original.getQuads(null,side,RandomSource.create(42)))checkNative(source,actual,reference);
        try(var reader=mc.getResourceManager().openAsReader(ResourceLocation.fromNamespaceAndPath("tm_wagon","parts.json"))) {
            var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            for(String name:new String[]{"long_cargo_body","wide_cargo_body","triple_seat","triple_wooden_seat"})
                require(json.getAsJsonObject(name).getAsJsonObject("uv_reference").size()>0,"Missing reference dimensions for "+name);
        } catch(java.io.IOException error) { throw new IllegalStateException(error); }
        com.mojang.logging.LogUtils.getLogger().info("TM_WAGON_TEXTURE_DENSITY_PASS: fractional/reversed UVs, cropped faces, original atlas bounds and cached native meshes");
    }
    private static Vector3f point(int[] data,int vertex,int stride) {
        int offset=vertex*stride;return new Vector3f(Float.intBitsToFloat(data[offset]),Float.intBitsToFloat(data[offset+1]),Float.intBitsToFloat(data[offset+2]));
    }
    private static void checkNative(BakedQuad source,Vec3 actual,Vec3 reference) {
        var original=source.getVertices();int stride=original.length/4;double area=0;
        float[] low={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY},high={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(int v=0;v<4;v++)for(int uv=0;uv<2;uv++) {
            float value=Float.intBitsToFloat(original[v*stride+4+uv]);low[uv]=Math.min(low[uv],value);high[uv]=Math.max(high[uv],value);
        }
        for(var face:TextureTiling.nativeQuad(source,actual,reference)) {
            require(face.getTintIndex()==source.getTintIndex()&&face.getSprite()==source.getSprite()&&face.getDirection()==source.getDirection()&&face.isShade()==source.isShade(),"Native tiling lost dye or lighting information");
            var data=face.getVertices();Vector3f a=point(data,0,stride),b=point(data,1,stride),d=point(data,3,stride);
            area+=new Vector3f(b).sub(a).cross(new Vector3f(d).sub(a)).length();
            for(int v=0;v<4;v++)for(int uv=0;uv<2;uv++) {
                float value=Float.intBitsToFloat(data[v*stride+4+uv]);require(value>=low[uv]-1e-6&&value<=high[uv]+1e-6,"Native tiling leaked into neighbouring atlas sprites");
            }
        }
        Vector3f a=point(original,0,stride),b=point(original,1,stride),d=point(original,3,stride);
        require(Math.abs(area-new Vector3f(b).sub(a).cross(new Vector3f(d).sub(a)).length())<1e-5,"Native tiling moved canopy seams");
    }
    private static void require(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }
    private TextureTilingClientChecks() {}
}
