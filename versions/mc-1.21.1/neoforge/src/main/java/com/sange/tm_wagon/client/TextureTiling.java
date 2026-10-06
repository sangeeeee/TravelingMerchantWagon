package com.sange.tm_wagon.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

/** Repeats each face's own UV rectangle, never adjacent atlas regions. Render-only, cached at size changes. */
public final class TextureTiling {
    private record Key(BakedModel model,Vec3 actual,Vec3 reference) {}
    private static final Map<Key,BakedModel> MODELS=new HashMap<>();
    public static void clear() { MODELS.clear(); }
    private static double ratio(Vec3 edge,Vec3 actual,Vec3 reference) {
        double baseline=edge.multiply(reference).length();
        return baseline<1e-9?1:edge.multiply(actual).length()/baseline;
    }
    private static int count(double repeats) { return Math.max(1,(int)Math.ceil(repeats-1e-6)); }
    private static double end(int i,double repeats) { return Math.min(1,(i+1)/repeats); }
    private static Vec3 position(GeoVertex v) { return new Vec3(v.position().x,v.position().y,v.position().z); }
    private static Vec3 interpolate(Vec3 a,Vec3 b,Vec3 d,double s,double t) {
        return a.add(b.subtract(a).scale(s)).add(d.subtract(a).scale(t));
    }
    public static List<GeoQuad> geo(GeoQuad quad,Vec3 actual,Vec3 reference) {
        var v=quad.vertices();Vec3 a=position(v[0]),b=position(v[1]),d=position(v[3]);
        double rs=ratio(b.subtract(a),actual,reference),rt=ratio(d.subtract(a),actual,reference);
        if(Math.abs(rs-1)<1e-6&&Math.abs(rt-1)<1e-6)return List.of(quad);
        var result=new ArrayList<GeoQuad>(count(rs)*count(rt));
        for(int i=0;i<count(rs);i++)for(int j=0;j<count(rt);j++) {
            double s0=i/rs,s1=end(i,rs),t0=j/rt,t1=end(j,rt);
            double[][] corners={{s0,t0,0,0},{s1,t0,(s1-s0)*rs,0},{s1,t1,(s1-s0)*rs,(t1-t0)*rt},{s0,t1,0,(t1-t0)*rt}};
            var vertices=new GeoVertex[4];
            for(int k=0;k<4;k++) {
                var p=interpolate(a,b,d,corners[k][0],corners[k][1]);double u=corners[k][2],t=corners[k][3];
                vertices[k]=new GeoVertex(new Vector3f((float)p.x,(float)p.y,(float)p.z),
                    (float)(v[0].texU()+(v[1].texU()-v[0].texU())*u+(v[3].texU()-v[0].texU())*t),
                    (float)(v[0].texV()+(v[1].texV()-v[0].texV())*u+(v[3].texV()-v[0].texV())*t));
            }
            result.add(new GeoQuad(vertices,quad.normal(),quad.direction()));
        }
        return result;
    }
    private static Vec3 position(int[] data,int vertex,int stride) {
        int i=vertex*stride;return new Vec3(Float.intBitsToFloat(data[i]),Float.intBitsToFloat(data[i+1]),Float.intBitsToFloat(data[i+2]));
    }
    public static List<BakedQuad> nativeQuad(BakedQuad quad,Vec3 actual,Vec3 reference) {
        var data=quad.getVertices();int stride=data.length/4;
        Vec3 a=position(data,0,stride),b=position(data,1,stride),d=position(data,3,stride);
        double rs=ratio(b.subtract(a),actual,reference),rt=ratio(d.subtract(a),actual,reference);
        if(Math.abs(rs-1)<1e-6&&Math.abs(rt-1)<1e-6)return List.of(quad);
        var result=new ArrayList<BakedQuad>(count(rs)*count(rt));
        for(int i=0;i<count(rs);i++)for(int j=0;j<count(rt);j++) {
            double s0=i/rs,s1=end(i,rs),t0=j/rt,t1=end(j,rt);
            double[][] corners={{s0,t0,0,0},{s1,t0,(s1-s0)*rs,0},{s1,t1,(s1-s0)*rs,(t1-t0)*rt},{s0,t1,0,(t1-t0)*rt}};
            int[] output=data.clone();
            for(int k=0;k<4;k++) {
                int offset=k*stride;var p=interpolate(a,b,d,corners[k][0],corners[k][1]);
                output[offset]=Float.floatToRawIntBits((float)p.x);output[offset+1]=Float.floatToRawIntBits((float)p.y);output[offset+2]=Float.floatToRawIntBits((float)p.z);
                for(int uv=4;uv<=5;uv++) {
                    float start=Float.intBitsToFloat(data[uv]),s=Float.intBitsToFloat(data[stride+uv])-start,t=Float.intBitsToFloat(data[3*stride+uv])-start;
                    output[offset+uv]=Float.floatToRawIntBits((float)(start+s*corners[k][2]+t*corners[k][3]));
                }
            }
            result.add(new BakedQuad(output,quad.getTintIndex(),quad.getDirection(),quad.getSprite(),quad.isShade(),quad.hasAmbientOcclusion()));
        }
        return result;
    }
    public static BakedModel model(BakedModel original,Vec3 actual,Vec3 reference) {
        if(actual.equals(reference))return original;
        return MODELS.computeIfAbsent(new Key(original,actual,reference),key->new TiledModel(original,actual,reference));
    }
    private static final class TiledModel extends BakedModelWrapper<BakedModel> {
        private final Map<Direction,List<BakedQuad>> sided=new EnumMap<>(Direction.class);
        private final List<BakedQuad> general;
        TiledModel(BakedModel original,Vec3 actual,Vec3 reference) {
            super(original);
            general=tile(original,null,actual,reference);
            for(var side:Direction.values())sided.put(side,tile(original,side,actual,reference));
        }
        private static List<BakedQuad> tile(BakedModel original,Direction side,Vec3 actual,Vec3 reference) {
            var output=new ArrayList<BakedQuad>();
            for(var quad:original.getQuads(null,side,RandomSource.create(42),ModelData.EMPTY,null))output.addAll(nativeQuad(quad,actual,reference));
            return List.copyOf(output);
        }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random) { return side==null?general:sided.get(side); }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,net.minecraft.client.renderer.RenderType type) { return getQuads(state,side,random); }
    }
    private TextureTiling() {}
}
