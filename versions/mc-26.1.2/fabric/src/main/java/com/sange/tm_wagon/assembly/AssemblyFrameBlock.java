package com.sange.tm_wagon.assembly;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AssemblyFrameBlock extends BaseEntityBlock {
    private static final MapCodec<AssemblyFrameBlock> CODEC=simpleCodec(AssemblyFrameBlock::new);
    @Override protected MapCodec<AssemblyFrameBlock> codec() { return CODEC; }
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty EXTENDED = BooleanProperty.create("extended");
    public AssemblyFrameBlock(Properties properties) {
        super(properties.dynamicShape()); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(EXTENDED,true));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING,EXTENDED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite())
            .setValue(EXTENDED,context.getPlayer()==null||!context.getPlayer().isShiftKeyDown());
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return level.getBlockEntity(pos) instanceof AssemblyFrameBlockEntity frame
            ? WagonGeometry.shape(frame.frameCells().getOrDefault(BlockPos.ZERO,java.util.List.of())) : Shapes.block(); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return level.getBlockEntity(pos) instanceof AssemblyFrameBlockEntity frame
            ? WagonGeometry.shape(frame.frameCells().getOrDefault(BlockPos.ZERO,java.util.List.of())) : Shapes.block(); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AssemblyFrameBlockEntity frame) frame.ensureFrame();
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AssemblyFrameBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, WagonContent.FRAME_ENTITY.get(), (world,pos,s,frame) -> {
            frame.tickFrame();
            frame.cargo().tick();
            if (world.getGameTime() % 20 == 0) frame.validateLoadedCells();
        });
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        return stack.getItem() instanceof WagonPartItem || stack.getItem() instanceof AssemblyFrameItem
            ? InteractionResult.PASS : InteractionResult.TRY_WITH_EMPTY_HAND;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        return interact(level,pos,player);
    }
    public static InteractionResult interact(Level level,BlockPos pos,Player player) {
        var frame = AssemblyFrameBlockEntity.find(level,pos);
        if (frame == null) return InteractionResult.PASS;
        // Consume ignored input so it cannot fall through to item use. No queued
        // toggle, timeline restart or repeated error message during animation.
        if (frame.switching()) return InteractionResult.CONSUME;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        String error = frame.toggleFrame(player);
        if (error != null) { player.sendOverlayMessage(Component.translatable(error)); return InteractionResult.FAIL; }
        return InteractionResult.CONSUME;
    }
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack, boolean willHarvest, FluidState fluid) {
        if (level.isClientSide()) return false;
        if (level.getBlockEntity(pos) instanceof AssemblyFrameBlockEntity frame) frame.dismantle(!player.isCreative(), true);
        else level.removeBlock(pos, false);
        return true;
    }
    @Override public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity entity, ItemStack tool) {}
}
