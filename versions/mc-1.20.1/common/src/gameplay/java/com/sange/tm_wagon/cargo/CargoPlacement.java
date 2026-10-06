package com.sange.tm_wagon.cargo;

import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Vanilla placement rules expressed in wagon-local coordinates, without placing a world block. */
final class CargoPlacement {
    private static final java.util.Set<net.minecraft.world.level.block.Block> FALLBACKS=new java.util.HashSet<>();
    static BlockState state(CargoHold hold,int slot,ItemStack stack,Player player) {
        var pose=hold.owner().cargoPose();var centre=hold.centreAt(slot).add(0,CargoHold.SCALE/2,0);
        Vec3 from=pose.local(player.getEyePosition());Vec3 toward=centre.subtract(from);
        if(toward.horizontalDistanceSqr()<1e-6)toward=pose.local(player.position().add(player.getLookAngle())).subtract(pose.local(player.position()));
        final Vec3 direction=toward;
        var context=new BlockPlaceContext(new CargoLevel(hold,null),player,InteractionHand.MAIN_HAND,stack,
            new BlockHitResult(Vec3.atCenterOf(CargoLevel.POS),Direction.UP,CargoLevel.POS,false)) {
            @Override public Direction getHorizontalDirection() { return Math.abs(direction.x)>Math.abs(direction.z)?(direction.x>0?Direction.EAST:Direction.WEST):(direction.z>0?Direction.SOUTH:Direction.NORTH); }
            @Override public float getRotation() { return (float)Math.toDegrees(Math.atan2(-direction.x,direction.z)); }
            @Override public Direction[] getNearestLookingDirections() {
                var directions=Direction.values().clone();Arrays.sort(directions,Comparator.comparingDouble((Direction d)->direction.dot(Vec3.atLowerCornerOf(d.getNormal()))).reversed());return directions;
            }
            @Override public Direction getNearestLookingDirection() { return getNearestLookingDirections()[0]; }
            @Override public Direction getNearestLookingVerticalDirection() { return direction.y>0?Direction.UP:Direction.DOWN; }
        };
        var block=stack.getItem() instanceof BlockItem item?item.getBlock():com.sange.tm_wagon.compat.BackpackCompat.block(stack);BlockState state;
        try { state=block.getStateForPlacement(context); }
        catch(RuntimeException unsupported) {
            if(FALLBACKS.add(block))com.mojang.logging.LogUtils.getLogger().warn("Using default cargo placement state for {}",block,unsupported);
            state=block.defaultBlockState();
        }
        if(state==null)state=block.defaultBlockState();
        if(state.hasProperty(BlockStateProperties.WATERLOGGED))state=state.setValue(BlockStateProperties.WATERLOGGED,false);
        return state;
    }
    private CargoPlacement() {}
}
