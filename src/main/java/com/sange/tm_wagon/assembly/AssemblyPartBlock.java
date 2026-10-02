package com.sange.tm_wagon.assembly;

import com.mojang.serialization.MapCodec;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AssemblyPartBlock extends BaseEntityBlock {
    public static final MapCodec<AssemblyPartBlock> CODEC = simpleCodec(AssemblyPartBlock::new);
    public AssemblyPartBlock(Properties properties) { super(properties.dynamicShape()); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AssemblyCellBlockEntity(pos, state); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if(!(world.getBlockEntity(pos) instanceof AssemblyCellBlockEntity cell))return Shapes.empty();
        var frame=AssemblyFrameBlockEntity.find(world,pos);
        if(frame==null)return cell.shape();
        var extra=frame.cargo().cover().selectionCells(frame.cargoBody(),frame.facing()).get(pos.subtract(frame.getBlockPos()));
        var shape=extra==null?cell.shape():Shapes.or(cell.shape(),WagonGeometry.shape(extra));
        var rim=frame.cargo().canopy().selectionCells(frame.cargoBody(),frame.facing()).get(pos.subtract(frame.getBlockPos()));
        return rim==null?shape:Shapes.or(shape,WagonGeometry.shape(rim));
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return world.getBlockEntity(pos) instanceof AssemblyCellBlockEntity cell?cell.shape():Shapes.empty();
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type,WagonContent.CELL_ENTITY.get(),(world,pos,s,cell) -> {
            if (world.getGameTime()%20 != 0 || !world.hasChunkAt(cell.owner())) return;
            AssemblyFrameBlockEntity frame = AssemblyFrameBlockEntity.find(world,cell.owner());
            if (frame == null || !frame.layout().containsKey(pos)) world.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        });
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        return stack.getItem() instanceof WagonPartItem || stack.getItem() instanceof AssemblyFrameItem
            ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        var frame=AssemblyFrameBlockEntity.find(level,pos);
        if(frame==null || !frame.layout().containsKey(pos) || !frame.layout().get(pos).framePart()) return InteractionResult.PASS;
        var slot=frame.hitSlot(pos,player.getEyePosition(),hit.getLocation().add(player.getLookAngle().scale(.01)));
        return slot==null ? AssemblyFrameBlock.interact(level,pos,player) : InteractionResult.PASS;
    }
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (level.isClientSide) return false;
        if (level.getBlockEntity(pos) instanceof AssemblyCellBlockEntity cell && !level.hasChunkAt(cell.owner())) {
            player.displayClientMessage(Component.translatable("message.tm_wagon.controller_unloaded"),true); return false;
        }
        var frame = AssemblyFrameBlockEntity.find(level,pos);
        if (frame == null) return level.removeBlock(pos,false);
        var eye = player.getEyePosition();
        var slot = frame.hitSlot(pos,eye,eye.add(player.getLookAngle().scale(player.blockInteractionRange()+1)));
        if (slot != null) frame.remove(EnumSet.of(slot),!player.isCreative());
        if (slot == null && frame.layout().containsKey(pos) && frame.layout().get(pos).framePart()) { frame.dismantle(!player.isCreative(),true); return true; }
        return slot != null;
    }
    @Override public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity entity, ItemStack tool) {}
    @Override public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        var frame = AssemblyFrameBlockEntity.find(level,pos);
        if (frame == null) return ItemStack.EMPTY;
        var eye = player.getEyePosition();
        var slot = frame.hitSlot(pos,eye,target.getLocation().add(player.getLookAngle().scale(.01)));
        return slot == null ? new ItemStack(WagonContent.FRAME_ITEM.get()) : frame.partStack(slot);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide) {
            var frame = AssemblyFrameBlockEntity.find(level,pos);
            if (frame != null && !frame.changing() && frame.layout().containsKey(pos)) {
                if (frame.layout().get(pos).framePart()) frame.dismantle(true,true);
                else frame.remove(frame.layout().get(pos).slots(),true);
            }
        }
        super.onRemove(state,level,pos,next,moved);
    }
}
