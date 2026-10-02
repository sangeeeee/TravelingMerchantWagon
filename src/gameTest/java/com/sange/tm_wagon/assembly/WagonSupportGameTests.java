package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.entity.WagonSupport;
import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
public class WagonSupportGameTests {
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
    private static void settle(Entity entity,WagonEntity w,Vec3 local) {
        entity.setPos(w.pose().point(local).add(0,.01,0));
        entity.move(MoverType.SELF,new Vec3(0,-.05,0));entity.setDeltaMovement(Vec3.ZERO);
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void moving_wagon_does_not_transport_unseated_player_mob_or_item(GameTestHelper h) {
        var w=wagon(h);var p=driver(h,w);var player=h.makeMockServerPlayerInLevel();
        var sheep=EntityType.SHEEP.create(h.getLevel());sheep.setNoAi(true);h.getLevel().addFreshEntity(sheep);
        var item=new ItemEntity(h.getLevel(),0,0,0,new ItemStack(Items.DIAMOND));h.getLevel().addFreshEntity(item);
        settle(player,w,new Vec3(0,1.5,.5));settle(sheep,w,new Vec3(.3,1.5,.3));settle(item,w,new Vec3(-.35,1.5,1.1));
        Vec3 playerStart=player.position(),sheepStart=sheep.position(),itemStart=item.position(),wagonStart=w.position();
        for(int i=0;i<8;i++) { w.acceptInput(p,i<4?1:-1,i%2==0?1:0);w.tick(); }
        h.assertTrue(w.position().distanceTo(wagonStart)>.5,"Fixture did not move");
        h.assertTrue(player.position().equals(playerStart)&&sheep.position().equals(sheepStart)&&item.position().equals(itemStart),"Wagon still transported an unseated entity");
        h.assertTrue(!player.isPassenger()&&!sheep.isPassenger()&&!item.isPassenger(),"Standing entities became passengers");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void stationary_wagon_still_has_solid_floor_and_flight_support(GameTestHelper h) {
        var w=wagon(h);var player=h.makeMockServerPlayerInLevel();settle(player,w,new Vec3(0,1.5,.7));
        h.assertTrue(player.onGround()&&WagonSupport.supportedByWagon(player),"Solid floor no longer supports player");
        try {
            var check=ServerGamePacketListenerImpl.class.getDeclaredMethod("noBlocksAround",Entity.class);check.setAccessible(true);
            h.assertTrue(!(boolean)check.invoke(player.connection,player),"Standing on wagon classified as flying");
            player.setPos(player.position().add(4,0,0));
            h.assertTrue((boolean)check.invoke(player.connection,player),"Nearby wagon incorrectly disabled flight checks");
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void ground_below_wagon_is_not_classified_as_onboard(GameTestHelper h) {
        var w=wagon(h);var mob=EntityType.RABBIT.create(h.getLevel());mob.setNoAi(true);h.getLevel().addFreshEntity(mob);
        mob.setPos(w.pose().point(new Vec3(0,0,.7)));mob.setOnGround(true);
        h.assertTrue(!WagonSupport.supportedByWagon(mob),"Entity below floor protected as onboard");
        settle(mob,w,new Vec3(0,1.5,.7));
        h.assertTrue(WagonSupport.supportedByWagon(mob),"Onboard entity not recognized");
        mob.setPos(mob.position().add(0,.3,0));mob.setOnGround(false);
        h.assertTrue(!WagonSupport.supportedByWagon(mob),"Airborne entity classified as ground support");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void road_clearing_protects_deck_jump_and_footboard_but_clears_below_floor(GameTestHelper h) {
        var w=wagon(h);
        var deck=EntityType.SHEEP.create(h.getLevel());deck.setNoAi(true);h.getLevel().addFreshEntity(deck);
        settle(deck,w,new Vec3(0,1.5,.7));deck.setPos(deck.position().add(0,.4,0));deck.setDeltaMovement(0,.42,0);
        var board=EntityType.CHICKEN.create(h.getLevel());board.setNoAi(true);h.getLevel().addFreshEntity(board);
        // Choose a real front footboard top face outside the cargo floor footprint.
        var box=w.collisionBoxes().stream().filter(b->w.pose().local(b.getCenter()).z<-2&&b.maxY>w.getY()+1).findFirst().orElseThrow();
        board.setPos(box.getCenter().x,box.maxY,box.getCenter().z);board.setDeltaMovement(Vec3.ZERO);
        h.assertTrue(WagonSupport.supportedByWagon(board),"Footboard fixture lacks support");
        var below=EntityType.RABBIT.create(h.getLevel());below.setNoAi(true);below.setPos(w.pose().point(new Vec3(0,0,.7)));h.getLevel().addFreshEntity(below);
        w.crowd().begin(w.pose().forward().scale(.1));
        try {
            h.assertTrue(!w.crowd().yields(deck)&&!w.crowd().yields(board),"Onboard mob selected for road clearing");
            h.assertTrue(w.crowd().yields(below),"Ground mob below wagon incorrectly protected");
        } finally { w.crowd().end(); }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void vanilla_player_packets_can_approach_and_retreat_from_sloped_walls(GameTestHelper h) {
        var w=wagon(h);var player=h.makeMockServerPlayerInLevel();var client=h.makeMockPlayer(GameType.SURVIVAL);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        try {
            var id=ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");id.setAccessible(true);
            var pending=ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");pending.setAccessible(true);
            player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id.getInt(player.connection)));
            for(Vec3 direction:new Vec3[]{new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,0,-1),new Vec3(0,0,1)}) {
                var pose=new WagonPose(w.position(),129.74466F,.20932731F,-.19161685F);w.applyPose(pose);
                player.setPos(pose.point(new Vec3(0,1.5,.4)).add(0,.4,0));player.move(MoverType.SELF,new Vec3(0,-.8,0));
                player.setDeltaMovement(Vec3.ZERO);player.connection.resetPosition();client.setPos(player.position());client.setOnGround(true);
                for(int step=0;step<40;step++) {
                    player.connection.tick();
                    client.move(MoverType.SELF,pose.vector(direction.scale(step<25?.12:-.12)).add(0,-.08,0));client.setDeltaMovement(Vec3.ZERO);
                    player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(client.getX(),client.getY(),client.getZ(),step*2,10,client.onGround()));
                    h.assertTrue(pending.get(player.connection)==null,"Vanilla wall contact caused a correction: "+direction+" tick="+step);
                    h.assertTrue(player.position().distanceTo(client.position())<.01&&Math.abs(player.getYRot()-step*2)<.001,"Vanilla movement or camera lost at wall");
                }
            }
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        h.succeed();
    }
}
