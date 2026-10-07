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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Vanilla sleep counters and night skipping, without replacing the player's respawn location. */

public final class StrawMatSleep {
    private record Session(CargoHold hold,UUID mat,int anchor,WagonPose pose,Vec3 head,BlockPos bed,int wagonId,BlockPos frame,boolean reversed) {
        Session(CargoHold hold,UUID mat,int anchor,WagonPose pose,Vec3 head,BlockPos bed) {
            this(hold,mat,anchor,pose,head,bed,hold!=null&&hold.owner() instanceof WagonEntity w?w.getId():-1,
                hold!=null&&hold.owner() instanceof AssemblyFrameBlockEntity f?f.getBlockPos():BlockPos.ZERO,
                hold!=null&&hold.entry(anchor)!=null&&hold.entry(anchor).reversed);
        }
        Vec3 localHead() { return pose.local(head); }
    }
    private static final Map<LivingEntity,Session> SERVER_SESSIONS=new WeakHashMap<>(),CLIENT_SESSIONS=new WeakHashMap<>();
    // Entity equality uses the numeric ID: the integrated server and its client must not share a map.
    private static Map<LivingEntity,Session> sessions(LivingEntity player) { return player.level().isClientSide()?CLIENT_SESSIONS:SERVER_SESSIONS; }
    private static synchronized Session session(LivingEntity player) { return sessions(player).get(player); }
    private static synchronized void put(LivingEntity player,Session value) { sessions(player).put(player,value); }
    private static synchronized Session remove(LivingEntity player) { return sessions(player).remove(player); }
    private static Session resolved(LivingEntity sleeper) {
        Session s=session(sleeper);if(s==null||s.hold!=null)return s;
        // A conversion payload can arrive before vanilla has spawned the new wagon.
        CargoHold hold=null;
        if(s.wagonId>=0&&sleeper.level().getEntity(s.wagonId) instanceof WagonEntity w)hold=w.cargo();
        else if(s.wagonId<0&&sleeper.level().getBlockEntity(s.frame) instanceof AssemblyFrameBlockEntity f)hold=f.cargo();
        if(hold!=null) { s=new Session(hold,s.mat,s.anchor,s.pose,s.head,s.bed,s.wagonId,s.frame,s.reversed);put(sleeper,s); }
        return s;
    }
    private static WagonPose pose(Session s) { return s.hold!=null&&s.hold.owner().cargoLive()?s.hold.owner().cargoPose():s.pose; }
    /** Interaction silhouette for the lying body; native sleeping AABBs cover only the head. */
    public static java.util.Optional<Vec3> pickSleeper(LivingEntity sleeper,Vec3 start,Vec3 end) {
        Session s=resolved(sleeper);if(s==null||s.hold==null||!sleeper.isSleeping())return java.util.Optional.empty();
        var bounds=s.hold.matBounds(s.anchor);var p=pose(s);
        return new AABB(bounds.minX,bounds.maxY,bounds.minZ,bounds.maxX,bounds.maxY+.5,bounds.maxZ)
            .clip(p.local(start),p.local(end)).map(p::point);
    }
    public static boolean nativeStart(Player player,BlockPos pos) {
        Session s=session(player);return s!=null&&s.bed.equals(pos)&&!player.isSleeping();
    }
    public static Vec3 sleepingPoint(LivingEntity player) { Session s=resolved(player);return s==null?null:pose(s).point(s.localHead()); }
    public static WagonPose sleepingPose(LivingEntity player) { Session s=resolved(player);return s==null?null:pose(s); }
    public static WagonPose sleepingPose(LivingEntity player,float partial) {
        Session s=resolved(player);if(s==null)return null;
        if(s.hold!=null&&s.hold.owner() instanceof WagonEntity w&&w.cargoLive())
            return new WagonPose(renderPosition(w,partial),net.minecraft.util.Mth.rotLerp(partial,w.yRotO,w.getYRot()),w.renderPitch(partial),w.renderRoll(partial));
        return pose(s);
    }
    /** LevelRenderer uses xOld, not the xo snapshot used by getPosition. */
    public static Vec3 renderPosition(net.minecraft.world.entity.Entity entity,float partial) {
        return new Vec3(net.minecraft.util.Mth.lerp(partial,entity.xOld,entity.getX()),
            net.minecraft.util.Mth.lerp(partial,entity.yOld,entity.getY()),net.minecraft.util.Mth.lerp(partial,entity.zOld,entity.getZ()));
    }
    public static boolean attachedTo(net.minecraft.world.entity.Entity entity,WagonEntity wagon) {
        if(!(entity instanceof LivingEntity living)||!living.isSleeping())return false;
        Session s=resolved(living);
        return s!=null&&s.hold==wagon.cargo();
    }
    public static Vec3 sleepingPoint(LivingEntity player,float partial) {
        Session s=resolved(player);return s==null?null:sleepingPose(player,partial).point(s.localHead());
    }
    public static boolean reversed(LivingEntity player) { Session s=resolved(player);return s!=null&&s.reversed; }
    public static Direction direction(LivingEntity player) { var pose=sleepingPose(player);return pose==null?null:Direction.fromYRot(pose.yaw()+(reversed(player)?180:0)); }
    /** Only active sleepers follow a mat; ordinary entities on deck still use normal world movement. */
    public static void follow(LivingEntity sleeper) {
        if(!sleeper.isSleeping())return;
        Session s=resolved(sleeper);if(s==null)return;
        Vec3 point=pose(s).point(s.localHead());
        long oldSection=net.minecraft.core.SectionPos.asLong(sleeper.blockPosition());
        sleeper.setPos(point);sleeper.setDeltaMovement(Vec3.ZERO);sleeper.fallDistance=0;
        BlockPos bed=BlockPos.containing(point);
        if(!sleeper.getSleepingPos().filter(bed::equals).isPresent())sleeper.setSleepingPos(bed);
        if(sleeper instanceof ServerPlayer p&&p.connection!=null) {
            p.connection.resetPosition();
            // Sleeping movement packets skip vanilla's normal chunk-tracking update.
            if(oldSection!=net.minecraft.core.SectionPos.asLong(bed))p.level().getChunkSource().move(p);
        }
    }
    /** Settle bindings after both wagon and sleeper ticks, independent of entity tick order. */
    
