package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.compat.StructureCollision;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.WagonPhysics;
import com.sange.tm_wagon.physics.WagonPose;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Loaded only by the optional integration run; uses real Sable structures, not stubs. */
final class SableCompatFixtures {
    private static final class CountingWagon extends WagonEntity {
        int builds;
        CountingWagon(net.minecraft.server.level.ServerLevel level) { super(WagonContent.WAGON.get(),level); }
        @Override public List<com.sange.tm_wagon.physics.OrientedBox> motionCollidersAt(WagonPose pose) {
            builds++;return super.motionCollidersAt(pose);
        }
    }
    private static List<net.minecraft.world.phys.AABB> bounds(List<StructureCollision.Surface> surfaces) {
        return surfaces.stream().map(s->s.box().bounds()).toList();
    }
    static void cachedSurfaces(GameTestHelper h) {
        var blocks=new ArrayList<BlockPos>();
        for(int x=5;x<21;x++)for(int z=5;z<21;z++)blocks.add(h.absolutePos(new BlockPos(x,8,z)));
        var platform=assemble(h,blocks);
        var wagon=new CountingWagon(h.getLevel());
        wagon.configure(WagonEntity.defaultParts(),Direction.NORTH);
        wagon.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(12,9,15))));
        try {
            wagon.builds=0;StructureCollision.push(wagon);
            h.assertTrue(wagon.builds==0,"Stationary structures should not rebuild wagon motion colliders");
            platform.updateLastPose();platform.logicalPose().orientation().rotateY(.25).rotateZ(.04);
            platform.logicalPose().position().add(.35,.07,-.25);
            platform.updateBoundingBox();platform.forceUpdateGlobalBounds();
            var outer=wagon.getBoundingBox().inflate(4).expandTowards(0,-2,0);
            var surfaces=StructureCollision.surfaces(h.getLevel(),outer);
            h.assertTrue(surfaces.size()>20,"Missing many-surface structure fixture");
            var queries=new ArrayList<net.minecraft.world.phys.AABB>();
            for(var surface:surfaces) {
                queries.add(surface.box().bounds().move(surface.motion().scale(-1)).inflate(.01));
                queries.add(surface.box().bounds().inflate(.01));
            }
            // Exercise both cached narrow probes and queries outside the scope bounds.
            queries.add(outer.inflate(2));
            var expected=queries.stream().map(q->bounds(StructureCollision.surfaces(h.getLevel(),q))).toList();
            wagon.builds=0;
            try(var scope=StructureCollision.begin(wagon)) {
                StructureCollision.push(wagon);
                h.assertTrue(wagon.builds==1,"Moving surfaces rebuilt the whole wagon more than once");
                for(int i=0;i<queries.size();i++)
                    h.assertTrue(bounds(StructureCollision.surfaces(h.getLevel(),queries.get(i))).equals(expected.get(i)),"Cached sweep query omitted or reordered a surface: "+i);
                try(var nested=StructureCollision.begin(wagon)) { StructureCollision.push(wagon); }
                h.assertTrue(bounds(StructureCollision.surfaces(h.getLevel(),queries.getFirst())).equals(expected.getFirst()),"Nested scope lost the enclosing snapshot");
            }
            var before=bounds(StructureCollision.surfaces(h.getLevel(),outer));
            platform.updateLastPose();platform.logicalPose().position().add(0,.4,0);
            platform.updateBoundingBox();platform.forceUpdateGlobalBounds();
            var after=bounds(StructureCollision.surfaces(h.getLevel(),outer));
            h.assertTrue(!before.equals(after),"Structure movement did not change the fixture");
            try(var scope=StructureCollision.begin(wagon)) {
                h.assertTrue(bounds(StructureCollision.surfaces(h.getLevel(),outer)).equals(after),"Previous scope's geometry leaked into the next tick");
            }
        } finally {
            wagon.discard();SubLevelContainer.getContainer(h.getLevel()).removeSubLevel(platform,SubLevelRemovalReason.REMOVED);
        }
    }
    private static ServerSubLevel assemble(GameTestHelper h,List<BlockPos> blocks) {
        for(var p:blocks)h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),3);
        var sub=SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),blocks.getFirst(),blocks,BoundingBox3i.from(blocks));
        sub.updateBoundingBox();sub.forceUpdateGlobalBounds();sub.updateLastPose();return sub;
    }
    static void riderClearance(GameTestHelper h) {
        var w=com.sange.tm_wagon.cargo.SeatClearanceGameTests.wagon(h);
        var rider=com.sange.tm_wagon.cargo.SeatClearanceGameTests.rider(h,w,false);
        var head=rider.getBoundingBox();
        BlockPos obstacle=BlockPos.containing(head.getCenter().add(2,head.getYsize()/2-.1,0));
        var beam=assemble(h,List.of(obstacle));
        try {
            Vec3 start=w.position();
            com.sange.tm_wagon.cargo.SeatClearanceGameTests.move(w,new Vec3(3,0,0),w.getYRot(),0);w.positionRider(rider);
            h.assertTrue(w.getX()-start.x<2.6,"Passenger passed through Sable low beam");
            h.assertTrue(StructureCollision.clear(h.getLevel(),rider.getBoundingBox().deflate(.0001)),"Passenger clipped Sable beam");
            rider.stopRiding();rider.discard();w.setPos(start);
            // Move the same physical beam directly over the stool: boarding must fail.
            beam.updateLastPose();beam.logicalPose().position().add(-2,0,0);beam.updateBoundingBox();beam.forceUpdateGlobalBounds();
            var next=net.minecraft.world.entity.EntityType.ENDERMAN.create(h.getLevel());next.setPos(w.position());
            h.assertTrue(!w.cargo().seats.available(4,next)&&!w.boardCargoSeat(next,4),"Sable ceiling allowed boarding");
            beam.logicalPose().position().add(2,0,0);beam.updateBoundingBox();beam.forceUpdateGlobalBounds();
            com.sange.tm_wagon.cargo.SeatClearanceGameTests.move(w,new Vec3(3,0,0),w.getYRot(),0);
            h.assertTrue(w.getX()-start.x>2.99,"Empty wagon cannot pass Sable beam");
        } finally {
            w.discard();SubLevelContainer.getContainer(h.getLevel()).removeSubLevel(beam,SubLevelRemovalReason.REMOVED);
        }
    }
    static void run(GameTestHelper h,int scenario) {
        // Other assembly fixtures can leave blocks above the template's one-block height.
        for(int x=1;x<25;x++)for(int y=2;y<20;y++)for(int z=1;z<25;z++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        var blocks=new ArrayList<BlockPos>();
        for(int x=3;x<=22;x++)for(int z=3;z<=22;z++)blocks.add(h.absolutePos(new BlockPos(x,8,z)));
        var platform=assemble(h,blocks);ServerSubLevel wall=null;WagonEntity wagon=null;
        try {
            Vec3 origin=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(12,9,15)));
            wagon=WagonContent.WAGON.get().create(h.getLevel());wagon.configure(WagonEntity.defaultParts(),Direction.NORTH);
            wagon.setPos(origin);h.getLevel().addFreshEntity(wagon);var physics=new WagonPhysics();
            h.assertTrue(!StructureCollision.surfaces(h.getLevel(),wagon.getBoundingBox().inflate(2)).isEmpty(),"Actual Sable plot produced no colliders: "+platform.logicalPose()+" plot="+platform.getPlot().getBoundingBox()+" bounds="+platform.boundingBox());
            if(scenario==0) {
                var old=new dev.ryanhcode.sable.companion.math.Pose3d(platform.logicalPose());
                platform.logicalPose().orientation().rotateY(Math.PI/4).rotateZ(.08);platform.updateBoundingBox();platform.forceUpdateGlobalBounds();
                Vec3 foot=platform.logicalPose().transformPosition(old.transformPositionInverse(origin));
                var ground=WagonPhysics.ground(h.getLevel(),foot,1.1,1.1,.1);
                h.assertTrue(ground.present()&&Math.abs(ground.height()-foot.y)<.1,"Rotated structure support used broad AABB top: "+ground+" expected="+foot.y);
            } else if(scenario==4) {
                physics.tick(wagon,0,0,false,0);
                var old=new dev.ryanhcode.sable.companion.math.Pose3d(platform.logicalPose());
                Vec3 before=wagon.position();float yaw=wagon.getYRot();
                platform.updateLastPose();platform.logicalPose().orientation().rotateY(.10);
                platform.updateBoundingBox();platform.forceUpdateGlobalBounds();
                Vec3 expected=platform.logicalPose().transformPosition(old.transformPositionInverse(before));
                physics.tick(wagon,0,0,false,0);
                h.assertTrue(wagon.position().distanceTo(expected)<.08,"Rotating platform did not carry wagon centre: "+wagon.position()+" expected="+expected);
                h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(wagon.getYRot()-yaw)+(float)Math.toDegrees(.10))<.1,"Rotating platform did not carry heading: "+wagon.getYRot());
            } else if(scenario==2) {
                physics.tick(wagon,0,0,false,0);Vec3 before=wagon.position();
                platform.updateLastPose();platform.logicalPose().position().add(.2,.1,.15);platform.updateBoundingBox();platform.forceUpdateGlobalBounds();
                physics.tick(wagon,0,0,false,0);
                Vec3 travelled=wagon.position().subtract(before);
                h.assertTrue(travelled.distanceTo(new Vec3(.2,.1,.15))<.08,"Wagon did not follow moving platform: "+travelled);
                platform.updateLastPose();Vec3 settled=wagon.position();physics.tick(wagon,0,0,false,0);
                h.assertTrue(wagon.position().subtract(settled).horizontalDistance()<.02,"Platform movement was applied twice");
            } else {
                blocks.clear();
                for(int x=7;x<=17;x++)for(int y=9;y<=15;y++)blocks.add(h.absolutePos(new BlockPos(x,y,6)));
                wall=assemble(h,blocks);
                if(scenario==1) {
                    for(int i=0;i<65;i++)physics.tick(wagon,1,0,true,0);
                    for(var part:wagon.motionCollidersAt(wagon.pose()))h.assertTrue(StructureCollision.clear(h.getLevel(),part),"Wagon penetrated Sable wall");
                    h.assertTrue(wagon.position().z>h.absolutePos(new BlockPos(0,0,6)).getZ()+1,"Wagon drove through Sable wall");
                    h.assertTrue(origin.distanceTo(wagon.position())>.2,"Fixture never moved");
                } else {
                    wall.updateLastPose();wall.logicalPose().position().add(0,0,6);wall.updateBoundingBox();wall.forceUpdateGlobalBounds();
                    Vec3 push=StructureCollision.push(wagon);
                    h.assertTrue(push.z>0,"Moving structure did not push wagon: "+push);
                    Vec3 before=wagon.position();physics.tick(wagon,0,0,false,0);
                    h.assertTrue(wagon.position().z>before.z,"Wagon ignored moving structure");
                    for(var part:wagon.motionCollidersAt(wagon.pose()))h.assertTrue(StructureCollision.clear(h.getLevel(),part),"Moving structure left wagon embedded: part="+part.bounds()+" position="+wagon.position()+" push="+push);
                }
            }
        } finally {
            if(wagon!=null)wagon.discard();
            var container=SubLevelContainer.getContainer(h.getLevel());
            if(wall!=null)container.removeSubLevel(wall,SubLevelRemovalReason.REMOVED);
            container.removeSubLevel(platform,SubLevelRemovalReason.REMOVED);
        }
    }
}
