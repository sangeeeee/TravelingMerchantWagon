package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.entity.WagonPlatform;
import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class WagonPlatformGameTests {
    private static WagonEntity wagon(GameTestHelper h) {
        for(int x=1;x<24;x++)for(int z=1;z<24;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var w=WagonContent.WAGON.get().create(h.getLevel());w.configure(WagonEntity.defaultParts(),Direction.NORTH);
        w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(w);return w;
    }
    private static Player driver(GameTestHelper h,WagonEntity w) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);h.assertTrue(p.startRiding(w),"Driver failed to board");
        var horse=EntityType.HORSE.create(h.getLevel());horse.setNoAi(true);horse.setPos(w.horsePosition(0));
        h.getLevel().addFreshEntity(horse);horse.setLeashedTo(p,true);h.assertTrue(w.attachHorse(p,horse,0)==null,"Horse failed to bind");return p;
    }
    private static <T extends Entity> T standing(GameTestHelper h,WagonEntity w,EntityType<T> type,Vec3 local) {
        T e=type.create(h.getLevel());if(e instanceof Mob mob)mob.setNoAi(true);
        e.setPos(w.pose().point(local).add(0,.01,0));h.getLevel().addFreshEntity(e);
        e.move(MoverType.SELF,new Vec3(0,-.05,0));e.setDeltaMovement(Vec3.ZERO);
        h.assertTrue(e.onGround()&&w.platform().supports(e),"Fixture is not standing on wagon: "+e.position());return e;
    }
    private static void drive(WagonEntity w,Player p,int forward,int steering,int ticks) {
        for(int i=0;i<ticks;i++) { w.acceptInput(p,forward,steering);w.tick(); }
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void cargo_mob_and_item_follow_forward_reverse_and_turn_without_riding(GameTestHelper h) {
        var w=wagon(h);var p=driver(h,w);
        var sheep=standing(h,w,EntityType.SHEEP,new Vec3(.3,1.5,.3));
        var item=new ItemEntity(h.getLevel(),w.getX()-.35,w.getY()+1.51,w.getZ()+1.1,new ItemStack(Items.DIAMOND));
        item.setDeltaMovement(Vec3.ZERO);h.getLevel().addFreshEntity(item);item.move(MoverType.SELF,new Vec3(0,-.05,0));
        Vec3 sheepLocal=w.pose().local(sheep.position()),itemLocal=w.pose().local(item.position());
        for(int i=0;i<24;i++) {
            // Exercise both entity tick orders, including their ordinary gravity/friction.
            if(i%2==0) { sheep.tick();item.tick(); }
            drive(w,p,i<12?1:-1,i%3==0?1:0,1);
            if(i%2!=0) { sheep.tick();item.tick(); }
            h.assertTrue(w.pose().local(sheep.position()).distanceTo(sheepLocal)<.015,"Standing mob drifted on moving cargo floor");
            h.assertTrue(w.pose().local(item.position()).distanceTo(itemLocal)<.015,"Dropped item drifted on moving cargo floor");
            h.assertTrue(!sheep.isPassenger()&&!item.isPassenger(),"Standing entities were converted into passengers");
        }
        h.assertTrue(Math.abs(w.getYRot()-180)>.1,"Fixture never turned");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void standing_server_player_follows_and_platform_counts_as_ground(GameTestHelper h) {
        var w=wagon(h);var p=driver(h,w);var player=h.makeMockServerPlayerInLevel();
        player.setPos(w.position().add(0,1.51,1));player.move(MoverType.SELF,new Vec3(0,-.05,0));player.setDeltaMovement(Vec3.ZERO);
        Vec3 local=w.pose().local(player.position());drive(w,p,1,1,12);
        h.assertTrue(!player.isPassenger()&&w.pose().local(player.position()).distanceTo(local)<.01,"Standing player was not transported");
        // The vanilla flight check scans blocks only: there is air below the cargo floor.
        try {
            var check=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredMethod("noBlocksAround",Entity.class);
            check.setAccessible(true);h.assertTrue(!(boolean)check.invoke(player.connection,player),"Platform standing was classified as floating");
            player.setPos(player.position().add(4,0,0));
            h.assertTrue((boolean)check.invoke(player.connection,player),"Nearby wagon disabled flight checks away from its surface");
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void relative_player_updates_preserve_walking_on_a_moving_slope(GameTestHelper h) {
        var w=wagon(h);var p=driver(h,w);var player=h.makeMockServerPlayerInLevel();
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        try {
            var id=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");id.setAccessible(true);
            player.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(id.getInt(player.connection)));
            var pending=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");pending.setAccessible(true);
            var floating=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("clientIsFloating");floating.setAccessible(true);
            player.setPos(w.position().add(0,1.51,.7));player.move(MoverType.SELF,new Vec3(0,-.05,0));player.setDeltaMovement(Vec3.ZERO);
            player.connection.resetPosition();
            for(int x=1;x<24;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
            for(int i=0;i<36;i++) {
                player.connection.tick();
                // The client reports its own previous platform pose, not the server's newer world position.
                Vec3 clientLocal=w.pose().local(player.position()).add(.005,0,0);
                drive(w,p,1,i<6?1:0,1);
                if(i%6==0)drive(w,p,1,0,1); // Simulate fewer player packets than server movement ticks.
                Vec3 expected=w.pose().point(clientLocal);
                w.platform().acceptStandingMovement(player,clientLocal,15,0,true);
                h.assertTrue(Math.abs(player.getX()-expected.x)<.001&&Math.abs(player.getZ()-expected.z)<.001,"Relative movement lost walking or platform position at tick "+i+" "+w.pose().local(player.position()));
                h.assertTrue(w.platform().supports(player)&&h.getLevel().noCollision(player,player.getBoundingBox().deflate(.001)),"Relative movement embedded player on slope");
                h.assertTrue(pending.get(player.connection)==null&&!floating.getBoolean(player.connection),"Platform packet triggered correction or flight detection");
            }
            Vec3 before=player.position();
            for(Vec3 invalid:new Vec3[]{new Vec3(Double.NaN,1.5,0),new Vec3(1000,1.5,0),new Vec3(0,10,0)})
                w.platform().acceptStandingMovement(player,invalid,0,0,true);
            w.platform().acceptStandingMovement(player,new Vec3(0,1.5,0),Float.NaN,0,true);
            w.platform().acceptStandingMovement(player,new Vec3(0,1.5,0),0,0,false);
            h.assertTrue(player.position().equals(before),"Invalid or airborne relative packet changed position");
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void walking_jumping_and_leaving_are_not_locked_to_platform(GameTestHelper h) {
        var w=wagon(h);var p=driver(h,w);var e=standing(h,w,EntityType.SHEEP,new Vec3(0,1.5,.7));
        Vec3 local=w.pose().local(e.position());e.move(MoverType.SELF,new Vec3(.2,0,0));e.setOnGround(true);
        e.setDeltaMovement(new Vec3(.03,0,0));drive(w,p,1,1,3);
        h.assertTrue(Math.abs(w.pose().local(e.position()).x-local.x-.2)<.01,"Platform cancelled walking");
        h.assertTrue(e.getDeltaMovement().equals(new Vec3(.03,0,0)),"Platform changed independent walking velocity");
        Vec3 before=e.position();e.setDeltaMovement(new Vec3(0,.42,0));drive(w,p,1,0,3);
        h.assertTrue(e.position().equals(before),"Jumping entity was dragged by wagon");
        e.setDeltaMovement(Vec3.ZERO);e.setOnGround(true);e.setPos(w.position().add(3,1.5,0));before=e.position();drive(w,p,1,0,3);
        h.assertTrue(e.position().equals(before)&&!WagonPlatform.supportedByWagon(e),"Leaving platform retained attachment");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void nearby_ground_and_air_entities_are_not_carried(GameTestHelper h) {
        var w=wagon(h);var p=driver(h,w);
        var ground=EntityType.SHEEP.create(h.getLevel());ground.setNoAi(true);ground.setPos(w.position().add(2,0,0));
        ground.setOnGround(true);h.getLevel().addFreshEntity(ground);
        var air=EntityType.SHEEP.create(h.getLevel());air.setNoAi(true);air.setPos(w.position().add(0,2,.7));
        air.setOnGround(false);h.getLevel().addFreshEntity(air);
        Vec3 groundStart=ground.position(),airStart=air.position();drive(w,p,1,0,5);
        h.assertTrue(ground.position().equals(groundStart)&&air.position().equals(airStart),"Nearby ground or airborne entity was transported");
        h.assertTrue(!w.platform().supports(ground)&&!w.platform().supports(air),"Proximity was mistaken for platform support");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void cargo_occupant_follows_one_block_slope_and_side_tilt(GameTestHelper h) {
        var w=wagon(h);var p=driver(h,w);var sheep=standing(h,w,EntityType.SHEEP,new Vec3(0,1.5,.5));
        for(int x=1;x<24;x++)for(int z=1;z<=10;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
        boolean tilted=false;
        for(int i=0;i<38;i++) {
            drive(w,p,1,0,1);sheep.tick();tilted|=Math.abs(w.pitch())>.03;
            h.assertTrue(w.platform().supports(sheep)&&h.getLevel().noCollision(sheep,sheep.getBoundingBox().deflate(.001)),"Slope lost support or embedded occupant at tick "+i+" "+sheep.position());
        }
        h.assertTrue(tilted&&w.getY()>h.absolutePos(new BlockPos(0,2,0)).getY()+.8,"Occupied wagon did not climb");
        WagonPose old=w.pose();w.platform().begin();
        try { h.assertTrue(w.platform().moveTo(new WagonPose(old.position().add(0,.15,0),old.yaw(),0,.10F)),"Small side tilt was blocked by its occupant"); }
        finally { w.platform().end(); }
        h.assertTrue(w.platform().supports(sheep)&&h.getLevel().noCollision(sheep,sheep.getBoundingBox().deflate(.001)),"Side tilt embedded occupant");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void occupied_platform_stops_before_a_low_ceiling(GameTestHelper h) {
        var w=wagon(h);var e=standing(h,w,EntityType.SHEEP,new Vec3(0,1.5,.5));
        h.setBlock(new BlockPos(11,5,17),Blocks.STONE);Vec3 start=w.position(),feet=e.position();
        w.platform().begin();
        try { h.assertTrue(!w.platform().moveTo(new WagonPose(start.add(0,.3,0),w.getYRot(),0,0)),"Platform crushed occupant into ceiling"); }
        finally { w.platform().end(); }
        h.assertTrue(w.position().equals(start)&&e.position().equals(feet),"Blocked platform step partly committed: "+w.position()+" "+e.position());
        h.assertTrue(h.getLevel().noCollision(e,e.getBoundingBox().deflate(.001)),"Blocked platform step embedded occupant");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void tipping_releases_standing_entities(GameTestHelper h) {
        var w=wagon(h);var e=standing(h,w,EntityType.SHEEP,new Vec3(0,1.5,.5));Vec3 start=e.position();
        w.platform().begin();
        try { w.platform().moveTo(new WagonPose(w.position(),180,0,.8F)); }
        finally { w.platform().end(); }
        h.assertTrue(e.position().equals(start)&&!w.platform().supports(e),"Tipped vehicle glued entity to its floor");h.succeed();
    }
}
