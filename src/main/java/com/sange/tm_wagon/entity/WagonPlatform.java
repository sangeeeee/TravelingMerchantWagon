package com.sange.tm_wagon.entity;

import com.sange.tm_wagon.physics.WagonPose;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Temporary ground contacts, not passengers. Only a moving wagon performs a nearby query. */
public final class WagonPlatform {
    private static final double CONTACT_EPSILON=.06;
    private static final ThreadLocal<WagonEntity> EXCLUDED=new ThreadLocal<>();
    private final WagonEntity wagon;
    private final Set<Entity> standing=Collections.newSetFromMap(new IdentityHashMap<>());

    WagonPlatform(WagonEntity wagon) { this.wagon=wagon; }
    public static WagonEntity excludedWagon() { return EXCLUDED.get(); }
    private static <T> T ignoringWagon(WagonEntity wagon,java.util.function.Supplier<T> action) {
        WagonEntity prior=EXCLUDED.get();EXCLUDED.set(wagon);
        try { return action.get(); }
        finally { if(prior==null)EXCLUDED.remove();else EXCLUDED.set(prior); }
    }
    static Vec3 allowedMovement(Entity entity,WagonEntity wagon,Vec3 delta) {
        return ignoringWagon(wagon,()->Entity.collideBoundingBox(entity,delta,entity.getBoundingBox(),wagon.level(),
            wagon.level().getEntityCollisions(entity,entity.getBoundingBox().expandTowards(delta))));
    }
    static void moveIgnoringWagon(Entity entity,WagonEntity wagon,Vec3 delta) {
        ignoringWagon(wagon,()->{entity.move(net.minecraft.world.entity.MoverType.SELF,delta);return null;});
    }
    public boolean carries(Entity entity) { return standing.contains(entity); }
    private static boolean upright(WagonPose pose) {
        return Math.abs(pose.pitch())<Math.toRadians(35)&&Math.abs(pose.roll())<Math.toRadians(30);
    }
    private boolean eligible(Entity entity) { return entity.onGround()&&eligibleWithoutGroundFlag(entity); }
    private boolean eligibleWithoutGroundFlag(Entity entity) {
        return entity!=wagon&&!(entity instanceof WagonEntity)&&!entity.isRemoved()&&!entity.isSpectator()
            &&!entity.noPhysics&&!entity.isPassenger()&&entity.getDeltaMovement().y<=.1
            &&(!(entity instanceof Player player)||!player.getAbilities().flying)
            &&(!wagon.level().isClientSide||entity instanceof Player player&&player.isLocalPlayer());
    }
    /** The feet must actually touch a top face; proximity to the large overall bounds is insufficient. */
    private static double surface(AABB feet,List<AABB> boxes,double tolerance) {
        double top=Double.NEGATIVE_INFINITY;
        for(AABB box:boxes)if(box.maxX>feet.minX+1e-5&&box.minX<feet.maxX-1e-5
            &&box.maxZ>feet.minZ+1e-5&&box.minZ<feet.maxZ-1e-5
            &&Math.abs(box.maxY-feet.minY)<=tolerance)top=Math.max(top,box.maxY);
        return top;
    }
    public boolean supports(Entity entity) {
        return !wagon.falling()&&upright(wagon.pose())&&eligibleWithoutGroundFlag(entity)
            &&Double.isFinite(surface(entity.getBoundingBox(),wagon.collisionBoxes(),CONTACT_EPSILON));
    }
    /** Vanilla's flight check only considers blocks; a real entity platform also provides support. */
    public static WagonEntity supportingWagon(Entity entity) {
        for(WagonEntity wagon:WagonSpatialIndex.candidates(entity.level(),entity.getBoundingBox().inflate(CONTACT_EPSILON)))
            if(wagon.platform().supports(entity))return wagon;
        return null;
    }
    public static boolean supportedByWagon(Entity entity) { return supportingWagon(entity)!=null; }
    /** Reproject client-local feet onto the current authoritative platform. Keep vanilla movement validation. */
    public void acceptStandingMovement(ServerPlayer player,Vec3 local,float yaw,float pitch,boolean onGround) {
        if(!onGround||player.level()!=wagon.level()||!eligibleWithoutGroundFlag(player)||wagon.falling()||!upright(wagon.pose())
            ||!Double.isFinite(local.x)||!Double.isFinite(local.y)||!Double.isFinite(local.z)
            ||!Float.isFinite(yaw)||!Float.isFinite(pitch))return;
        Vec3 target=wagon.pose().point(local);
        // A platform packet cannot be used to approach a distant vehicle or bypass normal walking.
        if(target.distanceToSqr(player.position())>1.5*1.5||Math.abs(target.y-player.getY())>.6)return;
        AABB feet=player.getBoundingBox().move(target.subtract(player.position()));
        double top=surface(feet,wagon.collisionBoxes(),.18);
        if(!Double.isFinite(top))return;
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(target.x,top,target.z,yaw,pitch,true));
    }
    public void begin() {
        standing.clear();
        if(wagon.falling()||!upright(wagon.pose()))return;
        List<AABB> boxes=wagon.collisionBoxes();
        for(Entity entity:wagon.level().getEntities(wagon,wagon.getBoundingBox().inflate(CONTACT_EPSILON),this::eligible))
            if(Double.isFinite(surface(entity.getBoundingBox(),boxes,CONTACT_EPSILON)))standing.add(entity);
    }
    public void end() { standing.clear(); }

    private record Transport(Entity entity,Vec3 destination) {}
    /** Called for each existing small physics step, so occupants cannot tunnel through walls/ceilings. */
    public boolean moveTo(WagonPose next) {
        WagonPose previous=wagon.pose();
        if(previous.equals(next))return true;
        if(standing.isEmpty()) { wagon.applyPose(next);return true; }
        if(!upright(next)||wagon.falling()) { standing.clear();wagon.applyPose(next);return true; }
        List<AABB> oldBoxes=wagon.collisionBoxes(),newBoxes=wagon.boxesAt(next);
        List<Transport> moves=new ArrayList<>(standing.size());
        for(var iterator=standing.iterator();iterator.hasNext();) {
            Entity entity=iterator.next();AABB bounds=entity.getBoundingBox();
            if(!eligible(entity)||!Double.isFinite(surface(bounds,oldBoxes,CONTACT_EPSILON))) { iterator.remove();continue; }
            Vec3 destination=next.point(previous.local(entity.position()));
            AABB shifted=bounds.move(destination.subtract(entity.position()));
            // Tilted geometry uses small conservative boxes. Match their actual top faces,
            // rather than sinking feet into a rotated tile's bounds.
            double top=surface(shifted,newBoxes,.18);
            if(!Double.isFinite(top)) { iterator.remove();continue; }
            destination=new Vec3(destination.x,top,destination.z);
            Vec3 delta=destination.subtract(entity.position());
            Vec3 allowed=allowedMovement(entity,wagon,delta);
            boolean clear=allowed.distanceToSqr(delta)<1e-8&&wagon.level().hasChunkAt(BlockPos.containing(destination));
            if(!clear) {
                // The server stops this step before crushing anyone. A client must still
                // accept the authoritative wagon pose, without moving its player into a wall.
                if(!wagon.level().isClientSide)return false;
                iterator.remove();continue;
            }
            moves.add(new Transport(entity,destination));
        }
        wagon.applyPose(next,newBoxes);
        for(Transport move:moves) {
            // Do not add platform displacement to walking velocity or turn it into a ride.
            move.entity.setPos(move.destination);move.entity.resetFallDistance();
            // This displacement was made by the server, not by a movement packet. Keep
            // vanilla's baselines aligned so delayed packets do not apply it a second time.
            if(move.entity instanceof ServerPlayer player)player.connection.resetPosition();
            // Items normally update much less often than vehicles. Ask the existing tracker
            // to send their changed position this tick, rather than double-carrying remote entities.
            if(!wagon.level().isClientSide)move.entity.hasImpulse=true;
        }
        return true;
    }
}
