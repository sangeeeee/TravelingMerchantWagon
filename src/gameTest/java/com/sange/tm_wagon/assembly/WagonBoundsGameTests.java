package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class WagonBoundsGameTests {
    private static void clearElevatedSpace(GameTestHelper h) {
        // The shared template is only one block high; earlier fixtures can leave
        // blocks above it when GameTest reuses a batch position.
        for(int x=1;x<24;x++)for(int y=2;y<20;y++)for(int z=1;z<24;z++)
            h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
    }
    @GameTest(template="assembly_test",timeoutTicks=60)
    public static void empty_outer_bounds_allow_entry_and_exit_on_all_sides(GameTestHelper h) {
        clearElevatedSpace(h);
        var w=WagonContent.WAGON.get().create(h.getLevel());w.configure(com.sange.tm_wagon.entity.WagonEntity.defaultParts(),Direction.NORTH);
        w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(12,10,15))));h.getLevel().addFreshEntity(w);
        var p=h.makeMockPlayer(GameType.SURVIVAL);int checked=0;
        for(float yaw:new float[]{180,165,135,105}) {
            w.applyPose(new WagonPose(w.position(),yaw,0,0));var b=w.getBoundingBox();
            for(int side=0;side<4;side++)for(int i=1;i<20;i++) {
                double t=i/20.0;
                Vec3 start=switch(side) {
                    case 0->new Vec3(b.minX-.35,w.getY()+.01,b.minZ+t*b.getZsize());
                    case 1->new Vec3(b.maxX+.35,w.getY()+.01,b.minZ+t*b.getZsize());
                    case 2->new Vec3(b.minX+t*b.getXsize(),w.getY()+.01,b.minZ-.35);
                    default->new Vec3(b.minX+t*b.getXsize(),w.getY()+.01,b.maxZ+.35);
                };
                Vec3 inward=switch(side) {case 0->new Vec3(.55,0,0);case 1->new Vec3(-.55,0,0);case 2->new Vec3(0,0,.55);default->new Vec3(0,0,-.55);};
                p.setPos(start);p.setOnGround(false);var path=p.getBoundingBox().expandTowards(inward);
                if(w.colliders().stream().anyMatch(box->box.intersects(path)))continue;
                p.move(MoverType.SELF,inward);
                h.assertTrue(p.position().distanceTo(start.add(inward))<1e-6,"Empty outer AABB blocked entry: yaw="+yaw+" side="+side+" actual="+p.position().subtract(start));
                p.move(MoverType.SELF,inward.scale(-1));
                h.assertTrue(p.position().distanceTo(start)<1e-6,"Empty outer AABB blocked exit");checked++;
            }
        }
        h.assertTrue(checked>20,"Not enough empty boundary crossings exercised: "+checked);h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=60)
    public static void dropped_items_still_land_on_real_wagon_floor(GameTestHelper h) {
        clearElevatedSpace(h);
        var w=WagonContent.WAGON.get().create(h.getLevel());w.configure(com.sange.tm_wagon.entity.WagonEntity.defaultParts(),Direction.NORTH);
        w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(12,10,15))));h.getLevel().addFreshEntity(w);
        var item=EntityType.ITEM.create(h.getLevel());item.setPos(w.pose().point(new Vec3(0,2.2,0)));
        item.move(MoverType.SELF,new Vec3(0,-1,0));
        h.assertTrue(Math.abs(item.getY()-(w.getY()+1.5))<.01,"Item no longer lands on actual cargo floor: "+item.getY());h.succeed();
    }
}
