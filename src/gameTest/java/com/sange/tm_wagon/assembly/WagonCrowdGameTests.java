package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.WagonPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class WagonCrowdGameTests {
    private record Fixture(WagonEntity wagon,Player driver,AbstractHorse horse) {
        void remove() { wagon.discard();horse.discard(); }
    }
    private static Fixture wagon(GameTestHelper h,Direction direction) {
        for(int x=1;x<24;x++)for(int z=1;z<24;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var w=WagonContent.WAGON.get().create(h.getLevel());w.configure(WagonEntity.defaultParts(),direction);
        w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(w);
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.startRiding(w);
        var horse=EntityType.HORSE.create(h.getLevel());horse.setNoAi(true);horse.setPos(w.horsePosition(0));
        h.getLevel().addFreshEntity(horse);horse.setLeashedTo(p,true);h.assertTrue(w.attachHorse(p,horse,0)==null,"Fixture horse failed to attach");
        return new Fixture(w,p,horse);
    }
    private static <T extends Mob> T mob(GameTestHelper h,WagonEntity w,EntityType<T> type,Vec3 local) {
        T e=type.create(h.getLevel());e.setNoAi(true);e.setPos(w.pose().point(local));e.setOnGround(true);h.getLevel().addFreshEntity(e);return e;
    }
    private static double travel(int ticks,int direction) {
        int ramp=direction>0?30:15,n=Math.min(ticks,ramp);
        double maximum=direction>0?WagonPhysics.FORWARD_SPEED:-WagonPhysics.REVERSE_SPEED;
        return maximum*(n*(n+1.0)/(2*ramp)+Math.max(0,ticks-ramp));
    }
    private static void drive(Fixture f,int direction,int steering,int ticks) {
        for(int i=0;i<ticks;i++) { f.wagon.acceptInput(f.driver,direction,steering);f.wagon.tick(); }
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void road_mobs_yield_without_slowing_forward_in_all_directions(GameTestHelper h) { straight(h,1); }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void road_mobs_yield_without_slowing_reverse_in_all_directions(GameTestHelper h) { straight(h,-1); }
    private static void straight(GameTestHelper h,int direction) {
        for(Direction facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
            var f=wagon(h,facing);var w=f.wagon;
            var left=mob(h,w,EntityType.COW,new Vec3(-.2,0,direction>0?-5.8:2.9));
            var right=mob(h,w,EntityType.ZOMBIE,new Vec3(.2,0,direction>0?-5.8:2.9));
            Vec3 start=w.position(),heading=w.pose().forward();int ticks=direction>0?22:19;
            drive(f,direction,0,ticks);
            double expected=travel(ticks,direction);
            h.assertTrue(Math.abs(w.position().subtract(start).dot(heading)-expected)<.01,"Road mob slowed wagon: "+facing+" "+direction+" "+w.position());
            h.assertTrue(w.pose().local(left.position()).x<-.5&&w.pose().local(right.position()).x>.5,"Mobs were not pushed to their respective sides: "+facing);
            h.assertTrue(left.getHealth()==left.getMaxHealth()&&right.getHealth()==right.getMaxHealth(),"Clearing road damaged mobs");
            h.assertTrue(h.getLevel().noBlockCollision(left,left.getBoundingBox().deflate(.001))&&h.getLevel().noBlockCollision(right,right.getBoundingBox().deflate(.001)),"Road clearing pushed mobs into blocks");
            drive(f,0,0,30);Vec3 stopped=left.position();drive(f,0,0,3);h.assertTrue(left.position().equals(stopped),"Parked wagon kept pushing road mob");
            left.discard();right.discard();f.remove();
        }h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void road_mob_uses_open_side_when_nearest_side_is_a_wall(GameTestHelper h) {
        var f=wagon(h,Direction.NORTH);var w=f.wagon;
        for(int z=7;z<22;z++)for(int y=2;y<5;y++)h.setBlock(new BlockPos(13,y,z),Blocks.STONE);
        var cow=mob(h,w,EntityType.COW,new Vec3(1,0,-1.25));Vec3 start=w.position();
        drive(f,1,0,22);
        h.assertTrue(Math.abs(start.z-w.getZ()-travel(22,1))<.01,"Roadside wall or cow slowed clear lane");
        h.assertTrue(w.pose().local(cow.position()).x<.2,"Cow did not use open side");
        h.assertTrue(h.getLevel().noBlockCollision(cow,cow.getBoundingBox().deflate(.001)),"Cow entered roadside wall");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void trapped_mob_does_not_stop_cart_or_enter_corridor_walls(GameTestHelper h) {
        var f=wagon(h,Direction.NORTH);var w=f.wagon;
        for(int x:new int[]{9,13})for(int z=7;z<22;z++)for(int y=2;y<5;y++)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        var cow=mob(h,w,EntityType.COW,new Vec3(.2,0,-2.7));Vec3 start=w.position();
        drive(f,1,0,26);
        h.assertTrue(Math.abs(start.z-w.getZ()-travel(26,1))<.01,"Trapped biological entity slowed wagon");
        h.assertTrue(h.getLevel().noBlockCollision(cow,cow.getBoundingBox().deflate(.001))&&Math.abs(w.pose().local(cow.position()).x)<1.1,"Trapped cow passed through corridor wall");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void standing_mob_and_pull_horse_are_not_cleared_as_road_obstacles(GameTestHelper h) {
        var f=wagon(h,Direction.NORTH);var w=f.wagon;
        var cargo=mob(h,w,EntityType.SHEEP,new Vec3(0,1.51,.7));cargo.move(MoverType.SELF,new Vec3(0,-.05,0));
        var road=mob(h,w,EntityType.COW,new Vec3(.2,0,-5.8));Vec3 cargoStart=cargo.position();
        drive(f,1,1,5);
        h.assertTrue(cargo.position().equals(cargoStart),"Road clearing shoved or transported cargo occupant");
        // Once the wagon passes beneath it, the unseated mob may leave the deck.
        drive(f,1,1,7);
        h.assertTrue(w.hasHorse(f.horse.getUUID())&&f.horse.position().distanceTo(w.horsePosition(0))<.05,"Road clearing displaced pulling horse");
        h.assertTrue(Math.abs(w.pose().local(road.position()).x)>.5&&road.position().distanceTo(w.horsePosition(0))>.8,"Road mob remained directly in horse footprint: "+w.pose().local(road.position())+" wagon="+w.position()+" yaw="+w.getYRot()+" road support="+com.sange.tm_wagon.entity.WagonSupport.supportedByWagon(road));h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void mobs_outside_actual_parts_are_not_pushed(GameTestHelper h) {
        var f=wagon(h,Direction.NORTH);var w=f.wagon;
        // Small rabbit is in the empty gap between the single-horse shafts, outside the horse/body.
        var rabbit=mob(h,w,EntityType.RABBIT,new Vec3(0,0,-2.95));
        var side=mob(h,w,EntityType.COW,new Vec3(3,0,-2));Vec3 a=rabbit.position(),b=side.position();
        drive(f,1,0,1);
        h.assertTrue(rabbit.position().equals(a)&&side.position().equals(b),"Overall wagon bounds caused a false shove");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void both_double_horse_lanes_clear_small_road_mobs(GameTestHelper h) {
        var f=wagon(h,Direction.NORTH);var w=f.wagon;
        var parts=new java.util.EnumMap<WagonSlot,WagonPart>(WagonSlot.class);parts.putAll(WagonEntity.defaultParts());
        parts.put(WagonSlot.SHAFTS,WagonPart.DOUBLE_HORSE_SHAFTS);w.configure(parts,Direction.NORTH);
        f.horse.setPos(w.horsePosition(0));var donkey=EntityType.DONKEY.create(h.getLevel());donkey.setNoAi(true);
        donkey.setPos(w.horsePosition(1));h.getLevel().addFreshEntity(donkey);donkey.setLeashedTo(f.driver,true);
        h.assertTrue(w.attachHorse(f.driver,donkey,1)==null,"Double horse fixture failed");
        var left=mob(h,w,EntityType.PIG,new Vec3(-1.05,0,-5.8));var right=mob(h,w,EntityType.WOLF,new Vec3(1.05,0,-5.8));
        Vec3 start=w.position();drive(f,1,0,24);
        h.assertTrue(Math.abs(start.z-w.getZ()-travel(24,1))<.01,"Double horse road crowd slowed wagon");
        h.assertTrue(w.pose().local(left.position()).x<-1.65&&w.pose().local(right.position()).x>1.65,"Double horse lanes did not clear small mobs");
        h.assertTrue(w.hasHorse(f.horse.getUUID())&&w.hasHorse(donkey.getUUID()),"Crowd clearing detached a pulling animal");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void mounted_mob_moves_with_its_root_without_blocking_vehicle(GameTestHelper h) {
        var f=wagon(h,Direction.NORTH);var w=f.wagon;var spider=mob(h,w,EntityType.SPIDER,new Vec3(.2,0,-5.8));
        var zombie=EntityType.ZOMBIE.create(h.getLevel());zombie.setNoAi(true);h.getLevel().addFreshEntity(zombie);zombie.startRiding(spider,true);spider.positionRider(zombie);
        Vec3 start=w.position();drive(f,1,0,22);
        h.assertTrue(Math.abs(start.z-w.getZ()-travel(22,1))<.01,"Mounted mob blocked vehicle");
        h.assertTrue(w.pose().local(spider.position()).x>.5&&zombie.getVehicle()==spider
            &&zombie.position().distanceTo(spider.getPassengerRidingPosition(zombie).subtract(zombie.getVehicleAttachmentPoint(spider)))<.05,"Crowd clearing split mounted mobs");h.succeed();
    }
}
