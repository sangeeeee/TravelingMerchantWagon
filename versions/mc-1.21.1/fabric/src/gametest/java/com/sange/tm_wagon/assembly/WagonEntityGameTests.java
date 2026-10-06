package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.entity.WagonEntity;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WagonEntityGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
    private static void aimSeat(WagonEntity w,net.minecraft.world.entity.player.Player p,double x) {
        Vec3 eye=w.pose().point(new Vec3(x,2.65,-2.875)),target=w.pose().point(new Vec3(x,2.15,-1.875));
        p.setPos(eye.subtract(0,p.getEyeHeight(),0));Vec3 d=target.subtract(eye).normalize();
        p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.asin(d.y)));
    }
    private static AssemblyFrameBlockEntity frame(GameTestHelper helper,Direction facing) {
        BlockPos pos=helper.absolutePos(new BlockPos(11,2,17));
        helper.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState().setValue(AssemblyFrameBlock.FACING,facing),3);
        var frame=(AssemblyFrameBlockEntity)helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(frame.initializeFrame()==null,"Could not place frame"); return frame;
    }
    private static void install(GameTestHelper helper,AssemblyFrameBlockEntity frame,Map<WagonSlot,WagonPart> parts) {
        var order=new java.util.ArrayList<WagonSlot>(); order.add(WagonSlot.BODY);
        for (WagonSlot slot : WagonSlot.values()) if (slot!=WagonSlot.BODY) order.add(slot);
        for (WagonSlot slot : order) if (parts.containsKey(slot)) {
            var part=parts.get(slot);
            String error=frame.install(slot,part,null,new ItemStack(WagonContent.PART_ITEMS.get(part).get()));
            helper.assertTrue(error==null,"Install failed: "+slot+" "+error);
        }
    }
    private static WagonEntity wagon(GameTestHelper helper,AssemblyFrameBlockEntity frame) {
        var list=helper.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(frame.getBlockPos()).inflate(7));
        helper.assertTrue(list.size()==1,"Expected exactly one wagon, got "+list.size()); return list.getFirst();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=20)
    public void incomplete_parts_never_fold(GameTestHelper helper) {
        var frame=frame(helper,Direction.NORTH);
        var parts=new EnumMap<WagonSlot,WagonPart>(WagonSlot.class);parts.putAll(WagonEntity.defaultParts());
        for (WagonSlot missing : WagonSlot.values()) {
            var incomplete=new EnumMap<>(parts);incomplete.remove(missing);
            // Other modules require the cargo body, so test the missing body via the completion predicate.
            if (missing==WagonSlot.BODY) { helper.assertTrue(!AssemblyFrameBlockEntity.complete(incomplete),"Body became optional"); continue; }
            install(helper,frame,incomplete);
            helper.assertTrue("message.tm_wagon.incomplete_assembly".equals(frame.toggleFrame(null)),"Incomplete cart started assembling: "+missing);
            helper.assertTrue(frame.extended()&&!frame.frameMoving()&&frame.parts().equals(incomplete),"Failed assembly changed parts or pose");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(frame.getBlockPos()).inflate(7)).isEmpty(),"Incomplete assembly spawned entity");
            frame.remove(java.util.Set.of(WagonSlot.BODY),false);
        }
        helper.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=60)
    public void all_parts_require_fully_extended_frame(GameTestHelper helper) {
        var frame=frame(helper,Direction.NORTH);
        helper.assertTrue(frame.toggleFrame(null)==null,"Empty frame could not fold");
        for (var entry : WagonEntity.defaultParts().entrySet()) {
            var stack=new ItemStack(WagonContent.PART_ITEMS.get(entry.getValue()).get());
            helper.assertTrue("message.tm_wagon.frame_extend_first".equals(frame.install(entry.getKey(),entry.getValue(),null,stack)),"Moving frame accepted part");
            helper.assertTrue(stack.getCount()==1,"Rejected moving placement consumed item");
        }
        helper.runAtTickTime(24,()->{
            for (var entry : WagonEntity.defaultParts().entrySet()) helper.assertTrue("message.tm_wagon.frame_extend_first".equals(frame.install(entry.getKey(),entry.getValue(),null,
                new ItemStack(WagonContent.PART_ITEMS.get(entry.getValue()).get()))),"Folded frame accepted a part");
            helper.succeed();
        });
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=900)
    public void every_variant_and_rotation_round_trips(GameTestHelper helper) { roundTrip(helper,0); }
    private static void roundTrip(GameTestHelper helper,int index) {
        if (index==16) { helper.succeed(); return; }
        var direction=new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}[index/4];
        var frame=frame(helper,direction);
        var modules=new EnumMap<WagonSlot,WagonPart>(WagonSlot.class);modules.putAll(WagonEntity.defaultParts());
        if ((index&1)!=0) modules.put(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);
        if ((index&2)!=0) modules.put(WagonSlot.SHAFTS,WagonPart.DOUBLE_HORSE_SHAFTS);
        install(helper,frame,modules);
        helper.assertTrue(frame.toggleFrame(null)==null,"Complete wagon did not assemble");
        var entity=wagon(helper,frame);
        helper.assertTrue(frame.parts().isEmpty()&&entity.parts().equals(modules),"Entity conversion duplicated or lost modules");
        helper.assertTrue(frame.frameMoving()&&!frame.extended(),"Assembly did not start folding immediately");
        helper.assertTrue(entity.facing()==direction,"Entity facing changed");
        var tag=new net.minecraft.nbt.CompoundTag();entity.saveWithoutId(tag);
        var copy=WagonContent.WAGON.get().create(helper.getLevel());copy.load(tag);
        helper.assertTrue(copy.parts().equals(modules)&&copy.facing()==direction,"Saved wagon lost variant or rotation");
        Vec3 center=Vec3.atBottomCenterOf(frame.getBlockPos());
        entity.setPos(center.add(.6,0,-.4)); // Alignment tolerance, without an exact docking requirement.
        helper.runAfterDelay(24,()->{
            helper.assertTrue(frame.toggleFrame(null)==null,"Nearby wagon was not selected for restoration");
            helper.assertTrue(!entity.isRemoved()&&frame.parts().isEmpty(),"Wagon converted before lift animation finished");
            helper.assertTrue("message.tm_wagon.assembly_busy".equals(frame.toggleFrame(null)),"Pending restoration could be interrupted");
            helper.runAfterDelay(24,()->{
                helper.assertTrue(entity.isRemoved()&&frame.parts().equals(modules),"Round trip did not preserve all modules");
                helper.assertTrue(frame.extended()&&!frame.frameMoving(),"Restored wagon left the lift folded");
                frame.dismantle(false,true);roundTrip(helper,index+1);
            });
        });
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=110)
    public void blocked_restoration_is_atomic_and_refolds(GameTestHelper helper) {
        var frame=frame(helper,Direction.NORTH);install(helper,frame,WagonEntity.defaultParts());
        helper.assertTrue(frame.toggleFrame(null)==null,"Assembly failed");var entity=wagon(helper,frame);
        BlockPos obstruction=frame.getBlockPos().offset(0,1,2);
        helper.runAtTickTime(24,()->{
            helper.assertTrue(frame.toggleFrame(null)==null,"Restore did not begin");
            helper.getLevel().setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);
        });
        helper.runAtTickTime(49,()->{
            helper.assertTrue(!entity.isRemoved()&&frame.parts().isEmpty(),"Failed restoration deleted wagon or installed partial blocks");
            helper.assertTrue(helper.getLevel().getBlockState(obstruction).is(Blocks.STONE),"Failure overwrote obstruction");
            helper.assertTrue(frame.frameMoving()&&!frame.extended(),"Failure did not automatically refold");
        });
        helper.runAtTickTime(74,()->{
            helper.assertTrue(!frame.extended()&&!frame.frameMoving(),"Failure return animation did not finish");
            helper.getLevel().setBlock(obstruction,Blocks.AIR.defaultBlockState(),3);
            helper.assertTrue(frame.toggleFrame(null)==null,"Failed restoration could not be retried");
        });
        helper.runAtTickTime(99,()->{helper.assertTrue(entity.isRemoved()&&AssemblyFrameBlockEntity.complete(frame.parts()),"Retry lost the wagon");helper.succeed();});
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=90)
    public void restoring_unseats_players_and_mobs(GameTestHelper helper) {
        var frame=frame(helper,Direction.EAST);
        var modules=new EnumMap<WagonSlot,WagonPart>(WagonSlot.class);modules.putAll(WagonEntity.defaultParts());modules.put(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);
        install(helper,frame,modules);helper.assertTrue(frame.toggleFrame(null)==null,"Assembly failed");var entity=wagon(helper,frame);
        var player=helper.makeMockPlayer(GameType.SURVIVAL);
        var pig=EntityType.PIG.create(helper.getLevel());pig.setPos(entity.position());helper.getLevel().addFreshEntity(pig);pig.setNoAi(true);
        helper.assertTrue(player.startRiding(entity)&&pig.startRiding(entity),"Two-seat wagon rejected passengers");
        var extra=EntityType.PIG.create(helper.getLevel());helper.assertTrue(!extra.startRiding(entity),"Two seats accepted a third rider");
        entity.positionRider(player);entity.positionRider(pig);
        helper.assertTrue(player.position().distanceToSqr(pig.position())>.4,"Double seat passengers overlap");
        helper.runAtTickTime(24,()->helper.assertTrue(frame.toggleFrame(null)==null,"Passenger blocked restoration"));
        helper.runAtTickTime(49,()->{
            helper.assertTrue(entity.isRemoved()&&!player.isPassenger()&&!pig.isPassenger(),"Block conversion left riders mounted");
            helper.assertTrue(helper.getLevel().noCollision(player,player.getBoundingBox())&&helper.getLevel().noCollision(pig,pig.getBoundingBox()),"Unseated riders were trapped in blocks");
            helper.succeed();
        });
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=40)
    public void entity_collision_preserves_empty_interior_and_floor(GameTestHelper helper) {
        var frame=frame(helper,Direction.NORTH);install(helper,frame,WagonEntity.defaultParts());
        helper.assertTrue(frame.toggleFrame(null)==null,"Assembly failed");var entity=wagon(helper,frame);Vec3 p=entity.position();
        AABB interior=new AABB(-.3,1.6,-.3,.3,2.1,.3).move(p);
        helper.assertTrue(helper.getLevel().noCollision(null,interior),"Entity broad bounds filled the empty cargo interior");
        AABB floor=new AABB(-.3,1.4,-.3,.3,1.48,.3).move(p);
        helper.assertTrue(!helper.getLevel().noCollision(null,floor),"Entity floor has no collision");
        helper.assertTrue(helper.getLevel().isUnobstructed(null,net.minecraft.world.phys.shapes.Shapes.create(interior)),"Placement treated empty interior as occupied");
        helper.assertTrue(!helper.getLevel().isUnobstructed(null,net.minecraft.world.phys.shapes.Shapes.create(floor)),"Placement ignored wagon floor");
        var pig=EntityType.PIG.create(helper.getLevel());pig.setPos(p.add(0,3,0));
        pig.move(MoverType.SELF,new Vec3(0,-3,0));
        helper.assertTrue(Math.abs(pig.getY()-p.y-1.5)<.01,"Moving entity did not land on wagon floor: "+(pig.getY()-p.y));
        var extra=EntityType.PIG.create(helper.getLevel());
        helper.assertTrue(pig.startRiding(entity)&&!extra.startRiding(entity),"Single-seat capacity is incorrect");
        helper.assertTrue(entity.collisionShape()==entity.collisionShape(),"Collision geometry rebuilt each query");
        helper.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=90)
    public void departed_wagon_fails_without_loss(GameTestHelper helper) {
        var frame=frame(helper,Direction.NORTH);install(helper,frame,WagonEntity.defaultParts());
        helper.assertTrue(frame.toggleFrame(null)==null,"Assembly failed");var entity=wagon(helper,frame);
        helper.runAtTickTime(24,()->{helper.assertTrue(frame.toggleFrame(null)==null,"Restore did not begin");entity.setPos(entity.position().add(3,0,0));});
        helper.runAtTickTime(74,()->{
            helper.assertTrue(!entity.isRemoved()&&frame.parts().isEmpty()&&!frame.extended()&&!frame.frameMoving(),"Departed wagon was lost or lift did not refold");
            helper.succeed();
        });
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=40)
    public void precise_picking_and_seat_interaction(GameTestHelper helper) {
        var frame=frame(helper,Direction.NORTH);install(helper,frame,WagonEntity.defaultParts());
        helper.assertTrue(frame.toggleFrame(null)==null,"Assembly failed");var entity=wagon(helper,frame);Vec3 p=entity.position();
        var player=helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 start=p.add(-3,.8,0),end=p.add(3,.8,0);
        var hit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player,start,end,new AABB(start,end).inflate(.1),e -> e.isPickable(),100);
        helper.assertTrue(hit==null,"Empty area under wagon prevented picking lift");
        start=p.add(0,1.8,0);end=p.add(0,2.1,.5);
        hit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player,start,end,new AABB(start,end).inflate(.1),e -> e.isPickable(),100);
        helper.assertTrue(hit==null,"Viewer inside broad bounds selected empty cargo space");
        aimSeat(entity,player,0);
        helper.assertTrue(entity.interact(player,net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction()&&player.getVehicle()==entity,"Right-click seat did not mount player");
        player.stopRiding();
        helper.assertTrue(!player.isPassenger()&&helper.getLevel().noCollision(player,player.getBoundingBox()),"Normal dismount trapped player");
        helper.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=60)
    public void long_shafts_remain_collidable_across_chunk_boundaries(GameTestHelper helper) {
        var entity=WagonContent.WAGON.get().create(helper.getLevel());
        BlockPos base=helper.absolutePos(new BlockPos(11,2,17));
        // Shaft tips are over two blocks past the west edge of the origin chunk.
        int x=(base.getX()&~15)+1;
        entity.configure(WagonEntity.defaultParts(),Direction.WEST);
        entity.setPos(x+.5,base.getY(),base.getZ()+.5);helper.getLevel().addFreshEntity(entity);
        Vec3 point=entity.position().add(WagonSlot.rotate(new Vec3(-.625,1.2,-5.25),entity.facing()));
        AABB probe=new AABB(point,point).inflate(.03);
        helper.assertTrue(!helper.getLevel().noCollision(null,probe),"Shaft vanished from collision across chunk boundary");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(WagonEntity.class,probe).contains(entity),"Typed entity query missed long shaft");
        var old=probe;entity.setPos(entity.position().add(16,0,0));
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(WagonEntity.class,old).contains(entity),"Index retained former location");
        probe=probe.move(16,0,0);
        helper.assertTrue(!helper.getLevel().noCollision(null,probe),"Index failed to follow changed position");
        entity.discard();
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(WagonEntity.class,probe).isEmpty(),"Removed wagon remained indexed");
        helper.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=90)
    public void pending_restoration_survives_controller_reload(GameTestHelper helper) {
        var frame=frame(helper,Direction.NORTH);install(helper,frame,WagonEntity.defaultParts());
        helper.assertTrue(frame.toggleFrame(null)==null,"Assembly failed");var entity=wagon(helper,frame);
        helper.runAtTickTime(24,()->{
            helper.assertTrue(frame.toggleFrame(null)==null,"Restore did not begin");
            var tag=frame.saveWithFullMetadata(helper.getLevel().registryAccess());
            frame.loadWithComponents(tag,helper.getLevel().registryAccess());
            helper.assertTrue(frame.restoring(entity.getUUID()),"Controller reload lost pending wagon");
        });
        helper.runAtTickTime(49,()->{
            helper.assertTrue(entity.isRemoved()&&AssemblyFrameBlockEntity.complete(frame.parts()),"Reload lost or duplicated pending wagon");
            helper.succeed();
        });
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=60)
    public void clicked_double_seat_remains_assigned_and_dismounts_above_chair(GameTestHelper helper) {
        for (Direction direction : new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
            var frame=frame(helper,direction);
            var modules=new EnumMap<WagonSlot,WagonPart>(WagonSlot.class);modules.putAll(WagonEntity.defaultParts());modules.put(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);
            install(helper,frame,modules);helper.assertTrue(frame.toggleFrame(null)==null,"Assembly failed");var entity=wagon(helper,frame);
            var right=helper.makeMockPlayer(GameType.SURVIVAL);right.setUUID(java.util.UUID.randomUUID());
            var left=helper.makeMockPlayer(GameType.SURVIVAL);left.setUUID(java.util.UUID.randomUUID());
            Vec3 rightHit=WagonSlot.rotate(new Vec3(.45,2.15,-1.875),direction);
            Vec3 leftHit=WagonSlot.rotate(new Vec3(-.45,2.15,-1.875),direction);
            aimSeat(entity,right,.45);
            helper.assertTrue(entity.interactAt(right,rightHit,net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction(),"Right seat did not accept first passenger");
            helper.assertTrue(entity.passengerSeat(right)==1,"First passenger was forced into left seat");
            aimSeat(entity,left,.45);
            helper.assertTrue(entity.interactAt(left,rightHit,net.minecraft.world.InteractionHand.MAIN_HAND)==net.minecraft.world.InteractionResult.FAIL&&!left.isPassenger(),"Occupied right seat redirected click into free left seat");
            aimSeat(entity,left,-.45);
            helper.assertTrue(entity.interactAt(left,leftHit,net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction()&&entity.passengerSeat(left)==0,"Left seat could not be selected independently");
            entity.positionRider(right);entity.positionRider(left);
            Vec3 rightPosition=entity.getPassengerRidingPosition(right),leftPosition=entity.getPassengerRidingPosition(left);
            var tag=new net.minecraft.nbt.CompoundTag();entity.saveWithoutId(tag);
            var copy=WagonContent.WAGON.get().create(helper.getLevel());copy.load(tag);
            helper.assertTrue(copy.passengerSeat(right)==1&&copy.passengerSeat(left)==0,"Saved seat assignment was lost");
            left.stopRiding();
            helper.assertTrue(Math.abs(left.getX()-leftPosition.x)<1e-6&&Math.abs(left.getZ()-leftPosition.z)<1e-6,"Left passenger dismounted away from their chair");
            helper.assertTrue(Math.abs(left.getY()-leftPosition.y-.001)<.001&&helper.getLevel().noCollision(left,left.getBoundingBox()),"Player did not stand on the cushion safely");
            helper.assertTrue(entity.getPassengerRidingPosition(right).equals(rightPosition),"Remaining right passenger shifted left");
            right.stopRiding();
            helper.assertTrue(Math.abs(right.getX()-rightPosition.x)<1e-6&&Math.abs(right.getZ()-rightPosition.z)<1e-6,"Last right passenger dismounted above left chair");
            helper.assertTrue(helper.getLevel().noCollision(right,right.getBoundingBox()),"Right passenger intersected chair after dismount");
            entity.discard();frame.dismantle(false,true);
        }
        helper.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=60)
    public void longer_double_shafts_update_block_and_entity_collision(GameTestHelper helper) {
        var frame=frame(helper,Direction.NORTH);
        var modules=new EnumMap<WagonSlot,WagonPart>(WagonSlot.class);modules.putAll(WagonEntity.defaultParts());modules.put(WagonSlot.SHAFTS,WagonPart.DOUBLE_HORSE_SHAFTS);
        install(helper,frame,modules);
        Vec3 origin=Vec3.atBottomCenterOf(frame.getBlockPos());
        AABB tip=new AABB(-.05,1.1,-6.1,.05,1.2,-6.0).move(origin);
        AABB bar=new AABB(.9,1.2,-5.70,1.0,1.25,-5.62).move(origin);
        AABB oldBar=new AABB(.9,1.2,-5.38,1.0,1.27,-5.3).move(origin);
        helper.assertTrue(!helper.getLevel().noCollision(null,tip)&&!helper.getLevel().noCollision(null,bar),"Extended pole or relocated yoke missing block collision");
        helper.assertTrue(helper.getLevel().noCollision(null,oldBar),"Old yoke location retained collision");
        var saved=frame.saveWithFullMetadata(helper.getLevel().registryAccess());
        frame.loadWithComponents(saved,helper.getLevel().registryAccess());frame.ensureFrame();
        helper.assertTrue(frame.parts().equals(modules)&&!helper.getLevel().noCollision(null,tip),"Current save lost installed modules or cell collision");
        helper.assertTrue(frame.toggleFrame(null)==null,"Updated wagon did not assemble");
        helper.assertTrue(!helper.getLevel().noCollision(null,tip)&&!helper.getLevel().noCollision(null,bar),"Entity collision did not match extended double shafts");
        helper.assertTrue(helper.getLevel().noCollision(null,oldBar),"Entity retained old yoke collision");
        helper.succeed();
    }
}
