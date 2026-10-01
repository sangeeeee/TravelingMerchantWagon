package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sange.tm_wagon.cargo.*;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/** Drawn in cart-local coordinates by both host renderers; no cargo entities or world blocks. */
public final class CargoRenderer {
    private static final Map<CargoEntry,BlockEntity> CACHE=new WeakHashMap<>();
    private static final java.util.Set<net.minecraft.world.level.block.Block> FALLBACKS=new java.util.HashSet<>();
    public static void render(CargoHold hold,float tick,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        var mc=Minecraft.getInstance();
        for(int slot=0;slot<CargoHold.CAPACITY;slot++) {
            var entry=hold.entry(slot);if(entry==null)continue;var p=CargoHold.centre(slot);
            poses.pushPose();poses.translate(p.x-CargoHold.SCALE/2,p.y,p.z-CargoHold.SCALE/2);
            poses.scale((float)CargoHold.SCALE,(float)CargoHold.SCALE,(float)CargoHold.SCALE);
            try {
                if(entry.kind==CargoEntry.Kind.CHEST||entry.kind==CargoEntry.Kind.SHULKER) {
                    BlockEntity be=CACHE.computeIfAbsent(entry,e->{
                        var reference=new java.lang.ref.WeakReference<>(e);
                        return e.kind==CargoEntry.Kind.CHEST
                            ?new ChestBlockEntity(BlockPos.ZERO,e.state) { @Override public float getOpenNess(float partial) { var cargo=reference.get();return cargo==null?0:cargo.lid(partial); } }
                            :new ShulkerBoxBlockEntity(BlockPos.ZERO,e.state) { @Override public float getProgress(float partial) { var cargo=reference.get();return cargo==null?0:cargo.lid(partial); } };
                    });
                    be.setBlockState(entry.state);be.setLevel(hold.owner().cargoLevel());
                    @SuppressWarnings("unchecked") var renderer=(BlockEntityRenderer<BlockEntity>)mc.getBlockEntityRenderDispatcher().getRenderer(be);
                    if(renderer!=null)renderer.render(be,tick,poses,buffers,light,overlay);
                    else item(mc,entry,poses,buffers,light,overlay);
                } else if(entry.state.getRenderShape()==RenderShape.MODEL&&!FALLBACKS.contains(entry.state.getBlock())) {
                    mc.getBlockRenderer().renderSingleBlock(entry.state,poses,buffers,light,overlay);
                } else item(mc,entry,poses,buffers,light,overlay);
            } catch(RuntimeException failure) {
                if(FALLBACKS.add(entry.state.getBlock()))com.mojang.logging.LogUtils.getLogger().warn("Using cargo item model for {}",entry.item.getItem(),failure);
                item(mc,entry,poses,buffers,light,overlay);
            }
            poses.popPose();
        }
    }
    private static void item(Minecraft mc,CargoEntry entry,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        poses.pushPose();poses.translate(.5,.5,.5);
        try { mc.getItemRenderer().renderStatic(entry.item,ItemDisplayContext.NONE,light,overlay,poses,buffers,entry.holdOwnerLevel(),0); }
        catch(RuntimeException unsupported) { mc.getItemRenderer().renderStatic(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BARRIER),ItemDisplayContext.NONE,light,overlay,poses,buffers,null,0); }
        poses.popPose();
    }
    private CargoRenderer() {}
}
