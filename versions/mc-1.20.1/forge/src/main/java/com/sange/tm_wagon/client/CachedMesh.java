package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.util.RenderUtils;

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
        int dyed=ColourMath.multiply(colour,tint);
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
                buffer.vertex(position.x,position.y,position.z,((rgba>>>16)&255)/255F,((rgba>>>8)&255)/255F,(rgba&255)/255F,((rgba>>>24)&255)/255F,vertices[i+3],vertices[i+4],overlay,packed,normal.x,normal.y,normal.z);
            }
        }
    }
    static final class Builder implements VertexConsumer {
        private final FloatArrayList vertices=new FloatArrayList();
        private final IntArrayList flags=new IntArrayList(),lights=new IntArrayList();
        private int bits;
        void model(BakedModel model,PoseStack.Pose pose) {
            var random=RandomSource.create(42);
            for(int side=0;side<=6;side++) {
                random.setSeed(42);
                var direction=side==6?null:Direction.from3DDataValue(side);
                for(var quad:model.getQuads(null,direction,random,ModelData.EMPTY,null)) {
                    bits=quad.isTinted()?TINT:0;
                    putBulkData(pose,quad,1,1,1,0,0);
                }
            }
        }
        void cube(GeoCube cube,boolean tinted) {
            var local=new PoseStack();
            RenderUtils.translateToPivotPoint(local,cube);RenderUtils.rotateMatrixAroundCube(local,cube);RenderUtils.translateAwayFromPivotPoint(local,cube);
            var position=new Vector3f();var normal=new Vector3f();var size=cube.size();
            bits=(tinted?TINT:0)|((size.y==0||size.z==0)?FLAT_X:0)|((size.x==0||size.z==0)?FLAT_Y:0)|((size.x==0||size.y==0)?FLAT_Z:0);
            for(var quad:cube.quads()) {
                if(quad==null)continue;
                local.last().normal().transform(quad.normal(),normal);
                for(var vertex:quad.vertices()) {
                    local.last().pose().transformPosition(vertex.position(),position);
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
        public void addVertex(float x,float y,float z,int colour,float u,float v,int overlay,int light,float nx,float ny,float nz) {
            if(lights.size()%4==0)flags.add(bits);
            vertices.add(x);vertices.add(y);vertices.add(z);vertices.add(u);vertices.add(v);
            vertices.add(nx);vertices.add(ny);vertices.add(nz);lights.add(light);
        }
        private float x,y,z,u,v,nx,ny,nz;
        private int light,overlay;
        @Override public VertexConsumer vertex(double x,double y,double z) {this.x=(float)x;this.y=(float)y;this.z=(float)z;return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a) {return this;}
        @Override public VertexConsumer uv(float u,float v) {this.u=u;this.v=v;return this;}
        @Override public VertexConsumer overlayCoords(int u,int v) {overlay=u|(v<<16);return this;}
        @Override public VertexConsumer uv2(int u,int v) {light=u|(v<<16);return this;}
        @Override public VertexConsumer normal(float x,float y,float z) {nx=x;ny=y;nz=z;return this;}
        @Override public void endVertex() {addVertex(x,y,z,-1,u,v,overlay,light,nx,ny,nz);}
        @Override public void defaultColor(int r,int g,int b,int a) {}
        @Override public void unsetDefaultColor() {}

    }
}