    private static Vec3 head(CargoHold hold,int anchor) {
        AABB box=hold.matBounds(anchor);
        return hold.owner().cargoPose().point(new Vec3((box.minX+box.maxX)/2,box.maxY+.035,hold.entry(anchor).reversed?box.maxZ-.26:box.minZ+.26));
    }
    private static boolean stable(CargoHold hold) {
        var pose=hold.owner().cargoPose();
        return Math.abs(pose.pitch())<Math.toRadians(30)&&Math.abs(pose.roll())<Math.toRadians(25)
            &&(!(hold.owner() instanceof WagonEntity w)||!w.falling()&&w.getDeltaMovement().lengthSqr()<.0001);
    }
    public static String sleep(CargoHold hold,CargoEntry mat,Player player) {
        return sleep(hold,mat,player,null);
    }
    public static String sleep(CargoHold hold,CargoEntry mat,Player player,Vec3 clicked) {
        int slot=hold.slot(mat);
        boolean valid=clicked==null?hold.valid(mat,player):slot>=0&&hold.owner().cargoLive()&&!hold.owner().cargoBusy()
            &&player.isAlive()&&player.level()==hold.owner().cargoLevel()
            &&hold.matBounds(slot).inflate(.04).contains(clicked)
            &&player.distanceToSqr(hold.owner().cargoPose().point(clicked))<=64
            &&!hold.cover().obstructs(hold.owner().cargoPose().local(player.getEyePosition()),clicked);
        if(!(player instanceof ServerPlayer p)||!valid||!mat.sleepingSurface())return "message.tm_wagon.assembly_busy";
        var level=p.level();
        if(!level.environmentAttributes().getValue(mat.kind==CargoEntry.Kind.STRAW_BED?net.minecraft.world.attribute.EnvironmentAttributes.STRAW_BED_RULE:net.minecraft.world.attribute.EnvironmentAttributes.BED_RULE,pointForDimension(hold)).canSleep().equals(net.minecraft.world.attribute.BedRule.Rule.WHEN_DARK))return "message.tm_wagon.mat_dimension";
        if(p.isSleeping()||!p.isAlive()||p.isSpectator())return "message.tm_wagon.mat_unavailable";
        if(mat.sleeper!=null)return "block.minecraft.bed.occupied";
        if(!stable(hold))return "message.tm_wagon.mat_unstable";
        if(!level.isDarkOutside())return "block.minecraft.bed.no_sleep";
        int anchor=hold.slot(mat);Vec3 point=head(hold,anchor);
        // Any segment of the mat can be used, just like either half of a bed.
        var bounds=hold.matBounds(anchor);Vec3 near=hold.owner().cargoPose().local(p.position());
        Vec3 usePoint=clicked!=null?clicked:new Vec3(net.minecraft.util.Mth.clamp(near.x,bounds.minX,bounds.maxX),bounds.maxY,
            net.minecraft.util.Mth.clamp(near.z,bounds.minZ,bounds.maxZ));
        if(p.distanceToSqr(hold.owner().cargoPose().point(usePoint))>36)return "block.minecraft.bed.too_far_away";
        if(!p.getAbilities().instabuild&&!level.getEntitiesOfClass(Monster.class,new AABB(point,point).inflate(8,5,8),m->m.isPreventingPlayerRest(level,p)).isEmpty())
            return "block.minecraft.bed.not_safe";
        Vec3 clear=null;
        for(double lift=0;lift<=.25;lift+=.025) {
            Vec3 candidate=point.add(0,lift,0);
            if(level.noCollision(p,p.getDimensions(Pose.SLEEPING).makeBoundingBox(candidate).deflate(.0001))) { clear=candidate;break; }
        }
        if(clear==null)return "block.minecraft.bed.obstructed";
        point=clear;
        BlockPos bed=BlockPos.containing(point);
        Either<Player.BedSleepingProblem,Unit> result=Either.right(Unit.INSTANCE);
        if(result.left().isPresent())return problem(result.left().get());
        p.closeContainer();
        var s=new Session(hold,mat.id,anchor,hold.owner().cargoPose(),point,bed);put(p,s);mat.sleeper=p.getUUID();hold.changed(true);
        send(p,s,true);
        // The scoped ServerPlayer mixin calls Player's implementation: reset the sleep counter,
        // enter the native sleeping pose, and update the native sleeping-player list, without setRespawnPosition.
        var bedState=mat.kind==CargoEntry.Kind.STRAW_BED?mat.state:net.minecraft.world.level.block.Blocks.BED.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState();
        result=p.startSleepInBed((net.minecraft.world.level.block.AbstractBedBlock)bedState.getBlock(),bedState,net.minecraft.world.attribute.BedRule.CAN_SLEEP_WHEN_DARK,bed);
        if(result.left().isPresent()) { remove(p);mat.sleeper=null;hold.changed(true);send(p,s,false);return problem(result.left().get()); }
        p.connection.teleport(point.x,point.y,point.z,p.getYRot(),p.getXRot());
        return null;
    }
    /** Optional AI integrations reserve the same mat as players and use native living-entity sleep. */
    public static boolean sleepMob(CargoHold hold,int anchor,LivingEntity mob) {
        var mat=hold.entry(anchor);var level=mob.level();
        if(level.isClientSide()||!mob.isAlive()||mob.isSleeping()||mat==null||mat.kind!=CargoEntry.Kind.STRAW_BED
            ||mat.sleeper!=null||!hold.owner().cargoLive()||hold.owner().cargoBusy()||hold.owner().cargoLevel()!=level
            ||!level.environmentAttributes().getValue(mat.kind==CargoEntry.Kind.STRAW_BED?net.minecraft.world.attribute.EnvironmentAttributes.STRAW_BED_RULE:net.minecraft.world.attribute.EnvironmentAttributes.BED_RULE,pointForDimension(hold)).canSleep().equals(net.minecraft.world.attribute.BedRule.Rule.WHEN_DARK)||!stable(hold))return false;
        Vec3 point=head(hold,anchor);if(mob.distanceToSqr(point)>36)return false;
        Vec3 clear=null;
        for(double lift=0;lift<=.25;lift+=.025) {
            Vec3 candidate=point.add(0,lift,0);
            if(level.noCollision(mob,mob.getDimensions(Pose.SLEEPING).makeBoundingBox(candidate).deflate(.0001))) { clear=candidate;break; }
        }
        if(clear==null)return false;
        mob.stopRiding();
        var s=new Session(hold,mat.id,anchor,hold.owner().cargoPose(),clear,BlockPos.containing(clear));
        put(mob,s);mat.sleeper=mob.getUUID();hold.changed(true);send(mob,s,true);
        mob.startSleeping(s.bed);mob.setPos(clear);mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;
        return true;
    }
    public static boolean matSleeper(LivingEntity mob) { return session(mob)!=null; }
    public static void checkMobSleep(LivingEntity mob,boolean resting) {
        Session s=session(mob);if(s==null)return;
        if(!resting||!mob.isAlive()||!mob.isSleeping()||!remains(s,mob)) {
            if(mob.isSleeping())mob.stopSleeping();else finishWake(mob);
        }
    }
    private static String problem(Player.BedSleepingProblem problem) {
        var message=problem.message();return message instanceof net.minecraft.network.chat.MutableComponent c
            &&c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t?t.getKey():"message.tm_wagon.mat_unavailable";
    }
    private static boolean remains(Session s,LivingEntity player) {
        var hold=s.hold;if(hold==null||!hold.owner().cargoLive()||hold.owner().cargoBusy()||hold.owner().cargoLevel()!=player.level())return false;
        var entry=hold.entry(s.anchor);
        return entry!=null&&entry.id.equals(s.mat)&&player.getUUID().equals(entry.sleeper);
    }
    
