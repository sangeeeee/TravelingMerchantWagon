package com.sange.tm_wagon.client;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.material.WagonMaterial;
import com.sange.tm_wagon.cargo.CargoHold;
import java.util.Map;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.*;
import com.geckolib.cache.model.*;
import com.geckolib.util.RenderUtil;
import net.minecraft.client.renderer.*;
import com.mojang.blaze3d.vertex.*;

/** Values are captured once per render extraction; geometry stays shared across instances. */
record WagonVisual(Map<WagonSlot,WagonPart> parts,Map<WagonSlot,WagonMaterial> materials,CargoHold cargo,
    float yaw,float pitch,float roll,float steering,float shafts,float[] wheels,float gate,double frame,boolean assembly) {
    static final DataTicket<WagonVisual> DATA=DataTicket.create("tm_wagon_visual",WagonVisual.class);
    WagonPart body() { return parts.getOrDefault(WagonSlot.BODY,WagonPart.CARGO_BODY); }
    static net.minecraft.resources.Identifier model(GeoRenderState state) {
        var v=state.getGeckolibData(DATA);
        if(v.assembly&&!v.parts.containsKey(WagonSlot.BODY))return net.minecraft.resources.Identifier.fromNamespaceAndPath("tm_wagon","wagon_assembly_frame");
        String seat=v.parts.getOrDefault(WagonSlot.SEAT,WagonPart.SINGLE_SEAT).id;
        String shafts=v.parts.get(WagonSlot.SHAFTS)==WagonPart.DOUBLE_HORSE_SHAFTS?"double_horse":"single_horse";
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("tm_wagon","assembly/"+v.body().modelPrefix()+seat+"_"+shafts+"");
    }
    void bones(BoneSnapshots snapshots) {
        snapshots.ifPresent("frame_root",b->{b.skipRender(!assembly);b.skipChildrenRender(!assembly);});
        for(String name:new String[]{"seat","shafts","front_left_wheel","front_right_wheel","rear_left_wheel","rear_right_wheel"})
            snapshots.ifPresent(name,b->{boolean hidden=!parts.containsKey(MaterialRenderer.slot(name));b.skipRender(hidden);b.skipChildrenRender(hidden);});
        snapshots.ifPresent("front_axle",b->b.setRotY(-steering));snapshots.ifPresent("shafts",b->b.setRotX(shafts));
        snapshots.ifPresent("tailgate",b->b.setRotX((float)Math.PI*gate));
        String[] names={"front_left_wheel","front_right_wheel","rear_left_wheel","rear_right_wheel"};
        for(int i=0;i<4;i++){final int n=i;snapshots.ifPresent(names[i],b->b.setRotX(wheels[n]));}
        if(assembly) {
            double delta=Math.toRadians((FrameMotion.EXTENDED_ANGLE-FrameMotion.FOLDED_ANGLE)*frame);
            snapshots.ifPresent("frame_grid_platform",b->b.setTranslateY((float)FrameMotion.platformOffset(frame)));
            for(String end:new String[]{"front","rear"})for(String side:new String[]{"left","right"}) {
                float sign=side.equals("left")?-1:1;
                snapshots.ifPresent("frame_"+end+"_"+side+"_lower",b->b.setRotZ((float)(sign*delta)));
                snapshots.ifPresent("frame_"+end+"_"+side+"_upper",b->b.setRotZ((float)(-sign*2*delta)));
            }
        }
    }
    static <R extends GeoRenderState> void submit(RenderPassInfo<R> info,OrderedSubmitNodeCollector tasks,net.minecraft.client.renderer.rendertype.RenderType type,WagonVisual visual) {
        if(type==null)return;
        tasks.submitCustomGeometry(info.poseStack(),type,(pose,buffer)->{
            var stack=info.poseStack();stack.pushPose();stack.last().set(pose);
            info.renderPosed(()->{for(var bone:info.model().topLevelBones())renderBone(info,stack,buffer,bone,visual);});stack.popPose();
        });
    }
    static void submitItem(RenderPassInfo<GeoRenderState> info,OrderedSubmitNodeCollector tasks,net.minecraft.client.renderer.rendertype.RenderType type,WagonVisual visual) {
        if(type==null)return;
        tasks.submitCustomGeometry(info.poseStack(),type,(pose,buffer)->{
            var stack=new PoseStack();stack.last().set(pose);
            info.renderPosed(()->{for(var bone:info.model().topLevelBones())renderItemBone(info,stack,buffer,bone,visual);});
        });
    }
    private static void renderItemBone(RenderPassInfo<?> info,PoseStack poses,VertexConsumer buffer,GeoBone bone,WagonVisual visual) {
        poses.pushPose();RenderUtil.prepMatrixForBoneAndUpdateListeners(poses,bone,info);
        MaterialRenderer.cubes(poses,bone,buffer,info.packedLight(),info.packedOverlay(),info.renderColor(),visual.body(),visual.materials.get(WagonSlot.BODY));
        for(var child:bone.children())renderItemBone(info,poses,buffer,child,visual);
        poses.popPose();
    }
    private static void renderBone(RenderPassInfo<?> info,PoseStack poses,VertexConsumer buffer,GeoBone bone,WagonVisual visual) {
        if(bone.frameSnapshot!=null&&bone.frameSnapshot.areChildrenHidden()&&bone.frameSnapshot.isHidden())return;
        poses.pushPose();RenderUtil.prepMatrixForBoneAndUpdateListeners(poses,bone,info);
        var slot=MaterialRenderer.slot(bone.name());
        MaterialRenderer.cubes(poses,bone,buffer,info.packedLight(),info.packedOverlay(),info.renderColor(),
            slot==null?null:visual.parts.get(slot),slot==null?WagonMaterial.DEFAULT:visual.materials.getOrDefault(slot,WagonMaterial.DEFAULT));
        if(bone.frameSnapshot==null||!bone.frameSnapshot.areChildrenHidden())for(var child:bone.children())renderBone(info,poses,buffer,child,visual);
        poses.popPose();
    }
}
