package com.sange.tm_wagon.assembly;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;

public class WagonPartItem extends BlockItem implements GeoItem {
    private final WagonPart part;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public WagonPartItem(Block block, WagonPart part, Properties properties) { super(block,properties); this.part = part; }
    public WagonPart part() { return part; }

    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var frame = AssemblyFrameBlockEntity.find(level,context.getClickedPos());
        if (frame == null) return fail(context,"message.tm_wagon.frame_required");
        if (!frame.acceptsParts()) return fail(context,"message.tm_wagon.frame_extend_first");
        WagonSlot target = null;
        if (part.isCargoBody()) {
            if (!frame.extended() || frame.frameMoving()) return fail(context,"message.tm_wagon.frame_extend_first");
            var hit = new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(),context.getClickedFace(),context.getClickedPos(),false);
            if (frame.platformTop(hit)) target = WagonSlot.BODY;
        }
        if (WagonSlot.SHAFTS.accepts(part)&&frame.has(WagonSlot.BODY)) {
            var cell=frame.layout().get(context.getClickedPos());
            var local=frame.cargoPose().local(net.minecraft.world.phys.Vec3.atCenterOf(context.getClickedPos()));
            double front=WagonGeometry.partBoxes(frame.cargoBody()).getFirst().minZ;
            if(cell!=null&&cell.slots().contains(WagonSlot.BODY)&&Math.abs(local.x)<.001&&local.z<=front+.5)
                target=WagonSlot.SHAFTS;
        }
        for (WagonSlot slot : WagonSlot.values()) {
            if (target!=null || part.isCargoBody()) break;
            if (!slot.accepts(part)) continue;
            var pos = slot.position(frame.getBlockPos(),frame.facing(),frame.cargoBody());
            if (pos.equals(context.getClickedPos()) || pos.equals(context.getClickedPos().relative(context.getClickedFace()))) { target = slot; break; }
        }
        if (target == null) return fail(context,"message.tm_wagon.wrong_slot");
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        String error = frame.install(target,part,context.getPlayer(),context.getItemInHand());
        if (error != null) return fail(context,error);
        level.playSound(null,frame.getBlockPos(),net.minecraft.sounds.SoundEvents.WOOD_PLACE,net.minecraft.sounds.SoundSource.BLOCKS,1,.9F);
        return InteractionResult.CONSUME;
    }
    private InteractionResult fail(UseOnContext context, String key) {
        if (!context.getLevel().isClientSide() && context.getPlayer() != null) context.getPlayer().sendOverlayMessage(Component.translatable(key));
        return InteractionResult.FAIL;
    }
    @Override public Component getName(ItemStack stack) { return com.sange.tm_wagon.material.WagonMaterial.name(stack,super.getName(stack)); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.tm_wagon."+part.id).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void createGeoRenderer(java.util.function.Consumer<com.geckolib.animatable.client.GeoRenderProvider> consumer) {
        consumer.accept(new com.geckolib.animatable.client.GeoRenderProvider() {
            private com.geckolib.renderer.GeoItemRenderer<?> renderer;
            @Override public com.geckolib.renderer.GeoItemRenderer<?> getGeoItemRenderer() { if(renderer==null)renderer=new com.sange.tm_wagon.client.PartItemRenderer(part);return renderer; }
        });
    }
}
