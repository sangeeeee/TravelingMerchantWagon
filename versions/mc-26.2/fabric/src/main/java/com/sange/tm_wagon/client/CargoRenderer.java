package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sange.tm_wagon.cargo.*;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.*;

/** Drawn in cart-local coordinates by both host renderers; no cargo entities or world blocks. */
public final class CargoRenderer {
    private static net.minecraft.client.renderer.block.BlockModelResolver resolver;
    private static net.minecraft.client.renderer.block.BlockModelResolver resolver(){
        if(resolver==null)resolver=new net.minecraft.client.renderer.block.BlockModelResolver(net.minecraft.client.Minecraft.getInstance().getModelManager());
        return resolver;
    }
    private static final Map<CargoEntry,BlockEntity> CACHE=new WeakHashMap<>();
    private static final java.util.Set<net.minecraft.world.level.block.Block> FALLBACKS=new java.util.HashSet<>();
    public static void render(CargoHold hold,float tick,PoseStack poses,SubmitNodeCollector buffers,int light,int overlay,CameraRenderState camera) {
        var mc=Minecraft.getInstance();
        CabinetRenderer.render(hold.cabinet(),tick,poses,buffers,light,overlay);
        CargoCoverRenderer.render(hold.cover(),hold.owner().cargoBody(),poses,buffers,light,overlay);
        CanopyRenderer.render(hold.canopy(),hold.owner().cargoBody(),poses,buffers,light,overlay);
        for(int slot=0;slot<hold.capacity();slot++) {
            var entry=hold.entry(slot);if(entry==null||hold.anchorSlot(slot)!=slot)continue;var p=hold.centreAt(slot);
            if(entry.kind==CargoEntry.Kind.STRAW_MAT) {
                poses.pushPose();poses.translate(p.x,CargoHold.FLOOR+1,p.z-.70);
                if(entry.reversed)poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
                poses.scale(2,2,2);
                poses.translate(-.5,-.5,-.5);
                CargoStrawMatRenderer.render(poses,buffers,light,overlay);
                poses.popPose();continue;
            }
            if(entry.kind==CargoEntry.Kind.SLEEPING_BAG) {
                // Two native block models, one uniform transform around their shared centre.
                poses.pushPose();poses.translate(p.x,CargoHold.FLOOR+.002,p.z-.35);
                if(entry.reversed)poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
                poses.scale((float)CargoHold.SCALE,(float)CargoHold.SCALE,(float)CargoHold.SCALE);
                poses.translate(-.5,0,0);
                var foot=entry.state.setValue(net.minecraft.world.level.block.BedBlock.FACING,net.minecraft.core.Direction.NORTH)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.FOOT);
                drawBlock(mc,foot,poses,buffers,light,overlay);
                poses.translate(0,0,-1);
                drawBlock(mc,foot.setValue(net.minecraft.world.level.block.BedBlock.PART,
                    net.minecraft.world.level.block.state.properties.BedPart.HEAD),poses,buffers,light,overlay);
                poses.popPose();continue;
            }
            if(entry.kind==CargoEntry.Kind.STOOL) {
                poses.pushPose();poses.translate(p.x,CargoHold.FLOOR+.5,p.z);
                drawItem(mc,entry.item,entry.holdOwnerLevel(),poses,buffers,light,overlay);
                poses.popPose();continue;
            }
            poses.pushPose();poses.translate(p.x-CargoHold.SCALE/2,p.y,p.z-CargoHold.SCALE/2);
            poses.scale((float)CargoHold.SCALE,(float)CargoHold.SCALE,(float)CargoHold.SCALE);
            try {
                if(com.sange.tm_wagon.compat.BackpackCompat.matches(entry.item)) {
                    // Native item renderers retain dyes and upgrade visuals without a staged world BE.
                    item(mc,entry,poses,buffers,light,overlay);
                } else if(entry.kind==CargoEntry.Kind.CHEST||entry.kind==CargoEntry.Kind.SHULKER||entry.kind==CargoEntry.Kind.ENDER_CHEST
                    ||entry.kind==CargoEntry.Kind.ENCHANTING||entry.kind==CargoEntry.Kind.LECTERN||entry.kind==CargoEntry.Kind.POT||entry.kind==CargoEntry.Kind.SHELF) {
                    if(entry.kind==CargoEntry.Kind.ENCHANTING||entry.kind==CargoEntry.Kind.LECTERN||entry.kind==CargoEntry.Kind.SHELF)
                        drawBlock(mc,entry.state,poses,buffers,light,overlay);
                    BlockEntity be=CACHE.computeIfAbsent(entry,e->{
                        var reference=new java.lang.ref.WeakReference<>(e);
                        return switch(e.kind) {
                            case CHEST->CargoChestVisuals.create(e);
                            case ENDER_CHEST->new EnderChestBlockEntity(BlockPos.ZERO,e.state) { @Override public float getOpenNess(float partial) { var cargo=reference.get();return cargo==null?0:cargo.lid(partial); } };
                            case SHULKER->new ShulkerBoxBlockEntity(BlockPos.ZERO,e.state) { @Override public float getProgress(float partial) { var cargo=reference.get();return cargo==null?0:cargo.lid(partial); } };
                            case ENCHANTING->new EnchantingTableBlockEntity(BlockPos.ZERO,e.state);
                            case LECTERN->new LecternBlockEntity(BlockPos.ZERO,e.state);
                            case POT->new DecoratedPotBlockEntity(BlockPos.ZERO,e.state);
                            case SHELF->new ShelfBlockEntity(BlockPos.ZERO,e.state);
                            default->throw new IllegalStateException("Not a cargo block renderer");
                        };
                    });
                    be.setBlockState(entry.state);be.setLevel(hold.owner().cargoLevel());
                    if(be instanceof DecoratedPotBlockEntity)be.applyComponentsFromItemStack(entry.item);
                    if(be instanceof ShelfBlockEntity shelf)for(int i=0;i<3;i++)shelf.getItems().set(i,entry.inventory.getItem(i));
                    if(be instanceof EnchantingTableBlockEntity table) {
                        table.time=hold.owner().cargoLevel()==null?0:(int)hold.owner().cargoLevel().getGameTime();table.open=table.oOpen=entry.opened?1:.2F;
                    }
                    BlockEntityRenderer<BlockEntity,net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState> renderer=mc.getBlockEntityRenderDispatcher().getRenderer(be);
                    if(renderer!=null) {
                        var state=renderer.createRenderState();renderer.extractRenderState(be,state,tick,camera.pos,null);
                        state.lightCoords=light;renderer.submit(state,poses,buffers,camera);
                    }
                    else item(mc,entry,poses,buffers,light,overlay);
                } else if(entry.state.getRenderShape()==RenderShape.MODEL&&!FALLBACKS.contains(entry.state.getBlock())) {
                    drawBlock(mc,entry.state,poses,buffers,light,overlay);
                } else item(mc,entry,poses,buffers,light,overlay);
            } catch(RuntimeException failure) {
                if(FALLBACKS.add(entry.state.getBlock()))com.mojang.logging.LogUtils.getLogger().warn("Using cargo item model for {}",entry.item.getItem(),failure);
                item(mc,entry,poses,buffers,light,overlay);
            }
            poses.popPose();
        }
    }
    private static void item(Minecraft mc,CargoEntry entry,PoseStack poses,SubmitNodeCollector buffers,int light,int overlay) {
        poses.pushPose();poses.translate(.5,.5,.5);
        try { drawItem(mc,entry.item,entry.holdOwnerLevel(),poses,buffers,light,overlay); }
        catch(RuntimeException unsupported) { drawItem(mc,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BARRIER),null,poses,buffers,light,overlay); }
        poses.popPose();
    }
    private static final net.minecraft.client.renderer.block.model.BlockDisplayContext CONTEXT=net.minecraft.client.renderer.block.model.BlockDisplayContext.create();
    private static void drawBlock(Minecraft mc,net.minecraft.world.level.block.state.BlockState block,PoseStack poses,SubmitNodeCollector tasks,int light,int overlay) {
        var state=new net.minecraft.client.renderer.block.BlockModelRenderState();
        resolver().update(state,block,CONTEXT);state.submit(poses,tasks,light,overlay,0);
    }
    private static void drawItem(Minecraft mc,net.minecraft.world.item.ItemStack stack,net.minecraft.world.level.Level level,PoseStack poses,SubmitNodeCollector tasks,int light,int overlay) {
        var state=new net.minecraft.client.renderer.item.ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state,stack,ItemDisplayContext.NONE,level,null,0);state.submit(poses,tasks,light,overlay,0);
    }
    private CargoRenderer() {}
}
