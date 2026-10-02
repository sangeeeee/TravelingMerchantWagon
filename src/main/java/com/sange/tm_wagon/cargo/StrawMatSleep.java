package com.sange.tm_wagon.cargo;

import com.mojang.datafixers.util.Either;
import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.assembly.AssemblyFrameBlockEntity;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.network.WagonNetwork;
import com.sange.tm_wagon.physics.WagonPose;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Vanilla sleep counters and night skipping, without replacing the player's respawn location. */
@EventBusSubscriber(modid=TravelingMerchantWagon.MODID)
public final class StrawMatSleep {
    private record Session(CargoHold hold,UUID mat,int anchor,WagonPose pose,Vec3 head,BlockPos bed) {}
    private static final Map<Player,Session> SERVER_SESSIONS=new WeakHashMap<>(),CLIENT_SESSIONS=new WeakHashMap<>();
    // Entity equality uses the numeric ID: the integrated server and its client must not share a map.
    private static Map<Player,Session> sessions(Player player) { return player.level().isClientSide?CLIENT_SESSIONS:SERVER_SESSIONS; }
    private static synchronized Session session(Player player) { return sessions(player).get(player); }
    private static synchronized void put(Player player,Session value) { sessions(player).put(player,value); }
    private static synchronized Session remove(Player player) { return sessions(player).remove(player); }
    public static boolean nativeStart(Player player,BlockPos pos) {
        Session s=session(player);return s!=null&&s.bed.equals(pos)&&!player.isSleeping();
    }
    public static Vec3 sleepingPoint(Player player) { Session s=session(player);return s==null?null:s.head; }
    public static WagonPose sleepingPose(Player player) { Session s=session(player);return s==null?null:s.pose; }
    public static Direction direction(Player player) { Session s=session(player);return s==null?null:Direction.fromYRot(s.pose.yaw()); }
    private static Vec3 head(CargoHold hold,int anchor) {
        AABB box=CargoHold.matBox(anchor);
        return hold.owner().cargoPose().point(new Vec3((box.minX+box.maxX)/2,CargoHold.FLOOR+.16,box.minZ+.26));
    }
    private static boolean stable(CargoHold hold) {
        var pose=hold.owner().cargoPose();
        return Math.abs(pose.pitch())<Math.toRadians(30)&&Math.abs(pose.roll())<Math.toRadians(25)
            &&(!(hold.owner() instanceof WagonEntity w)||!w.falling()&&w.getDeltaMovement().lengthSqr()<.0001);
    }
    public static String sleep(CargoHold hold,CargoEntry mat,Player player) {
        if(!(player instanceof ServerPlayer p)||!hold.valid(mat,player)||mat.kind!=CargoEntry.Kind.STRAW_MAT)return "message.tm_wagon.assembly_busy";
        var level=p.serverLevel();
        if(!level.dimensionType().bedWorks()||!level.dimensionType().natural())return "message.tm_wagon.mat_dimension";
        if(p.isSleeping()||!p.isAlive()||p.isSpectator())return "message.tm_wagon.mat_unavailable";
        if(mat.sleeper!=null)return "block.minecraft.bed.occupied";
        if(!stable(hold))return "message.tm_wagon.mat_unstable";
        if(level.isDay())return "block.minecraft.bed.no_sleep";
        int anchor=hold.slot(mat);Vec3 point=head(hold,anchor);
        if(p.distanceToSqr(point)>36)return "block.minecraft.bed.too_far_away";
        if(!p.getAbilities().instabuild&&!level.getEntitiesOfClass(Monster.class,new AABB(point,point).inflate(8,5,8),m->m.isPreventingPlayerRest(p)).isEmpty())
            return "block.minecraft.bed.not_safe";
        Vec3 clear=null;
        for(double lift=0;lift<=.25;lift+=.025) {
            Vec3 candidate=point.add(0,lift,0);
            if(level.noCollision(p,p.getDimensions(Pose.SLEEPING).makeBoundingBox(candidate).deflate(.0001))) { clear=candidate;break; }
        }
        if(clear==null)return "block.minecraft.bed.obstructed";
        point=clear;
        BlockPos bed=BlockPos.containing(point);
        var result=net.neoforged.neoforge.event.EventHooks.canPlayerStartSleeping(p,bed,Either.right(Unit.INSTANCE));
        if(result.left().isPresent())return problem(result.left().get());
        p.closeContainer();
        var s=new Session(hold,mat.id,anchor,hold.owner().cargoPose(),point,bed);put(p,s);mat.sleeper=p.getUUID();hold.changed(true);
        send(p,s,true);
        // The scoped ServerPlayer mixin calls Player's implementation: reset the sleep counter,
        // enter the native sleeping pose, and update the native sleeping-player list, without setRespawnPosition.
        result=p.startSleepInBed(bed);
        if(result.left().isPresent()) { remove(p);mat.sleeper=null;hold.changed(true);send(p,s,false);return problem(result.left().get()); }
        p.connection.teleport(point.x,point.y,point.z,p.getYRot(),p.getXRot());
        return null;
    }
    private static String problem(Player.BedSleepingProblem problem) {
        var message=problem.getMessage();return message instanceof net.minecraft.network.chat.MutableComponent c
            &&c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t?t.getKey():"message.tm_wagon.mat_unavailable";
    }
    private static boolean remains(Session s,Player player) {
        var hold=s.hold;if(hold==null||!hold.owner().cargoLive()||hold.owner().cargoBusy()||hold.owner().cargoLevel()!=player.level())return false;
        var entry=hold.entry(s.anchor);if(entry==null||!entry.id.equals(s.mat)||!player.getUUID().equals(entry.sleeper)||!stable(hold))return false;
        var now=hold.owner().cargoPose();
        // A moving, falling or converting wagon wakes its sleeper rather than attaching a camera to an invalid bed.
        return now.position().distanceToSqr(s.pose.position())<.0025&&Math.abs(net.minecraft.util.Mth.wrapDegrees(now.yaw()-s.pose.yaw()))<2
            &&Math.abs(now.pitch()-s.pose.pitch())<.035&&Math.abs(now.roll()-s.pose.roll())<.035;
    }
    @SubscribeEvent public static void continueSleep(CanContinueSleepingEvent event) {
        if(!(event.getEntity() instanceof Player p))return;Session s=session(p);if(s==null)return;
        event.setContinueSleeping(event.getProblem()!=Player.BedSleepingProblem.NOT_POSSIBLE_NOW&&remains(s,p));
    }
    public static void wake(CargoHold hold,CargoEntry entry) {
        if(hold.owner().cargoLevel()==null||hold.owner().cargoLevel().isClientSide)return;
        java.util.List<Player> players;
        synchronized(StrawMatSleep.class) { players=SERVER_SESSIONS.entrySet().stream().filter(e->e.getValue().hold==hold&&(entry==null||entry.id.equals(e.getValue().mat))).map(Map.Entry::getKey).toList(); }
        for(Player player:players) {
            if(player.isSleeping())player.stopSleepInBed(true,true);
            else finishWake(player);
        }
    }
    /** Called in LivingEntity.stopSleeping before vanilla can use the unrelated block under a moving bed. */
    public static boolean finishWake(Player player) {
        Session s=remove(player);if(s==null)return false;
        Vec3 target=standUp(player,s);
        player.setPose(Pose.STANDING);player.clearSleepingPos();player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;
        player.setPos(target);player.setXRot(0);player.setOnGround(false);
        if(!player.level().isClientSide) {
            if(s.hold!=null) { var mat=s.hold.entry(s.anchor);if(mat!=null&&mat.id.equals(s.mat)) { mat.sleeper=null;s.hold.changed(true); } }
            if(player instanceof ServerPlayer p)send(p,s,false);
        }
        return true;
    }
    private static Vec3 standUp(Player player,Session s) {
        var pose=s.hold!=null&&s.hold.owner().cargoLive()?s.hold.owner().cargoPose():s.pose;
        Vec3 centre=CargoHold.centre(s.anchor).add(0,0,-.7);
        // Standing boxes remain world-aligned. Raise above the actual tilted collision tops,
        // then check the full standing dimensions instead of reusing the tiny sleeping box.
        for(double dx:new double[]{0,-.18,.18})for(double dz:new double[]{0,-.5,.5}) {
            Vec3 base=aboveCover(player,s.hold,pose,pose.point(centre.add(dx,.126,dz)));
            for(double dy=0;dy<=2.5;dy+=.125) {
                Vec3 point=base.add(0,dy,0);AABB box=player.getDimensions(Pose.STANDING).makeBoundingBox(point).deflate(.0001);
                if(player.level().getWorldBorder().isWithinBounds(box)&&player.level().noCollision(player,box))return point;
            }
        }
        if(s.hold!=null&&s.hold.owner() instanceof WagonEntity w)return w.safeDismount(player);
        for(int dy=0;dy<=4;dy++)for(double x:new double[]{-2.5,2.5})for(double z:new double[]{0,-2,2}) {
            Vec3 point=pose.point(new Vec3(x,dy,z));
            if(player.level().noCollision(player,player.getDimensions(Pose.STANDING).makeBoundingBox(point)))return point;
        }
        return s.head.add(0,3,0);
    }
    /** Start above cloth at the wake footprint, including a sloped wagon; rolls have no surface. */
    private static Vec3 aboveCover(Player player,CargoHold hold,WagonPose pose,Vec3 base) {
        if(hold==null||!hold.owner().cargoLive())return base;
        var cloth=hold.cover().boxes(hold.owner().cargoBody());if(cloth.isEmpty())return base;
        Vec3 normal=pose.vector(new Vec3(0,1,0));if(normal.y<.5)return base;
        Vec3 plane=pose.point(new Vec3(0,CargoCover.TOP,0));double height=base.y;
        double half=player.getDimensions(Pose.STANDING).width()/2;
        // Only five surface samples on waking; the existing full-body collision search still follows.
        for(double[] offset:new double[][]{{0,0},{-half,-half},{-half,half},{half,-half},{half,half}}) {
            double x=base.x+offset[0],z=base.z+offset[1];
            double y=plane.y-(normal.x*(x-plane.x)+normal.z*(z-plane.z))/normal.y;
            Vec3 local=pose.local(new Vec3(x,y,z));var sheet=cloth.getFirst();
            if(local.x>=sheet.minX&&local.x<=sheet.maxX&&local.z>=sheet.minZ&&local.z<=sheet.maxZ)
                height=Math.max(height,y+.001);
        }
        return height>base.y?new Vec3(base.x,height,base.z):base;
    }
    private static void send(ServerPlayer player,Session s,boolean sleeping) {
        // Fake/headless players have no negotiated client payload channels.
        if(player.connection==null||!player.connection.hasChannel(WagonNetwork.MatSleep.TYPE))return;
        int wagon=s.hold!=null&&s.hold.owner() instanceof WagonEntity w?w.getId():-1;
        BlockPos frame=s.hold!=null&&s.hold.owner() instanceof AssemblyFrameBlockEntity f?f.getBlockPos():BlockPos.ZERO;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,new WagonNetwork.MatSleep(player.getId(),wagon,frame,s.mat,s.anchor,s.pose.position(),s.pose.yaw(),s.pose.pitch(),s.pose.roll(),s.head,sleeping));
    }
    public static void receive(Level level,WagonNetwork.MatSleep packet) {
        if(!(level.getEntity(packet.playerId()) instanceof Player p))return;
        if(!packet.sleeping()) { if(session(p)!=null) { if(p.isSleeping())p.stopSleeping();else finishWake(p); }return; }
        CargoHold hold=null;
        if(packet.wagonId()>=0&&level.getEntity(packet.wagonId()) instanceof WagonEntity w)hold=w.cargo();
        else if(level.getBlockEntity(packet.frame()) instanceof AssemblyFrameBlockEntity f)hold=f.cargo();
        put(p,new Session(hold,packet.mat(),packet.anchor(),new WagonPose(packet.position(),packet.yaw(),packet.pitch(),packet.roll()),packet.head(),BlockPos.containing(packet.head())));
        if(p.isSleeping())p.setPos(packet.head());
    }
    /** Late tracking sends the same one-shot session; no periodic sleep packet is necessary. */
    public static void track(ServerPlayer observer,Player sleeper) {
        Session s=session(sleeper);if(s==null||observer.connection==null||!observer.connection.hasChannel(WagonNetwork.MatSleep.TYPE))return;
        int wagon=s.hold.owner() instanceof WagonEntity w?w.getId():-1;
        BlockPos frame=s.hold.owner() instanceof AssemblyFrameBlockEntity f?f.getBlockPos():BlockPos.ZERO;
        PacketDistributor.sendToPlayer(observer,new WagonNetwork.MatSleep(sleeper.getId(),wagon,frame,s.mat,s.anchor,s.pose.position(),s.pose.yaw(),s.pose.pitch(),s.pose.roll(),s.head,true));
    }
    @SubscribeEvent public static void tracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
        if(event.getEntity() instanceof ServerPlayer observer&&event.getTarget() instanceof Player sleeper)track(observer,sleeper);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        synchronized(StrawMatSleep.class) {
            SERVER_SESSIONS.keySet().removeIf(p->p.level()==event.getLevel());CLIENT_SESSIONS.keySet().removeIf(p->p.level()==event.getLevel());
        }
    }
    private StrawMatSleep() {}
}
