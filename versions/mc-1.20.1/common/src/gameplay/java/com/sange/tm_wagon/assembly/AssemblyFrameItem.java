package com.sange.tm_wagon.assembly;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Reserve the complete platform before the normal block placement consumes an item. */
public class AssemblyFrameItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    public AssemblyFrameItem(Block block, Properties properties) { super(block, properties); }

    @Override public void appendHoverText(ItemStack stack,net.minecraft.world.level.Level context,List<Component> tooltip,TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tm_wagon.wagon_assembly_frame").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    @Override public InteractionResult place(BlockPlaceContext context) {
        var state = getPlacementState(context);
        if (state == null) return InteractionResult.FAIL;
        var level = context.getLevel();
        var origin = context.getClickedPos();
        String error = null;
        for (var entry : WagonGeometry.frameCells(state.getValue(AssemblyFrameBlock.FACING),state.getValue(AssemblyFrameBlock.EXTENDED)?0:1).entrySet()) {
            var pos = origin.offset(entry.getKey());
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)) {
                error = "message.tm_wagon.out_of_bounds"; break;
            }
            var existing = level.getBlockState(pos);
            if (!existing.canBeReplaced() || !existing.getFluidState().isEmpty() || existing.hasBlockEntity()) {
                error = "message.tm_wagon.blocked"; break;
            }
            var player = context.getPlayer();
            if (player != null && (!level.mayInteract(player,pos) || !player.mayUseItemAt(pos,Direction.UP,context.getItemInHand()))) {
                error = "message.tm_wagon.protected"; break;
            }
            if (!level.isUnobstructed(null,WagonGeometry.shape(entry.getValue()).move(pos.getX(),pos.getY(),pos.getZ()))) {
                error = "message.tm_wagon.entity_blocked"; break;
            }
        }
        if (error != null) {
            if (!level.isClientSide && context.getPlayer() != null) context.getPlayer().displayClientMessage(Component.translatable(error),true);
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }

    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var previous = level.getBlockState(pos);
        if (!super.placeBlock(context,state)) return false;
        if (level.isClientSide) return true;
        if (level.getBlockEntity(pos) instanceof AssemblyFrameBlockEntity frame) {
            if (frame.initializeFrame() == null) return true;
            frame.dismantle(false,true);
        }
        level.setBlock(pos,previous,3);
        return false;
    }
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            private final net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer = new com.sange.tm_wagon.client.FrameItemRenderer();
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {return renderer;}
        });
    }
}
