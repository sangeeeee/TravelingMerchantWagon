package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.cargo.*;
import com.sange.tm_wagon.material.*;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;

/** Compares submitted vertices with the previous renderers before measuring native frames. */
final class RenderCacheChecks {
    private static boolean ready;
    private static final java.util.Set<GeoBone> BONES=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    static void bone(GeoRenderer<?> renderer,PoseStack poses,GeoBone bone,WagonPart part) {
        if(!ready||bone.isHidden()||bone.getCubes().isEmpty()||!BONES.add(bone))return;
        for(var wood:WoodMaterial.values()) {
            var material=new WagonMaterial(wood,DyeColor.BLUE);var old=new Capture();var actual=new Capture();
            ReferenceMaterialRenderer.cubes(renderer,poses,bone,old,0x00b00070,0x00020003,0xffd0b090,part,material);
            MaterialRenderer.cubes(renderer,poses,bone,actual,0x00b00070,0x00020003,0xffd0b090,part,material);
            same(old,actual,"bone "+bone.getName()+" / "+wood);
        }
    }
    static void accessories(Minecraft mc) {
        ReferenceMaterialRenderer.reload(mc.getResourceManager());
        int checks=0;
        for(var body:new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY}) {
            var w=WagonContent.WAGON.get().create(mc.level);var parts=com.sange.tm_wagon.entity.WagonEntity.defaultParts();
            parts.put(WagonSlot.BODY,body);w.configure(parts,Direction.NORTH);
            var canopy=new CargoCanopy(w.cargo());var cover=new CargoCover(w.cargo());
            for(var colour:DyeColor.values()) {
                var tag=new CompoundTag();tag.putBoolean("Installed",true);tag.put("Material",new WagonMaterial(WoodMaterial.OAK,colour).save());
                for(int state=0;state<4;state++) {
                    tag.putBoolean("FrontClosed",(state&1)!=0);tag.putBoolean("RearClosed",(state&2)!=0);canopy.load(tag);
                    var old=new Capture();var actual=new Capture();var poses=pose();
                    ReferenceCanopyRenderer.render(canopy,body,poses,type->old,0x00b00070,0x00020003);
                    CanopyRenderer.render(canopy,body,poses,type->actual,0x00b00070,0x00020003);
                    same(old,actual,"canopy "+body+" / "+state+" / "+colour);checks++;
                }
                for(int state=0;state<=body.rows();state++) {
                    tag.putInt("OpenRows",state);cover.load(tag);var old=new Capture();var actual=new Capture();var poses=pose();
                    ReferenceCargoCoverRenderer.render(cover,body,poses,type->old,0x00b00070,0x00020003);
                    CargoCoverRenderer.render(cover,body,poses,type->actual,0x00b00070,0x00020003);
                    same(old,actual,"cover "+body+" / "+state+" / "+colour);checks++;
                }
            }
            w.discard();
        }
        // Rebuild once after clearing the same caches used by resource reloads.
        CanopyRenderer.clear();CargoCoverRenderer.clear();TextureTiling.clear();MaterialRenderer.reload(mc.getResourceManager());
        ready=true;
        com.mojang.logging.LogUtils.getLogger().info("RENDER_CACHE_CHECKS_PASSED {} accessory variants",checks);
    }
    static void summary() { com.mojang.logging.LogUtils.getLogger().info("RENDER_CACHE_BONES_CHECKED {} bones x 10 woods",BONES.size()); }
    private static PoseStack pose() {
        var p=new PoseStack();p.translate(-4.25,2.5,-8.25);
        p.mulPose(com.mojang.math.Axis.YP.rotation(.73F));p.mulPose(com.mojang.math.Axis.XP.rotation(.19F));return p;
    }
    private static void same(Capture a,Capture b,String label) {
        if(a.data.size()!=b.data.size()||a.meta.size()!=b.meta.size())throw new IllegalStateException(label+" changed vertex count");
        for(int i=0;i<a.data.size();i++)if(Math.abs(a.data.getFloat(i)-b.data.getFloat(i))>0.00005F)
            throw new IllegalStateException(label+" vertex component "+i+": "+a.data.getFloat(i)+" / "+b.data.getFloat(i));
        for(int i=0;i<a.meta.size();i++)if(a.meta.getInt(i)!=b.meta.getInt(i))throw new IllegalStateException(label+" light/colour/overlay "+i+": "+a.meta.getInt(i)+" / "+b.meta.getInt(i));
    }
    private static final class Capture implements VertexConsumer {
        final FloatArrayList data=new FloatArrayList();final IntArrayList meta=new IntArrayList();
        @Override public void addVertex(float x,float y,float z,int colour,float u,float v,int overlay,int light,float nx,float ny,float nz) {
            data.add(x);data.add(y);data.add(z);data.add(u);data.add(v);data.add(nx);data.add(ny);data.add(nz);
            meta.add(colour);meta.add(overlay);meta.add(light);
        }
        @Override public VertexConsumer addVertex(float x,float y,float z) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv(float u,float v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv1(int u,int v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv2(int u,int v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setNormal(float x,float y,float z) { throw new UnsupportedOperationException(); }
    }
}