    public static void wake(CargoHold hold,CargoEntry entry) {
        if(hold.owner().cargoLevel()==null||hold.owner().cargoLevel().isClientSide())return;
        java.util.List<LivingEntity> players;
        synchronized(StrawMatSleep.class) { players=SERVER_SESSIONS.entrySet().stream().filter(e->e.getValue().hold==hold&&(entry==null||entry.id.equals(e.getValue().mat))).map(Map.Entry::getKey).toList(); }
        for(LivingEntity player:players) {
            if(player.isSleeping()) { if(player instanceof Player p)p.stopSleepInBed(true,true);else player.stopSleeping(); }
            else finishWake(player);
        }
    }
    /** Rebind the same sleep session after cargo ownership commits, without stop/startSleeping. */
    public static void transfer(CargoHold source,CargoHold target) {
        java.util.List<Map.Entry<LivingEntity,Session>> sleepers;
        synchronized(StrawMatSleep.class) { sleepers=SERVER_SESSIONS.entrySet().stream().filter(e->e.getValue().hold==source)
            .map(e->Map.entry(e.getKey(),e.getValue())).toList(); }
        for(var sleeper:sleepers) {
            LivingEntity living=sleeper.getKey();Session old=sleeper.getValue();var mat=target.entry(old.anchor);
            if(mat==null||!mat.id.equals(old.mat))continue;
            WagonPose pose=target.owner().cargoPose();Vec3 point=pose.point(old.localHead());
            Session next=new Session(target,old.mat,old.anchor,pose,point,BlockPos.containing(point));
            put(living,next);follow(living);send(living,next,true);
        }
    }
    /** Called in LivingEntity.stopSleeping before vanilla can use the unrelated block under a moving bed. */
    public static boolean finishWake(LivingEntity player) {
        Session s=remove(player);if(s==null)return false;
        Vec3 target=standUp(player,s);
        player.setPose(Pose.STANDING);player.clearSleepingPos();player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;
        player.setPos(target);player.setXRot(0);player.setOnGround(false);
        if(player.level().isClientSide()) {
            // Do not interpolate the first awake frame from a now-detached moving bed.
            player.xOld=player.xo=target.x;player.yOld=player.yo=target.y;player.zOld=player.zo=target.z;
            player.xRotO=0;
        }
        if(!player.level().isClientSide()) {
            if(s.hold!=null) { var mat=s.hold.entry(s.anchor);if(mat!=null&&mat.id.equals(s.mat)) { mat.sleeper=null;s.hold.changed(true); } }
            send(player,s,false);
        }
        return true;
    }
    private static Vec3 standUp(LivingEntity player,Session s) {
        var pose=s.hold!=null&&s.hold.owner().cargoLive()?s.hold.owner().cargoPose():s.pose;
        AABB surface=s.hold!=null?s.hold.matBounds(s.anchor):null;
        Vec3 centre=surface!=null?new Vec3((surface.minX+surface.maxX)/2,surface.maxY+.001,(surface.minZ+surface.maxZ)/2)
            :pose.local(s.head).add(0,-.034,s.reversed?-.78:.78);
        // Standing boxes remain world-aligned. Raise above the actual tilted collision tops,
        // then check the full standing dimensions instead of reusing the tiny sleeping box.
        for(double dx:new double[]{0,-.18,.18})for(double dz:new double[]{0,-.5,.5}) {
            Vec3 base=aboveCover(player,s.hold,pose,pose.point(centre.add(dx,0,dz)));
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
        return pose.point(s.localHead()).add(0,3,0);
    }
    /** Start above cloth at the wake footprint, including a sloped wagon; rolls have no surface. */
    private static Vec3 aboveCover(LivingEntity player,CargoHold hold,WagonPose pose,Vec3 base) {
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
    private static void send(LivingEntity player,Session s,boolean sleeping) {
        // Fake/headless players have no negotiated client payload channels.
        if(player instanceof ServerPlayer p&&(p.connection==null||!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p,WagonNetwork.MatSleep.TYPE)))return;
        int wagon=s.hold!=null&&s.hold.owner() instanceof WagonEntity w?w.getId():-1;
        BlockPos frame=s.hold!=null&&s.hold.owner() instanceof AssemblyFrameBlockEntity f?f.getBlockPos():BlockPos.ZERO;
        var now=pose(s);var point=now.point(s.localHead());
        var packet=new WagonNetwork.MatSleep(player.getId(),wagon,frame,s.mat,s.anchor,now.position(),now.yaw(),now.pitch(),now.roll(),point,sleeping,s.reversed);
        if(player instanceof ServerPlayer p)com.sange.tm_wagon.network.WagonPackets.sendToPlayersTrackingEntityAndSelf(p,packet);
        else com.sange.tm_wagon.network.WagonPackets.sendToPlayersTrackingEntity(player,packet);
    }
    public static void receive(Level level,WagonNetwork.MatSleep packet) {
        if(!(level.getEntity(packet.playerId()) instanceof LivingEntity p))return;
        if(!packet.sleeping()) { if(session(p)!=null) { if(p.isSleeping())p.stopSleeping();else finishWake(p); }return; }
        CargoHold hold=null;
        if(packet.wagonId()>=0&&level.getEntity(packet.wagonId()) instanceof WagonEntity w)hold=w.cargo();
        else if(level.getBlockEntity(packet.frame()) instanceof AssemblyFrameBlockEntity f)hold=f.cargo();
        put(p,new Session(hold,packet.mat(),packet.anchor(),new WagonPose(packet.position(),packet.yaw(),packet.pitch(),packet.roll()),packet.head(),BlockPos.containing(packet.head()),packet.wagonId(),packet.frame(),packet.reversed()));
        follow(p);
    }
    /** Late tracking sends the same one-shot session; no periodic sleep packet is necessary. */
    
    
    
    
    private static Vec3 pointForDimension(CargoHold hold) { return hold.owner().cargoPose().position(); }
    private StrawMatSleep() {}
public static void followAfterLevel(Level level) {
        java.util.List<LivingEntity> sleepers;
        synchronized(StrawMatSleep.class) {
            var active=level.isClientSide()?CLIENT_SESSIONS:SERVER_SESSIONS;
            if(active.isEmpty())return;
            sleepers=active.keySet().stream().filter(e->e.level()==level).toList();
        }
        for(var sleeper:sleepers) {
            follow(sleeper);
            if(sleeper.level().isClientSide()&&sleeper.isSleeping()) {
                // Keep vanilla consumers of either old-position snapshot in the
                // parent's frame too. Rendering still uses the exact rotated arc.
                Vec3 previous=sleepingPoint(sleeper,0);
                if(previous!=null) {
                    sleeper.xOld=sleeper.xo=previous.x;sleeper.yOld=sleeper.yo=previous.y;sleeper.zOld=sleeper.zo=previous.z;
                }
            }
        }
    }
public static void track(ServerPlayer observer,LivingEntity sleeper) {
        Session s=session(sleeper);if(s==null||observer.connection==null||!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(observer,WagonNetwork.MatSleep.TYPE))return;
        int wagon=s.hold.owner() instanceof WagonEntity w?w.getId():-1;
        BlockPos frame=s.hold.owner() instanceof AssemblyFrameBlockEntity f?f.getBlockPos():BlockPos.ZERO;
        var now=pose(s);var point=now.point(s.localHead());
        com.sange.tm_wagon.network.WagonPackets.sendToPlayer(observer,new WagonNetwork.MatSleep(sleeper.getId(),wagon,frame,s.mat,s.anchor,now.position(),now.yaw(),now.pitch(),now.roll(),point,true,s.reversed));
    }
public static void leave(net.minecraft.world.entity.Entity entity) {
        if(entity instanceof LivingEntity living&&!(living instanceof Player)&&session(living)!=null)finishWake(living);
    }
public static void unload(Level level) {
        synchronized(StrawMatSleep.class) {
            SERVER_SESSIONS.keySet().removeIf(p->p.level()==level);CLIENT_SESSIONS.keySet().removeIf(p->p.level()==level);
        }
    }
}
