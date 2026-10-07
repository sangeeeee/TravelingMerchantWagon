package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;

import org.joml.Vector3f;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.util.RenderUtil;

/** Immutable local vertices. Uses the active consumer, including Iris shadow/extended formats. */
final class CachedMesh {
    private static final int TINT=1,FLAT_X=2,FLAT_Y=4,FLAT_Z=8,SHARED_NORMAL=16;
    private final float[] vertices;
    private final int[] flags,lights;
    private CachedMesh(float[] vertices,int[] flags,int[] lights) {
        this.vertices=vertices;this.flags=flags;this.lights=lights;
    }
    void render(PoseStack.Pose pose,VertexConsumer buffer,int light,int overlay,int colour,int tint) {
        var matrix=pose.pose();var normals=pose.normal();
        var position=new Vector3f();var normal=new Vector3f();
        int dyed=ARGB.multiply(colour,tint);
        for(int quad=0;quad<flags.length;quad++) {
            int bits=flags[quad],rgba=(bits&TINT)!=0?dyed:colour;
            for(int v=0;v<4;v++) {
                int vertex=quad*4+v,i=vertex*8;
                matrix.transformPosition(vertices[i],vertices[i+1],vertices[i+2],position);
                if(v==0||(bits&SHARED_NORMAL)==0) {
                    normals.transform(vertices[i+5],vertices[i+6],vertices[i+7],normal);
                    // GeckoLib applies these corrections in the final coordinate space.
                    if((bits&FLAT_X)!=0&&normal.x<0)normal.x=-normal.x;
                    if((bits&FLAT_Y)!=0&&normal.y<0)normal.y=-normal.y;
                    if((bits&FLAT_Z)!=0&&normal.z<0)normal.z=-normal.z;
                }
                int baked=lights[vertex];
                int packed=baked==0?light:Math.max(light&65535,baked&65535)|(Math.max(light>>>16,baked>>>16)<<16);
                buffer.addVertex(position.x,position.y,position.z,rgba,vertices[i+3],vertices[i+4],overlay,packed,normal.x,normal.y,normal.z);
            }
        }
    }
    static final class Builder implements VertexConsumer {
        private final FloatArrayList vertices=new FloatArrayList();
        private final IntArrayList flags=new IntArrayList(),lights=new IntArrayList();
        private int bits;
        void model(BlockStateModelPart model,PoseStack.Pose pose) {
            var random=RandomSource.create(42);
            for(int side=0;side<=6;side++) {
                random.setSeed(42);
                var direction=side==6?null:Direction.from3DDataValue(side);
                for(var quad:model.getQuads(direction)) {
                    bits=quad.materialInfo().isTinted()?TINT:0;
                    putBakedQuad(pose,quad,new com.mojang.blaze3d.vertex.QuadInstance() {{ setLightCoords(0);setOverlayCoords(0); }});
                }
            }
        }
        void cube(GeoCube cube,boolean tinted) {
            var local=new PoseStack();
            cube.translateToPivotPoint(local);
            cube.rotate(local);
            cube.translateAwayFromPivotPoint(local);
            var position=new Vector3f();var normal=new Vector3f();var size=cube.size();
            bits=(tinted?TINT:0)|((size.y==0||size.z==0)?FLAT_X:0)|((size.x==0||size.z==0)?FLAT_Y:0)|((size.x==0||size.y==0)?FLAT_Z:0);
            for(var quad:cube.quads()) {
                if(quad==null)continue;
                local.last().normal().transform(quad.normalVec(),normal);
                for(var vertex:quad.vertices()) {
                    local.last().pose().transformPosition(vertex.posX(),vertex.posY(),vertex.posZ(),position);
                    addVertex(position.x,position.y,position.z,-1,vertex.texU(),vertex.texV(),0,0,normal.x,normal.y,normal.z);
                }
            }
        }
        CachedMesh build() {
            float[] data=vertices.toFloatArray();int[] metadata=flags.toIntArray();
            if(lights.size()%4!=0)throw new IllegalStateException("Incomplete wagon mesh quad");
            for(int q=0;q<metadata.length;q++) {
                int start=q*32;boolean same=true;
                for(int v=1;v<4;v++)for(int n=5;n<8;n++)same&=data[start+n]==data[start+v*8+n];
                if(same)metadata[q]|=SHARED_NORMAL;
            }
            return new CachedMesh(data,metadata,lights.toIntArray());
        }
        @Override public void addVertex(float x,float y,float z,int colour,float u,float v,int overlay,int light,float nx,float ny,float nz) {
            if(lights.size()%4==0)flags.add(bits);
            vertices.add(x);vertices.add(y);vertices.add(z);vertices.add(u);vertices.add(v);
            vertices.add(nx);vertices.add(ny);vertices.add(nz);lights.add(light);
        }
        @Override public VertexConsumer setColor(int c) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv3(float u,float v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setLineWidth(float width) { throw new UnsupportedOperationException(); }
        // The native bulk quad writer emits complete vertices through the overload above.
        @Override public VertexConsumer addVertex(float x,float y,float z) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv(float u,float v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv1(int u,int v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv2(int u,int v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setNormal(float x,float y,float z) { throw new UnsupportedOperationException(); }
    }
}
