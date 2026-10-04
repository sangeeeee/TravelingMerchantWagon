package com.sange.tm_wagon.physics;

import com.sange.tm_wagon.assembly.WagonContent;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public final class WagonTerrainGameTests {
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void movement_snapshot_matches_live_partial_shapes_and_query_growth(GameTestHelper h) {
        var w=WagonContent.WAGON.get().create(h.getLevel());w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(10,2,10))));
        h.setBlock(new BlockPos(4,4,4),Blocks.STONE);h.setBlock(new BlockPos(5,4,4),Blocks.OAK_FENCE);
        h.setBlock(new BlockPos(6,4,4),Blocks.STONE_STAIRS);
        var query=new WagonTerrain(w,true);AABB base=new AABB(h.absolutePos(new BlockPos(4,4,4))).inflate(.2);
        for(double shift:new double[]{0,.1,.2,1.9,-.1}) {
            var area=base.move(shift,0,0);var expected=new HashSet<AABB>();
            for(var shape:w.level().getBlockCollisions(w,area))for(var box:shape.toAabbs())if(box.intersects(area))expected.add(box);
            var actual=new HashSet<AABB>();for(var box:query.blocks(area))actual.add(box.bounds());
            h.assertTrue(actual.equals(expected),"Cached partial collision geometry differs from vanilla at "+shift);
        }
        h.setBlock(new BlockPos(4,4,4),Blocks.AIR);
        h.assertTrue(new WagonTerrain(w,true).blocks(new AABB(h.absolutePos(new BlockPos(4,4,4))).deflate(.01)).isEmpty(),"Snapshot persisted into another movement");
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void contextual_collision_blocks_keep_live_queries(GameTestHelper h) {
        var w=WagonContent.WAGON.get().create(h.getLevel());w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(10,2,10))));
        var pos=new BlockPos(4,4,4);h.setBlock(pos,Blocks.POWDER_SNOW);
        var query=new WagonTerrain(w,true);var area=new AABB(h.absolutePos(pos)).deflate(.01);query.blocks(area);
        h.setBlock(pos,Blocks.STONE);
        h.assertTrue(query.blocks(area).size()==1,"Dynamic collision context incorrectly cached");
        h.setBlock(pos,Blocks.AIR);
        h.assertTrue(query.blocks(area).isEmpty(),"Dynamic collision path stopped being live");h.succeed();
    }
}
