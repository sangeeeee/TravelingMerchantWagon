package com.sange.tm_wagon.physics;

import com.sange.tm_wagon.entity.WagonEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** A terrain snapshot local to ONE synchronous movement, never retained across ticks.
 * Moving entities/structures are queried separately. Context-sensitive blocks and road
 * mobs (whose movement may activate/change blocks) keep the live vanilla query path. */
final class WagonTerrain {
    private final WagonEntity wagon;
    private AABB region;
    private List<OrientedBox> snapshot;
    private boolean live;
    WagonTerrain(WagonEntity wagon,boolean cache) { this.wagon=wagon;live=!cache||wagon.crowd().hasCandidates(); }

    List<OrientedBox> blocks(AABB area) {
        if(live)return query(area);
        if(region==null||!contains(region,area)) {
            AABB next=area.inflate(.35);
            if(!cacheable(next)) { live=true;region=null;snapshot=null;return query(area); }
            snapshot=query(next);region=next;
        }
        var found=new ArrayList<OrientedBox>();
        for(var box:snapshot)if(box.bounds().intersects(area))found.add(box);
        return found;
    }
    private List<OrientedBox> query(AABB area) {
        var found=new ArrayList<OrientedBox>();
        for(var shape:wagon.level().getBlockCollisions(wagon,area))for(var box:shape.toAabbs())
            if(box.intersects(area))found.add(OrientedBox.of(box));
        return found;
    }
    private boolean cacheable(AABB area) {
        // Include vanilla's one-cell halo for shapes extending outside their own cell.
        var pos=new BlockPos.MutableBlockPos();
        for(int x=Mth.floor(area.minX)-1;x<=Mth.floor(area.maxX)+1;x++)
            for(int z=Mth.floor(area.minZ)-1;z<=Mth.floor(area.maxZ)+1;z++) {
                var chunk=wagon.level().getChunkForCollisions(x>>4,z>>4);if(chunk==null)return false;
                for(int y=Mth.floor(area.minY)-1;y<=Mth.floor(area.maxY)+1;y++)
                    if(chunk.getBlockState(pos.set(x,y,z)).getBlock().hasDynamicShape())return false;
            }
        return true;
    }
    private static boolean contains(AABB outer,AABB inner) {
        return outer.minX<=inner.minX&&outer.minY<=inner.minY&&outer.minZ<=inner.minZ
            &&outer.maxX>=inner.maxX&&outer.maxY>=inner.maxY&&outer.maxZ>=inner.maxZ;
    }
}
