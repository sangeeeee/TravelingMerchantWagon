package com.sange.tm_wagon.performance;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Actual 32x32 Sable floor; animation imposed only on this disposable test structure. */
final class SablePerformanceFixture {
    private final ServerLevel level;
    private final ServerSubLevel sub;
    private final org.joml.Vector3d position;
    private final dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem physics;
    private final boolean wasPaused;
    private int ticks;
    SablePerformanceFixture(ServerLevel level,BlockPos origin,boolean rotated) {
        this.level=level;
        // This fixture supplies a prescribed floor pose. Do not let Sable's rigid-body
        // solver independently move/drop it between samples and invalidate comparisons.
        physics=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(level);
        wasPaused=physics.getPaused();physics.setPaused(true);
        var blocks=new ArrayList<BlockPos>();
        for(int x=-16;x<16;x++)for(int z=-24;z<8;z++) {
            var p=origin.offset(x,-1,z);level.setBlock(p,Blocks.STONE.defaultBlockState(),3);blocks.add(p);
        }
        sub=SubLevelAssemblyHelper.assembleBlocks(level,blocks.getFirst(),blocks,BoundingBox3i.from(blocks));
        if(rotated)sub.logicalPose().orientation().rotateY(Math.toRadians(15));
        sub.updateBoundingBox();sub.forceUpdateGlobalBounds();sub.updateLastPose();position=new org.joml.Vector3d(sub.logicalPose().position());
    }
    void step() {
        sub.updateLastPose();sub.logicalPose().position().set(position).add(Math.sin(++ticks*.06)*.4,0,0);
        sub.updateBoundingBox();sub.forceUpdateGlobalBounds();
    }
    void close() {
        try { SubLevelContainer.getContainer(level).removeSubLevel(sub,SubLevelRemovalReason.REMOVED); }
        finally { physics.setPaused(wasPaused); }
    }
}
