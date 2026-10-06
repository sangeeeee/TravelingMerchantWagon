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
        var frame = (AssemblyFrameBlockEntity)helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(frame.initializeFrame()==null,"Frame platform could not be placed");
        return frame;
    }
    private static java.util.Map<BlockPos,java.util.List<net.minecraft.world.phys.AABB>> collisionSnapshot(GameTestHelper helper,AssemblyFrameBlockEntity frame) {
        var result=new java.util.HashMap<BlockPos,java.util.List<net.minecraft.world.phys.AABB>>();
        result.put(frame.getBlockPos(),frame.getBlockState().getCollisionShape(helper.getLevel(),frame.getBlockPos()).toAabbs());
        frame.layout().forEach((pos,cell)->{
            var boxes=helper.getLevel().getBlockState(pos).getCollisionShape(helper.getLevel(),pos).toAabbs();
            if(!boxes.isEmpty())result.put(pos,boxes);
        });
        return result;
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
        BlockPos obstruction = frame.getBlockPos().offset(0,1,2);
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
        for (BlockPos pos : positions) if (!frame.layout().containsKey(pos)) helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(),"Left an orphan cell at "+pos);
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
        var hit = new BlockHitResult(Vec3.atLowerCornerOf(frame.getBlockPos()).add(.5,1.375,.5),Direction.UP,frame.getBlockPos().above(),false);
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
    public static void shafts_can_install_from_every_front_centre_body_cell(GameTestHelper helper) {
        var player=helper.makeMockPlayer(GameType.SURVIVAL);
        player.getAbilities().instabuild=false;
        for(var body:new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY})
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            var f=frame(helper,facing);install(helper,f,WagonSlot.BODY,body);
            double front=WagonGeometry.partBoxes(body).getFirst().minZ;
            var positions=f.layout().entrySet().stream().filter(e-> {
                var local=f.cargoPose().local(Vec3.atCenterOf(e.getKey()));
                return e.getValue().slots().contains(WagonSlot.BODY)&&Math.abs(local.x)<.001&&local.z<=front+.5;
            }).map(java.util.Map.Entry::getKey).toList();
            helper.assertTrue(positions.size()>1,"No front centre cells at different heights/distances");
            for(var shafts:new WagonPart[]{WagonPart.SINGLE_HORSE_SHAFTS,WagonPart.DOUBLE_HORSE_SHAFTS})
            for(var pos:positions) {
                var stack=item(shafts);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var hit=new BlockHitResult(Vec3.atCenterOf(pos),facing,pos,false);
                var result=stack.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
                helper.assertTrue(result==InteractionResult.CONSUME&&f.part(WagonSlot.SHAFTS)==shafts&&stack.getCount()==1,"Front body cell rejected shafts: "+body+" / "+facing+" / "+pos);
                f.remove(EnumSet.of(WagonSlot.SHAFTS),false);
            }
            var stack=item(WagonPart.SINGLE_SEAT);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var pos=positions.stream().min(java.util.Comparator.comparingInt(BlockPos::getY)).orElseThrow();
            var hit=new BlockHitResult(Vec3.atCenterOf(pos),facing,pos,false);
            helper.assertTrue(stack.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit))==InteractionResult.FAIL&&stack.getCount()==2,"Shaft allowance also broadened seat placement");
            f.dismantle(false,true);
        }
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
    @GameTest(template="assembly_test")
    public static void frame_platform_placement_and_breaking(GameTestHelper helper) {
        var origin=helper.absolutePos(new BlockPos(11,2,17));
        var player=helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0);
        var stack=new ItemStack(WagonContent.FRAME_ITEM.get(),2);
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        helper.getLevel().setBlock(origin.below(),Blocks.STONE.defaultBlockState(),3);
        var hit=new BlockHitResult(Vec3.atLowerCornerOf(origin).add(.5,0,.5),Direction.UP,origin.below(),false);
        var obstruction=origin.offset(1,1,0);
        helper.getLevel().setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);
        helper.assertTrue(stack.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit))==InteractionResult.FAIL,"Platform obstruction must reject frame placement");
        helper.assertTrue(stack.getCount()==2&&helper.getLevel().getBlockState(origin).isAir(),"Rejected frame left a partial structure or consumed an item");
        helper.getLevel().setBlock(obstruction,Blocks.AIR.defaultBlockState(),3);
        helper.assertTrue(stack.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit))==InteractionResult.CONSUME,"Frame item placement failed");
        var frame=AssemblyFrameBlockEntity.find(helper.getLevel(),origin);
        helper.assertTrue(frame!=null&&stack.getCount()==1,"Frame placement must consume one item");
        var top=origin.above();
        var shape=helper.getLevel().getBlockState(top).getCollisionShape(helper.getLevel(),top);
        helper.assertTrue(shape.bounds().maxY==.375,"Platform collision must end at the cargo floor underside");
        var body=item(WagonPart.CARGO_BODY);player.setItemInHand(InteractionHand.MAIN_HAND,body);
        var lowerHit=new BlockHitResult(Vec3.atLowerCornerOf(origin).add(.5,1,.5),Direction.UP,origin,false);
        helper.assertTrue(body.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,lowerHit))==InteractionResult.FAIL,"Lower support must not count as the top platform");
        // Click the edge, outside the root cell: it still belongs to this platform.
        var edgeHit=new BlockHitResult(Vec3.atLowerCornerOf(origin).add(1.1,1.375,.5),Direction.UP,obstruction,false);
        helper.assertTrue(body.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,edgeHit))==InteractionResult.CONSUME&&frame.has(WagonSlot.BODY),"Platform edge must support cargo placement");
        var positions=java.util.Set.copyOf(frame.layout().keySet());
        helper.getLevel().setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);
        helper.assertTrue(helper.getLevel().getBlockState(origin).isAir(),"Breaking a platform section must remove the whole frame");
        for(var pos:positions) if(!pos.equals(obstruction)) helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(),"Platform break left an orphan");
        helper.assertTrue(helper.getLevel().getBlockState(obstruction).is(Blocks.STONE),"Frame cleanup overwrote an external replacement");
        var drops=helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,helper.getBounds());
        helper.assertTrue(drops.stream().mapToInt(e->e.getItem().getCount()).sum()==2,"Platform dismantling must drop the frame and body exactly once");
        helper.succeed();
    }

    @GameTest(template="assembly_test")
    public static void simplified_collision_and_extended_cargo(GameTestHelper helper) {
        var body=WagonGeometry.cells(WagonPart.CARGO_BODY,WagonSlot.BODY,Direction.NORTH);
        helper.assertTrue(body.containsKey(new BlockPos(0,1,2)),"Extended rear cargo floor is missing collision");
        var interior=WagonGeometry.shape(body.get(new BlockPos(0,2,0))==null?java.util.List.of():body.get(new BlockPos(0,2,0)));
        helper.assertTrue(interior.isEmpty(),"Cargo interior must remain an open box");
        var wheels=WagonGeometry.cells(WagonPart.SMALL_WHEEL,WagonSlot.FRONT_LEFT,Direction.NORTH);
        var origin=helper.absolutePos(new BlockPos(11,2,17));
        var cell=origin.offset(-1,0,-1);
        var eye=new Vec3(origin.getX()-3,origin.getY()+10.5/16,origin.getZ()+.5-20.0/16+.15);
        helper.assertTrue(WagonGeometry.shape(wheels.get(new BlockPos(-1,0,-1))).clip(eye,eye.add(5,0,0),cell)!=null,"Wheel spokes must not leave holes in gameplay collision");
        helper.succeed();
    }

    @GameTest(template="assembly_test",timeoutTicks=80)
    public static void frame_right_click_fold_unfold_and_save(GameTestHelper helper) {
        var frame=frame(helper,Direction.NORTH);
        var origin=frame.getBlockPos();
        var player=helper.makeMockPlayer(GameType.SURVIVAL);
        var hit=new BlockHitResult(Vec3.atCenterOf(origin),Direction.NORTH,origin,false);
        var raisedCollision=collisionSnapshot(helper,frame);
        var loweredCollision=new java.util.HashMap<BlockPos,java.util.List<net.minecraft.world.phys.AABB>>();
        helper.assertTrue(frame.extended()&&frame.frameProgress(0)==0,"Placed frames must begin fully extended");
        helper.assertTrue(frame.getBlockState().useWithoutItem(helper.getLevel(),player,hit)==InteractionResult.CONSUME,"Empty-hand right click must start folding");
        helper.assertTrue(!frame.extended()&&frame.frameMoving(),"Right click did not change target state");
        helper.assertTrue(frame.getBlockState().getValue(AssemblyFrameBlock.EXTENDED),"Block state must commit only after the animation");
        helper.assertTrue(collisionSnapshot(helper,frame).equals(raisedCollision),"Starting the animation changed collision");
        for(int tick=1;tick<20;tick++)helper.runAtTickTime(tick,()->helper.assertTrue(collisionSnapshot(helper,frame).equals(raisedCollision),"Collision changed during folding"));
        helper.runAtTickTime(10,()->{
            helper.assertTrue(frame.frameProgress(0)>0&&frame.frameProgress(0)<1,"Folding must animate through intermediate poses");
            helper.assertTrue(helper.getLevel().getBlockEntity(origin.offset(1,0,0)) instanceof AssemblyCellBlockEntity,"Motion must reserve the lower platform cells");
        });
        helper.runAtTickTime(24,()->{
            helper.assertTrue(!frame.frameMoving()&&frame.frameProgress(0)==1,"Fold animation did not finish");
            var shape=frame.getBlockState().getCollisionShape(helper.getLevel(),origin);
            helper.assertTrue(Math.abs(shape.bounds().maxY-FrameMotion.collisionTop(1))<1e-6,"Collision did not follow the lowered platform");
            helper.assertTrue(helper.getLevel().getBlockState(origin.above()).isAir(),"Folded frame left elevated collision cells");
            var copy=new AssemblyFrameBlockEntity(origin,frame.getBlockState());
            copy.loadWithComponents(frame.saveWithFullMetadata(helper.getLevel().registryAccess()),helper.getLevel().registryAccess());
            helper.assertTrue(!copy.extended()&&copy.frameProgress(0)==1&&copy.layout().keySet().equals(frame.layout().keySet()),"Save/load lost the folded pose");
            loweredCollision.putAll(collisionSnapshot(helper,frame));
            var body=item(WagonPart.CARGO_BODY);
            helper.assertTrue("message.tm_wagon.frame_extend_first".equals(frame.install(WagonSlot.BODY,WagonPart.CARGO_BODY,null,body))&&body.getCount()==2,"Folded frames must reject cargo placement without consuming items");
            helper.assertTrue(frame.getBlockState().useWithoutItem(helper.getLevel(),player,hit)==InteractionResult.CONSUME,"Second right click must unfold");
        });
        for(int tick=25;tick<44;tick++)helper.runAtTickTime(tick,()->helper.assertTrue(collisionSnapshot(helper,frame).equals(loweredCollision),"Collision changed during unfolding"));
        helper.runAtTickTime(48,()->{
            helper.assertTrue(frame.extended()&&!frame.frameMoving()&&frame.frameProgress(0)==0,"Unfolding did not return to the extended state");
            helper.assertTrue(collisionSnapshot(helper,frame).equals(raisedCollision),"Completed unfolding did not restore collision");
            helper.assertTrue(frame.platformTop(new BlockHitResult(Vec3.atLowerCornerOf(origin).add(.5,1.375,.5),Direction.UP,origin.above(),false)),"Raised platform no longer accepts cargo");
            helper.assertTrue(helper.getLevel().getBlockState(origin.offset(1,0,0)).isAir(),"Unfolding left a lower reservation cell");
            helper.succeed();
        });
    }

    @GameTest(template="assembly_test")
    public static void frame_motion_rejects_obstructions_and_preserves_item_use(GameTestHelper helper) {
        var frame=frame(helper,Direction.EAST);
        var origin=frame.getBlockPos();
        var obstruction=origin.offset(1,0,0);
        helper.getLevel().setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);
        helper.assertTrue("message.tm_wagon.blocked".equals(frame.toggleFrame(null))&&frame.extended()&&!frame.frameMoving(),"Blocked folding changed the frame or overwrote a block");
        helper.getLevel().setBlock(obstruction,Blocks.AIR.defaultBlockState(),3);
        var pig=helper.spawn(net.minecraft.world.entity.EntityType.PIG,new BlockPos(12,2,17));
        helper.assertTrue("message.tm_wagon.entity_blocked".equals(frame.toggleFrame(null)),"Entities in the platform sweep must block folding");
        pig.discard();
        var player=helper.makeMockPlayer(GameType.CREATIVE);
        var body=item(WagonPart.CARGO_BODY);
        var hit=new BlockHitResult(Vec3.atLowerCornerOf(origin).add(.5,1.375,.5),Direction.UP,origin.above(),false);
        var state=helper.getLevel().getBlockState(origin.above());
        helper.assertTrue(state.useItemOn(body,helper.getLevel(),player,InteractionHand.MAIN_HAND,hit)==net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION,"Component use must not toggle the frame");
        helper.assertTrue(frame.extended()&&!frame.frameMoving(),"Holding a component started an animation");
        helper.succeed();
    }

    @GameTest(template="assembly_test",timeoutTicks=70)
    public static void clicks_during_animation_are_ignored_until_state_commits(GameTestHelper helper) {
        var frame=frame(helper,Direction.SOUTH);
        var player=helper.makeMockPlayer(GameType.CREATIVE);
        var hit=new BlockHitResult(Vec3.atCenterOf(frame.getBlockPos()),Direction.NORTH,frame.getBlockPos(),false);
        helper.assertTrue(frame.toggleFrame(null)==null,"Empty frame could not fold");
        long start=frame.getUpdateTag(helper.getLevel().registryAccess()).getLong("MotionStart");
        var collision=collisionSnapshot(helper,frame);
        for (int tick=1;tick<20;tick++) helper.runAtTickTime(tick,()->{
            double before=frame.frameProgress(0);
            for (int click=0;click<5;click++) {
                helper.assertTrue("message.tm_wagon.assembly_busy".equals(frame.toggleFrame(null)),"Moving frame accepted a repeated toggle");
                helper.assertTrue(frame.getBlockState().useWithoutItem(helper.getLevel(),player,hit)==InteractionResult.CONSUME,"Ignored click fell through to item use");
            }
            helper.assertTrue(frame.frameProgress(0)==before,"Repeated input changed animation pose");
            helper.assertTrue(frame.getUpdateTag(helper.getLevel().registryAccess()).getLong("MotionStart")==start,"Repeated input restarted the timeline");
            helper.assertTrue(collisionSnapshot(helper,frame).equals(collision),"Repeated input changed collision before animation ended");
        });
        helper.runAtTickTime(24,()->{
            helper.assertTrue(!frame.extended()&&!frame.switching()&&!frame.getBlockState().getValue(AssemblyFrameBlock.EXTENDED),"Fold did not commit before unlocking");
            helper.assertTrue(frame.toggleFrame(null)==null,"Committed folded state could not unfold");
        });
        helper.runAtTickTime(34,()->{
            helper.assertTrue("message.tm_wagon.assembly_busy".equals(frame.toggleFrame(null)),"Unfolding accepted a reverse click");
            // An elapsed animation is still busy until its collision/block-state commit.
            var copy=new AssemblyFrameBlockEntity(frame.getBlockPos(),frame.getBlockState());copy.setLevel(helper.getLevel());
            var tag=frame.getUpdateTag(helper.getLevel().registryAccess());tag.putLong("MotionStart",helper.getLevel().getGameTime()-20);
            copy.loadWithComponents(tag,helper.getLevel().registryAccess());
            helper.assertTrue(!copy.frameMoving()&&copy.switching(),"Elapsed animation unlocked before state commit");
            helper.assertTrue("message.tm_wagon.assembly_busy".equals(copy.toggleFrame(null)),"Uncommitted endpoint accepted a toggle");
        });
        helper.runAtTickTime(48,()->{
            helper.assertTrue(frame.extended()&&!frame.switching(),"Unfold did not commit");
            helper.assertTrue(collisionSnapshot(helper,frame).equals(collision),"Completed cycle changed extended collision");
            helper.succeed();
        });
    }

    @GameTest(template="assembly_test",timeoutTicks=80)
    public static void frame_packet_order_does_not_flash_end_pose(GameTestHelper helper) {
        var frame=frame(helper,Direction.NORTH);
        var original=frame.getBlockState();
        var receiver=new AssemblyFrameBlockEntity(frame.getBlockPos(),original);
        receiver.setLevel(helper.getLevel());
        receiver.setBlockState(original.setValue(AssemblyFrameBlock.EXTENDED,false));
        helper.assertTrue(receiver.frameProgress(0)==0,"Block-state packet exposed the folded endpoint before animation data");
        helper.assertTrue(frame.toggleFrame(null)==null,"Could not start packet-order test animation");
        receiver.loadWithComponents(frame.getUpdateTag(helper.getLevel().registryAccess()),helper.getLevel().registryAccess());
        helper.assertTrue(receiver.frameProgress(0)==frame.frameProgress(0)&&receiver.frameProgress(0)<.01,"Animation data must start at the previous pose");
        helper.runAtTickTime(10,()->{
            receiver.loadWithComponents(frame.getUpdateTag(helper.getLevel().registryAccess()),helper.getLevel().registryAccess());
            helper.assertTrue(Math.abs(receiver.frameProgress(0)-frame.frameProgress(0))<1e-6,"Mid-animation sync changed the visual progress");
        });
        helper.runAtTickTime(24,()->{
            var folded=frame.getBlockState();
            var unfoldReceiver=new AssemblyFrameBlockEntity(frame.getBlockPos(),folded);
            unfoldReceiver.setLevel(helper.getLevel());
            unfoldReceiver.setBlockState(folded.setValue(AssemblyFrameBlock.EXTENDED,true));
            helper.assertTrue(unfoldReceiver.frameProgress(0)==1,"Block-state packet exposed the extended endpoint before animation data");
            helper.assertTrue(frame.toggleFrame(null)==null,"Could not start unfolding packet-order test");
            unfoldReceiver.loadWithComponents(frame.getUpdateTag(helper.getLevel().registryAccess()),helper.getLevel().registryAccess());
            helper.assertTrue(unfoldReceiver.frameProgress(0)==frame.frameProgress(0)&&unfoldReceiver.frameProgress(0)>.99,"Unfolding data must start at the folded pose");
            helper.succeed();
        });
    }

}
