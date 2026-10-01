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
    public static void standing_player_can_press_and_leave_walls_without_server_corrections(GameTestHelper h) {
        var w=wagon(h);var player=h.makeMockServerPlayerInLevel();var client=h.makeMockPlayer(GameType.SURVIVAL);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        try {
            var id=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");id.setAccessible(true);
            player.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(id.getInt(player.connection)));
            var pending=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");pending.setAccessible(true);
            for(float yaw:new float[]{180,90,0,270,203})for(Vec3 direction:new Vec3[]{new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,0,-1),new Vec3(0,0,1)}) {
                w.applyPose(new WagonPose(w.position(),yaw,0,0));
                player.setPos(w.pose().point(new Vec3(0,1.5,.4)));player.setOnGround(true);player.connection.resetPosition();
                client.setPos(player.position());client.setOnGround(true);
                for(int i=0;i<30;i++) {
                    player.connection.tick();
                    Vec3 walking=w.pose().vector(direction.scale(i<20?.12:-.12));
                    client.move(MoverType.SELF,walking.add(0,-.08,0));client.setDeltaMovement(Vec3.ZERO);
                    Vec3 local=w.pose().local(client.position());
                    w.platform().acceptStandingMovement(player,new Vec3((float)local.x,(float)local.y,(float)local.z),0,0,true);
                    h.assertTrue(pending.get(player.connection)==null,"Wall contact caused a correction: yaw="+yaw+" direction="+direction+" tick="+i+" local="+local);
                    h.assertTrue(player.position().distanceTo(client.position())<.02,"Wall contact lost relative movement: "+local+" server="+w.pose().local(player.position()));
                }
            }
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void turning_platform_keeps_wall_contacts_clear_and_delayed_input_can_retreat(GameTestHelper h) {
        var w=wagon(h);var player=h.makeMockServerPlayerInLevel();player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        try {
            var id=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");id.setAccessible(true);
            player.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(id.getInt(player.connection)));
            var pending=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");pending.setAccessible(true);
            for(Vec3 contact:new Vec3[]{new Vec3(.7,1.5,.4),new Vec3(0,1.5,-1.04375),new Vec3(0,1.5,1.91875)}) {
                w.applyPose(new WagonPose(w.position(),180,0,0));player.setPos(w.pose().point(contact));player.setOnGround(true);player.setDeltaMovement(Vec3.ZERO);player.connection.resetPosition();
                for(int i=0;i<12;i++) {
                    player.connection.tick();
                    w.platform().begin();
                    h.assertTrue(w.platform().carries(player),"Contact fixture not carried: local="+w.pose().local(player.position())+" ground="+player.onGround()+" velocity="+player.getDeltaMovement()+" support="+w.platform().supports(player));
                    try { h.assertTrue(w.platform().moveTo(new WagonPose(w.position(),w.getYRot()+1,0,0)),"Turning at a wall was blocked"); }
                    finally { w.platform().end(); }
                    h.assertTrue(h.getLevel().noCollision(player,player.getBoundingBox().deflate(.00001)),"Turning platform embedded wall occupant: "+contact+" yaw="+w.getYRot());
                    // A delayed client still sends a contact position from its older, less-rotated geometry.
                    w.platform().acceptStandingMovement(player,new Vec3((float)contact.x,(float)contact.y,(float)contact.z),0,0,true);
                    h.assertTrue(pending.get(player.connection)==null,"Delayed wall contact caused a teleport correction");
                }
                Vec3 before=w.pose().local(player.position());
                Vec3 retreat=contact.x!=0?new Vec3(-.2,0,0):new Vec3(0,0,contact.z<0?.2:-.2);
                w.platform().acceptStandingMovement(player,before.add(retreat),0,0,true);
                h.assertTrue(w.pose().local(player.position()).distanceTo(before.add(retreat))<.02,"Wall occupant could not retreat");
            }
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void exact_foot_contact_survives_ground_flag_changes_but_not_jump_or_air(GameTestHelper h) {
        var w=wagon(h);var e=standing(h,w,EntityType.SHEEP,new Vec3(.2,1.5,.6));
        e.setOnGround(false);e.setDeltaMovement(new Vec3(0,-.0784,0));
        h.assertTrue(WagonPlatform.standingWagon(e)==w,"Exact foot contact lost packet eligibility when ground flag cleared");
        w.platform().begin();h.assertTrue(w.platform().carries(e),"Exact contact lost carry eligibility");w.platform().end();
        e.setDeltaMovement(new Vec3(0,.42,0));
        h.assertTrue(WagonPlatform.standingWagon(e)==null,"Jump impulse retained standing packet eligibility");
        w.platform().begin();h.assertTrue(!w.platform().carries(e),"Jump impulse retained carry eligibility");w.platform().end();
        e.setDeltaMovement(Vec3.ZERO);e.setPos(e.position().add(0,.025,0));
        h.assertTrue(WagonPlatform.standingWagon(e)==null,"Nearby airborne feet were snapped to platform");
        w.platform().begin();h.assertTrue(!w.platform().carries(e),"Airborne entity was caught by the platform");w.platform().end();h.succeed();
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
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void saved_sloped_seat_dismount_settles_and_preserves_camera(GameTestHelper h) {
        var w=wagon(h);var parts=new java.util.EnumMap<WagonSlot,WagonPart>(WagonSlot.class);parts.putAll(w.parts());parts.put(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);w.configure(parts,Direction.NORTH);
        w.applyPose(new WagonPose(w.position(),129.74466F,.20932731F,-.19161685F));
        var player=h.makeMockServerPlayerInLevel();var client=h.makeMockPlayer(GameType.SURVIVAL);
        try {
            var id=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");id.setAccessible(true);
            var pending=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");pending.setAccessible(true);
            player.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(id.getInt(player.connection)));
            h.assertTrue(player.startRiding(w),"Failed to mount sloped seat");w.positionRider(player);
            player.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(id.getInt(player.connection)));
            player.stopRiding();
            player.connection.resetPosition();client.setPos(player.position());client.setDeltaMovement(Vec3.ZERO);
            for(int i=0;i<25;i++) {
                player.connection.tick();
                var serverPose=w.pose();
                // Vanilla entity tracking stores yaw in 1/256-turn increments.
                float clientYaw=(float)(Math.floor(serverPose.yaw()*256/360)*360/256);
                w.applyPose(new WagonPose(serverPose.position(),clientYaw,serverPose.pitch(),serverPose.roll()));
                client.move(MoverType.SELF,new Vec3(0,-.08,0));client.setDeltaMovement(Vec3.ZERO);
                Vec3 local=w.pose().local(client.position());
                boolean standing=WagonPlatform.standingWagon(client)==w;
                w.applyPose(serverPose);
                if(standing)w.platform().acceptStandingMovement(player,new Vec3((float)local.x,(float)local.y,(float)local.z),i*3,10,true);
                else player.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(client.getX(),client.getY(),client.getZ(),i*3,10,client.onGround()));
                h.assertTrue(pending.get(player.connection)==null,"Sloped dismount enters teleport correction loop at tick "+i+" local="+local+" server="+player.position()+" client="+client.position());
                h.assertTrue(Math.abs(player.getYRot()-i*3)<.01,"Sloped dismount discarded camera rotation at tick "+i+" local="+local);
                h.assertTrue(player.position().distanceTo(w.pose().point(local))<.05,"Sloped dismount lost movement at tick "+i+" local="+local+" server="+player.position()+" client="+client.position());
            }
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void stale_standing_contact_falls_back_to_vanilla_movement(GameTestHelper h) {
        var w=wagon(h);var player=h.makeMockServerPlayerInLevel();
        try {
            var id=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");id.setAccessible(true);
            var pending=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");pending.setAccessible(true);
            player.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(id.getInt(player.connection)));
            player.setPos(w.position().add(4,0,0));player.setDeltaMovement(Vec3.ZERO);player.setOnGround(true);
            player.connection.resetPosition();
            // Contact can disappear after dismounting, jumping, or removal of the wagon.
            for(int step=0;step<3;step++) {
                player.connection.tick();
                Vec3 target=player.position().add(.1,step==2?.42:0,0);
                if(step==2)player.setDeltaMovement(0,.42,0);
                var packet=new com.sange.tm_wagon.network.WagonNetwork.Standing(step==1?-1:w.getId(),0,100,0,
                    35+step*25,12,step!=2,target.x,target.y,target.z);
                com.sange.tm_wagon.network.WagonNetwork.handleStanding(player,packet);
                h.assertTrue(pending.get(player.connection)==null,"Stale contact caused a teleport correction");
                h.assertTrue(player.position().distanceTo(target)<1e-5,"Stale contact swallowed walking/jumping");
                h.assertTrue(Math.abs(player.getYRot()-packet.yaw())<.001&&Math.abs(player.getXRot()-12)<.001,"Stale contact swallowed camera rotation");
            }
            // The fallback must not bypass the ordinary pending-teleport handshake.
            Vec3 authoritative=player.position();player.connection.teleport(authoritative.x,authoritative.y,authoritative.z,90,0);
            com.sange.tm_wagon.network.WagonNetwork.handleStanding(player,new com.sange.tm_wagon.network.WagonNetwork.Standing(
                -1,0,0,0,45,10,false,authoritative.x+5,authoritative.y,authoritative.z));
            h.assertTrue(player.position().equals(authoritative)&&pending.get(player.connection)!=null,"Fallback bypassed pending teleport validation");
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        h.succeed();
    }

    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void sloped_wall_contact_with_synchronized_yaw_does_not_correct_or_lock(GameTestHelper h) {
        var w=wagon(h);var player=h.makeMockServerPlayerInLevel();var client=h.makeMockPlayer(GameType.SURVIVAL);
        try {
            var id=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");id.setAccessible(true);
            var pending=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");pending.setAccessible(true);
            player.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(id.getInt(player.connection)));
            for(Vec3 direction:new Vec3[]{new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,0,-1),new Vec3(0,0,1)}) {
                var serverPose=new WagonPose(w.position(),129.74466F,.20932731F,-.19161685F);
                // Full yaw is now synchronized with pitch/roll. The old 129.375-degree
                // quantized client pose diverged at the side wall after six walking ticks.
                var clientPose=new WagonPose(w.position(),serverPose.yaw(),serverPose.pitch(),serverPose.roll());
                w.applyPose(serverPose);
                player.setPos(serverPose.point(new Vec3(0,1.5,.4)).add(0,.4,0));
                player.move(MoverType.SELF,new Vec3(0,-.8,0));player.setDeltaMovement(Vec3.ZERO);player.connection.resetPosition();
                client.setPos(player.position());client.setDeltaMovement(Vec3.ZERO);client.setOnGround(true);
                for(int step=0;step<40;step++) {
                    player.connection.tick();w.applyPose(clientPose);
                    client.move(MoverType.SELF,clientPose.vector(direction.scale(step<25?.12:-.12)).add(0,-.08,0));client.setDeltaMovement(Vec3.ZERO);
                    Vec3 local=clientPose.local(client.position());w.applyPose(serverPose);
                    com.sange.tm_wagon.network.WagonNetwork.handleStanding(player,new com.sange.tm_wagon.network.WagonNetwork.Standing(
                        w.getId(),(float)local.x,(float)local.y,(float)local.z,step*2,10,client.onGround(),client.getX(),client.getY(),client.getZ()));
                    h.assertTrue(pending.get(player.connection)==null,"Sloped wall causes correction: direction="+direction+" tick="+step+" local="+local+" player="+serverPose.local(player.position()));
                    h.assertTrue(player.position().distanceTo(client.position())<.08,"Sloped wall diverges: direction="+direction+" tick="+step+" client="+serverPose.local(client.position())+" server="+serverPose.local(player.position())+" local="+local);
                }
            }
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        h.succeed();
    }

}
