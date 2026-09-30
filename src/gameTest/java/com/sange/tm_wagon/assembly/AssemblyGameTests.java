package com.sange.tm_wagon.assembly;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class AssemblyGameTests {
    private static AssemblyFrameBlockEntity frame(GameTestHelper helper, Direction facing) {
        BlockPos pos = helper.absolutePos(new BlockPos(11,2,17));
        helper.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState().setValue(AssemblyFrameBlock.FACING,facing),3);
        return (AssemblyFrameBlockEntity)helper.getLevel().getBlockEntity(pos);
    }
    private static ItemStack item(WagonPart part) { return new ItemStack(WagonContent.PART_ITEMS.get(part).get(),2); }
    private static void install(GameTestHelper helper, AssemblyFrameBlockEntity frame, WagonSlot slot, WagonPart part) {
        ItemStack stack = item(part);
        String error = frame.install(slot,part,null,stack);
        helper.assertTrue(error == null,"Installation failed: "+error+" "+slot);
        helper.assertTrue(stack.getCount()==1,"Successful placement must consume exactly one item");
    }

    @GameTest(template="assembly_test")
    public static void all_rotations_and_slots(GameTestHelper helper) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            var frame = frame(helper,direction);
            install(helper,frame,WagonSlot.BODY,WagonPart.CARGO_BODY);
            install(helper,frame,WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);
            install(helper,frame,WagonSlot.SHAFTS,WagonPart.DOUBLE_HORSE_SHAFTS);
            for (var slot : new WagonSlot[]{WagonSlot.FRONT_LEFT,WagonSlot.FRONT_RIGHT}) install(helper,frame,slot,WagonPart.SMALL_WHEEL);
            for (var slot : new WagonSlot[]{WagonSlot.REAR_LEFT,WagonSlot.REAR_RIGHT}) install(helper,frame,slot,WagonPart.LARGE_WHEEL);
            helper.assertTrue(frame.parts().size()==7,"All seven mounting slots must be present");
            for (var entry : frame.layout().entrySet()) {
                var cell = (AssemblyCellBlockEntity)helper.getLevel().getBlockEntity(entry.getKey());
                helper.assertTrue(cell != null && cell.owner().equals(frame.getBlockPos()),"Missing ownership at "+entry.getKey());
                for (var box : cell.shape().toAabbs()) helper.assertTrue(box.minX>=0&&box.minY>=0&&box.minZ>=0&&box.maxX<=1&&box.maxY<=1&&box.maxZ<=1,"Collision extends outside a cell");
            }
            helper.assertTrue(helper.getLevel().getBlockState(frame.getBlockPos()).getCollisionShape(helper.getLevel(),frame.getBlockPos()).bounds().equals(new net.minecraft.world.phys.AABB(0,0,0,1,1,1)),"Frame collision must stay one block");
            frame.dismantle(false,true);
        }
        helper.succeed();
    }

    @GameTest(template="assembly_test")
    public static void rejected_placement_is_atomic(GameTestHelper helper) {
        var frame = frame(helper,Direction.NORTH);
        var stack = item(WagonPart.CARGO_BODY);
        BlockPos obstruction = frame.getBlockPos().offset(0,1,1);
        helper.getLevel().setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);
        String error = frame.install(WagonSlot.BODY,WagonPart.CARGO_BODY,null,stack);
        helper.assertTrue("message.tm_wagon.blocked".equals(error),"Obstruction must reject placement");
        helper.assertTrue(stack.getCount()==2 && frame.parts().isEmpty(),"Rejected placement consumed an item or installed a partial module");
        helper.assertTrue(helper.getLevel().getBlockState(obstruction).is(Blocks.STONE),"Obstacle was overwritten");
        helper.getLevel().setBlock(obstruction,Blocks.AIR.defaultBlockState(),3);
        install(helper,frame,WagonSlot.BODY,WagonPart.CARGO_BODY);
        var wrong = item(WagonPart.LARGE_WHEEL);
        helper.assertTrue(frame.install(WagonSlot.FRONT_LEFT,WagonPart.LARGE_WHEEL,null,wrong)!=null && wrong.getCount()==2,"Wrong wheel size was accepted");
        install(helper,frame,WagonSlot.SEAT,WagonPart.SINGLE_SEAT);
        var duplicate = item(WagonPart.DOUBLE_SEAT);
        helper.assertTrue("message.tm_wagon.occupied".equals(frame.install(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT,null,duplicate))&&duplicate.getCount()==2,"Seats must share one exclusive slot");
        helper.succeed();
    }

    @GameTest(template="assembly_test")
    public static void shared_cells_and_body_dismantling(GameTestHelper helper) {
        var frame = frame(helper,Direction.NORTH);
        install(helper,frame,WagonSlot.BODY,WagonPart.CARGO_BODY);
        install(helper,frame,WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);
        install(helper,frame,WagonSlot.FRONT_LEFT,WagonPart.SMALL_WHEEL);
        BlockPos shared = frame.layout().entrySet().stream().filter(e->e.getValue().slots().contains(WagonSlot.BODY)&&e.getValue().slots().contains(WagonSlot.FRONT_LEFT)).findFirst().orElseThrow().getKey();
        frame.remove(EnumSet.of(WagonSlot.FRONT_LEFT),false);
        helper.assertTrue(frame.has(WagonSlot.BODY)&&frame.has(WagonSlot.SEAT)&&!frame.has(WagonSlot.FRONT_LEFT),"Wheel removal deleted another module");
        helper.assertTrue(helper.getLevel().getBlockEntity(shared) instanceof AssemblyCellBlockEntity,"Shared body cell was deleted");
        var positions = java.util.Set.copyOf(frame.layout().keySet());
        frame.remove(EnumSet.of(WagonSlot.BODY),false);
        helper.assertTrue(frame.parts().isEmpty(),"Body removal must remove unsupported attachments");
        for (BlockPos pos : positions) helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(),"Left an orphan cell at "+pos);
        helper.assertTrue(helper.getLevel().getBlockState(frame.getBlockPos()).is(WagonContent.FRAME.get()),"Removing body deleted the frame");
        helper.succeed();
    }

    @GameTest(template="assembly_test")
    public static void persistence_and_external_removal(GameTestHelper helper) {
        var frame = frame(helper,Direction.EAST);
        install(helper,frame,WagonSlot.BODY,WagonPart.CARGO_BODY);
        install(helper,frame,WagonSlot.SHAFTS,WagonPart.SINGLE_HORSE_SHAFTS);
        var saved = frame.saveWithFullMetadata(helper.getLevel().registryAccess());
        var copy = new AssemblyFrameBlockEntity(frame.getBlockPos(),frame.getBlockState());
        copy.loadWithComponents(saved,helper.getLevel().registryAccess());
        helper.assertTrue(copy.parts().equals(frame.parts())&&copy.layout().keySet().equals(frame.layout().keySet()),"Controller save/load lost layout");
        BlockPos cellPos = frame.layout().keySet().iterator().next();
        var cell = (AssemblyCellBlockEntity)helper.getLevel().getBlockEntity(cellPos);
        var cellCopy = new AssemblyCellBlockEntity(cellPos,cell.getBlockState());
        cellCopy.loadWithComponents(cell.saveWithFullMetadata(helper.getLevel().registryAccess()),helper.getLevel().registryAccess());
        helper.assertTrue(cellCopy.owner().equals(cell.owner())&&cellCopy.shape().toAabbs().equals(cell.shape().toAabbs()),"Proxy save/load lost collision");
        BlockPos bodyCell = frame.layout().entrySet().stream().filter(e->e.getValue().slots().contains(WagonSlot.BODY)).findFirst().orElseThrow().getKey();
        helper.getLevel().setBlock(bodyCell,Blocks.STONE.defaultBlockState(),3);
        helper.assertTrue(frame.parts().isEmpty(),"Externally removing a body cell must remove the body and its attachments");
        helper.assertTrue(helper.getLevel().getBlockState(bodyCell).is(Blocks.STONE),"Cleanup overwrote the external replacement");
        helper.succeed();
    }

    @GameTest(template="assembly_test")
    public static void item_use_and_creative_consumption(GameTestHelper helper) {
        var frame = frame(helper,Direction.NORTH);
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild=true;
        var stack = item(WagonPart.CARGO_BODY);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(frame.getBlockPos()).add(0,.5,0),Direction.UP,frame.getBlockPos(),false);
        var result = stack.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
        helper.assertTrue(result==InteractionResult.CONSUME&&frame.has(WagonSlot.BODY)&&stack.getCount()==2,"Creative item use failed or consumed the item");
        var seat = item(WagonPart.SINGLE_SEAT);player.setItemInHand(InteractionHand.MAIN_HAND,seat);
        helper.assertTrue(seat.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit))==InteractionResult.FAIL,"Wrong clicked cell must reject a seat");
        BlockPos seatPos=WagonSlot.SEAT.position(frame.getBlockPos(),frame.facing());
        var seatHit=new BlockHitResult(Vec3.atCenterOf(seatPos.below()).add(0,.5,0),Direction.UP,seatPos.below(),false);
        helper.assertTrue(seat.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,seatHit))==InteractionResult.CONSUME&&frame.has(WagonSlot.SEAT),"Seat should install by clicking the footboard top");
        helper.succeed();
    }

    @GameTest(template="assembly_test")
    public static void unique_drops(GameTestHelper helper) {
        var frame = frame(helper,Direction.NORTH);
        install(helper,frame,WagonSlot.BODY,WagonPart.CARGO_BODY);
        install(helper,frame,WagonSlot.SEAT,WagonPart.SINGLE_SEAT);
        install(helper,frame,WagonSlot.FRONT_LEFT,WagonPart.SMALL_WHEEL);
        frame.dismantle(true,true);
        var drops=helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,helper.getBounds());
        int total=drops.stream().mapToInt(e->e.getItem().getCount()).sum();
        helper.assertTrue(total==4,"Expected exactly four module/frame drops, got "+total);
        helper.succeed();
    }

    @GameTest(template="assembly_test")
    public static void creative_break_and_entity_obstruction(GameTestHelper helper) {
        var frame = frame(helper,Direction.NORTH);
        var pig=helper.spawn(net.minecraft.world.entity.EntityType.PIG,new BlockPos(11,3,17));
        var body=item(WagonPart.CARGO_BODY);
        helper.assertTrue("message.tm_wagon.entity_blocked".equals(frame.install(WagonSlot.BODY,WagonPart.CARGO_BODY,null,body))&&body.getCount()==2,"Entities in new cargo volume must block placement without item loss");
        pig.discard();
        install(helper,frame,WagonSlot.BODY,WagonPart.CARGO_BODY);
        install(helper,frame,WagonSlot.SEAT,WagonPart.SINGLE_SEAT);
        var positions=java.util.Set.copyOf(frame.layout().keySet());
        var player=helper.makeMockPlayer(GameType.CREATIVE);player.getAbilities().instabuild=true;
        var state=frame.getBlockState();
        state.getBlock().onDestroyedByPlayer(state,helper.getLevel(),frame.getBlockPos(),player,false,state.getFluidState());
        for(BlockPos pos:positions)helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(),"Creative frame break left an orphan");
        helper.assertTrue(helper.getLevel().getBlockState(frame.getBlockPos()).isAir(),"Creative break left the frame");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,helper.getBounds()).isEmpty(),"Creative dismantling must not drop parts");
        helper.succeed();
    }

    @GameTest(template="assembly_test")
    public static void survival_break_targets_one_module(GameTestHelper helper) {
        var frame=frame(helper,Direction.NORTH);
        install(helper,frame,WagonSlot.BODY,WagonPart.CARGO_BODY);
        install(helper,frame,WagonSlot.FRONT_LEFT,WagonPart.SMALL_WHEEL);
        var origin=frame.getBlockPos();
        var pos=WagonSlot.FRONT_LEFT.position(origin,frame.facing());
        var eye=new Vec3(origin.getX()-3,origin.getY()+10.5/16,origin.getZ()+.5-20.0/16);
        helper.assertTrue(frame.hitSlot(pos,eye,eye.add(5,0,0))==WagonSlot.FRONT_LEFT,"Ray selected body rather than the wheel in a shared cell");
        var player=helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(eye.x,eye.y-player.getEyeHeight(),eye.z);player.setYRot(-90);player.setXRot(0);
        var state=helper.getLevel().getBlockState(pos);
        helper.assertTrue(state.getBlock().onDestroyedByPlayer(state,helper.getLevel(),pos,player,true,state.getFluidState()),"Player could not break the wheel");
        helper.assertTrue(frame.has(WagonSlot.BODY)&&!frame.has(WagonSlot.FRONT_LEFT),"Wheel mining removed the cargo body");
        var drops=helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,helper.getBounds());
        helper.assertTrue(drops.size()==1&&drops.getFirst().getItem().is(WagonContent.PART_ITEMS.get(WagonPart.SMALL_WHEEL).get()),"Wheel mining must drop exactly one wheel");
        helper.succeed();
    }
}
