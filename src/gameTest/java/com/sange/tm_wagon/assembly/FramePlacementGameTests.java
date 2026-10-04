package com.sange.tm_wagon.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class FramePlacementGameTests {
    @GameTest(template="assembly_test",timeoutTicks=50)
    public static void sneak_placement_starts_folded_and_only_reserves_folded_space(GameTestHelper h) {
        var level=h.getLevel();var root=h.absolutePos(new BlockPos(11,2,17));level.setBlock(root.below(),Blocks.STONE.defaultBlockState(),3);
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(root).add(-3,0,0));
        var stack=new ItemStack(WagonContent.FRAME_ITEM.get(),3);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3.atBottomCenterOf(root),Direction.UP,root.below(),false);
        var context=new UseOnContext(player,InteractionHand.MAIN_HAND,hit);
        h.assertTrue(stack.getItem().useOn(context).consumesAction(),"Ordinary frame placement failed");
        var extended=(AssemblyFrameBlockEntity)level.getBlockEntity(root);
        h.assertTrue(extended.extended()&&!extended.switching()&&extended.frameProgress(0)==0&&stack.getCount()==2,"Ordinary placement did not start fully extended");
        extended.dismantle(false,true);level.setBlock(root.above(),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!stack.getItem().useOn(context).consumesAction()&&stack.getCount()==2&&level.getBlockEntity(root)==null,"Extended placement ignored obstruction or consumed item");
        player.setShiftKeyDown(true);
        h.assertTrue(stack.getItem().useOn(context).consumesAction(),"Folded placement unnecessarily reserved expanded platform");
        var folded=(AssemblyFrameBlockEntity)level.getBlockEntity(root);
        h.assertTrue(!folded.extended()&&!folded.switching()&&folded.frameProgress(0)==1&&!folded.acceptsParts()&&stack.getCount()==1,"Sneak placement did not start fully folded");
        h.assertTrue(!folded.getBlockState().getValue(AssemblyFrameBlock.EXTENDED)&&level.getBlockState(root.above()).is(Blocks.STONE),"Placement changed ceiling or left expanded block state");
        double top=folded.frameCells().entrySet().stream().flatMap(e->e.getValue().stream().map(b->e.getKey().getY()+b.maxY)).mapToDouble(Double::doubleValue).max().orElseThrow();
        h.assertTrue(Math.abs(top-FrameMotion.collisionTop(1))<.0001,"Folded collision does not match initial model");
        h.assertTrue(folded.toggleFrame(player)!=null&&!folded.switching(),"Blocked unfolded motion was allowed");
        level.removeBlock(root.above(),false);h.assertTrue(folded.toggleFrame(player)==null,"Folded placement cannot unfold normally");
        h.runAtTickTime(25,()->{h.assertTrue(folded.extended()&&!folded.switching()&&folded.acceptsParts(),"Placed folded lift failed to finish unfolding");h.succeed();});
    }
}
