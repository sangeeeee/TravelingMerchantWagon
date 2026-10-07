package com.sange.tm_wagon.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;


import org.joml.Vector3f;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;

/** Repeats each face's own UV rectangle, never adjacent atlas regions. Render-only, cached at size changes. */
public final class TextureTiling {
    private record Key(BlockStateModelPart model,Vec3 actual,Vec3 reference) {}
    private static final Map<Key,BlockStateModelPart> MODELS=new HashMap<>();
    public static void clear() { MODELS.clear(); }
    private static double ratio(Vec3 edge,Vec3 actual,Vec3 reference) {
        double baseline=edge.multiply(reference).length();
        return baseline<1e-9?1:edge.multiply(actual).length()/baseline;
    }
    private static int count(double repeats) { return Math.max(1,(int)Math.ceil(repeats-1e-6)); }
    private static double end(int i,double repeats) { return Math.min(1,(i+1)/repeats); }
    private static Vec3 position(GeoVertex v) { return new Vec3(v.posX(),v.posY(),v.posZ()); }
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
                vertices[k]=new GeoVertex((float)p.x,(float)p.y,(float)p.z,
                    (float)(v[0].texU()+(v[1].texU()-v[0].texU())*u+(v[3].texU()-v[0].texU())*t),
                    (float)(v[0].texV()+(v[1].texV()-v[0].texV())*u+(v[3].texV()-v[0].texV())*t));
            }
            result.add(new GeoQuad(vertices,quad.normalX(),quad.normalY(),quad.normalZ(),quad.direction()));
        }
        return result;
    }
    public static List<BakedQuad> nativeQuad(BakedQuad quad,Vec3 actual,Vec3 reference) {
        Vec3 a=new Vec3(quad.position0()),b=new Vec3(quad.position1()),d=new Vec3(quad.position3());
        double rs=ratio(b.subtract(a),actual,reference),rt=ratio(d.subtract(a),actual,reference);
        if(Math.abs(rs-1)<1e-6&&Math.abs(rt-1)<1e-6)return List.of(quad);
        var result=new ArrayList<BakedQuad>(count(rs)*count(rt));
        for(int i=0;i<count(rs);i++)for(int j=0;j<count(rt);j++) {
            double s0=i/rs,s1=end(i,rs),t0=j/rt,t1=end(j,rt);
            double[][] corners={{s0,t0,0,0},{s1,t0,(s1-s0)*rs,0},{s1,t1,(s1-s0)*rs,(t1-t0)*rt},{s0,t1,0,(t1-t0)*rt}};
            var positions=new org.joml.Vector3f[4];var uv=new long[4];
            float u0=u(quad.packedUV0()),v0=v(quad.packedUV0()),us=u(quad.packedUV1())-u0,ut=u(quad.packedUV3())-u0,vs=v(quad.packedUV1())-v0,vt=v(quad.packedUV3())-v0;
            for(int k=0;k<4;k++) {var p=interpolate(a,b,d,corners[k][0],corners[k][1]);positions[k]=p.toVector3f();
                uv[k]=pack((float)(u0+us*corners[k][2]+ut*corners[k][3]),(float)(v0+vs*corners[k][2]+vt*corners[k][3]));}
            result.add(new BakedQuad(positions[0],positions[1],positions[2],positions[3],uv[0],uv[1],uv[2],uv[3],quad.direction(),quad.materialInfo()));
        }return result;
    }
    private static float u(long uv) { return Float.intBitsToFloat((int)uv); }
    private static float v(long uv) { return Float.intBitsToFloat((int)(uv>>>32)); }
    private static long pack(float u,float v) { return Integer.toUnsignedLong(Float.floatToRawIntBits(u))|((long)Float.floatToRawIntBits(v)<<32); }
    public static net.minecraft.client.renderer.block.dispatch.BlockStateModelPart model(net.minecraft.client.renderer.block.dispatch.BlockStateModelPart original,Vec3 actual,Vec3 reference) {
        if(actual.equals(reference))return original;
        return MODELS.computeIfAbsent(new Key(original,actual,reference),key->{
            var sides=new EnumMap<Direction,List<BakedQuad>>(Direction.class);
            for(var side:Direction.values())sides.put(side,tile(original.getQuads(side),actual,reference));
            var general=tile(original.getQuads(null),actual,reference);
            return new net.minecraft.client.renderer.block.dispatch.BlockStateModelPart() {
                public List<BakedQuad> getQuads(Direction side) { return side==null?general:sides.get(side); }
                public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }
                public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial() { return original.particleMaterial(); }
                public int materialFlags() { return original.materialFlags(); }
            };
        });
    }
    private static List<BakedQuad> tile(List<BakedQuad> quads,Vec3 actual,Vec3 reference) {
        var output=new ArrayList<BakedQuad>();for(var q:quads)output.addAll(nativeQuad(q,actual,reference));return List.copyOf(output);
    }
    private TextureTiling() {}
}
