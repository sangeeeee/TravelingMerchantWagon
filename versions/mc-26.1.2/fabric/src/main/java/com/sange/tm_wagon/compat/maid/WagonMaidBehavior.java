package com.sange.tm_wagon.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBrains;
import com.github.tartaricacid.touhoulittlemaid.init.InitPoi;
import com.sange.tm_wagon.assembly.AssemblyFrameBlockEntity;
import com.sange.tm_wagon.cargo.CargoEntry;
import com.sange.tm_wagon.cargo.CargoHold;
import com.sange.tm_wagon.cargo.StrawMatSleep;
import com.sange.tm_wagon.entity.WagonEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;

/** Per-maid target cache; searches loaded chunks only, at most once every two seconds. */
public final class WagonMaidBehavior extends Behavior<EntityMaid> {
    private record Target(CargoHold hold,int index,UUID mat,boolean stool) {
        boolean bed() { return mat!=null; }
        Vec3 point() {
            return bed()?hold.owner().cargoPose().point(hold.centreAt(index).add(0,.16,(hold.entry(index).reversed?1:-1)*.7))
                :stool?hold.owner().cargoPose().point(hold.centreAt(index).add(0,.5,0))
                :((WagonEntity)hold.owner()).companionSeatPosition(index);
        }
        boolean valid(EntityMaid maid) {
            if(!hold.owner().cargoLive()||hold.owner().cargoBusy())return false;
            if(stool)return hold.seats.available(index,maid);
            if(!bed())return ((WagonEntity)hold.owner()).companionSeatAvailable(index)&&((WagonEntity)hold.owner()).seatClear(maid,index);
            var entry=hold.entry(index);return entry!=null&&entry.id.equals(mat)&&entry.kind==CargoEntry.Kind.STRAW_MAT&&entry.sleeper==null;
        }
    }
    private Target target;
    // Only server-side maids approaching a mat are present. Native bed AI must not
    // replace that path during the brief gaps in WALK_TARGET while arriving.
    private static final Map<EntityMaid,Target> MAT_TARGETS=new WeakHashMap<>();
    private long nextTick,nextSearch,expires,nextPath;
    private Vec3 walkingTo;
    private boolean resting,stoolTask;
    public WagonMaidBehavior() { super(Map.of(),1); }
    @Override protected boolean checkExtraStartConditions(ServerLevel level,EntityMaid maid) { return level.getGameTime()>=nextTick; }
    @Override protected void start(ServerLevel level,EntityMaid maid,long time) {
        nextTick=time+10;
        boolean selected=WagonMaidExtension.selected(maid),rest=maid.getScheduleDetail()==Activity.REST;
        StrawMatSleep.checkMobSleep(maid,rest);
        if(maid.getVehicle() instanceof WagonEntity w) {
            if(w.passengerSeat(maid)>=WagonEntity.CARGO_SEAT_BASE?!WagonMaidExtension.ridingTask(maid):!selected)leaveCompanionSeat(maid);
        } else if(maid.getVehicle() instanceof com.sange.tm_wagon.entity.CargoSeatEntity&&!WagonMaidExtension.ridingTask(maid))maid.stopRiding();
        if((!selected&&!rest)||!maid.isAlive()||maid.isMaidInSittingPose()||maid.isLeashed()||maid.getBrain().isActive(Activity.PANIC)) { clear(maid);return; }
        if(maid.isSleeping()) { clear(maid);return; }
        if(rest!=resting||stoolTask!=WagonMaidExtension.ridingTask(maid)) { clear(maid);resting=rest;stoolTask=WagonMaidExtension.ridingTask(maid);nextSearch=0; }
        if(rest&&(maid.getVehicle() instanceof WagonEntity||maid.getVehicle() instanceof com.sange.tm_wagon.entity.CargoSeatEntity)) {
            maid.stopRiding();maid.getBrain().setActiveActivityIfPossible(Activity.REST);
        }
        if(!rest&&maid.getScheduleDetail()!=Activity.WORK) { clear(maid);return; }
        if(maid.isPassenger()) { clear(maid);return; }
        if(rest&&!level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.BED_RULE,maid.position()).canSleep().equals(net.minecraft.world.attribute.BedRule.Rule.WHEN_DARK)) { clear(maid);return; }
        if(target!=null&&(!target.valid(maid)||time>=expires||maid.distanceToSqr(target.point())>24*24))clear(maid);
        if(target==null) {
            if(time<nextSearch)return;
            nextSearch=time+40;target=find(level,maid,rest);expires=time+(rest?40:200);
            if(target!=null&&rest) {
                stopWalking(maid);MAT_TARGETS.put(maid,target);
                maid.getBrain().eraseMemory(InitBrains.TARGET_POS);
            }
        }
        if(target==null)return;
        Vec3 point=target.point();
        if(close(maid,point)) {
            stopWalking(maid);
            if(rest) {
                maid.getBrain().setActiveActivityIfPossible(Activity.REST);
                StrawMatSleep.sleepMob(target.hold,target.index,maid);
            } else if(target.stool)target.hold.seats.sit(target.index,maid);
            else ((WagonEntity)target.hold.owner()).boardCompanion(maid,target.index);
            clear(maid);return;
        }
        if(walkingTo!=null&&time<nextPath&&maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET))return;
        Vec3 approach=approach(maid,target);
        if(approach==null) { clear(maid);return; }
        walkingTo=approach;nextPath=time+40;
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(approach,.6F,0));
    }
    /** Called immediately by setTask, and by the core AI for restored passengers. */
    public static void leaveCompanionSeat(EntityMaid maid) {
        if(maid.getVehicle() instanceof WagonEntity wagon) {
            int seat=wagon.passengerSeat(maid);
            if(seat>=0)maid.stopRiding();
        } else if(maid.getVehicle() instanceof com.sange.tm_wagon.entity.CargoSeatEntity)maid.stopRiding();
    }
    public static boolean approachingMat(EntityMaid maid) {
        Target mat=MAT_TARGETS.get(maid);
        return mat!=null&&mat.valid(maid)&&maid.getScheduleDetail()==Activity.REST;
    }
    private static boolean close(EntityMaid maid,Vec3 point) {
        return Math.hypot(maid.getX()-point.x,maid.getZ()-point.z)<=3.25&&Math.abs(maid.getY()-point.y)<=3;
    }
    private static Vec3 approach(EntityMaid maid,Target target) {
        var pose=target.hold.owner().cargoPose();Vec3 local=pose.local(target.point());
        double side=pose.local(maid.position()).x<0?-1:1;
        for(double sign:new double[]{side,-side}) {
            Vec3 point=pose.point(new Vec3(sign*(target.hold.owner().cargoBody().widthScale()+1.25),0,local.z));
            var path=maid.getNavigation().createPath(BlockPos.containing(point),1);
            if(path!=null&&path.canReach())return point;
        }
        return null;
    }
    private static Target find(ServerLevel level,EntityMaid maid,boolean rest) {
        BlockPos search=maid.getBrainSearchPos();int range=(int)maid.getHomeRadius();
        var area=rest?new net.minecraft.world.phys.AABB(search).inflate(range):maid.getBoundingBox().inflate(16,8,16);
        var candidates=new ArrayList<Target>();
        for(var wagon:level.getEntitiesOfClass(WagonEntity.class,area,w->!w.isRemoved()&&!w.cargoBusy())) {
            if(rest)addMats(candidates,wagon.cargo());
            else if(WagonMaidExtension.ridingTask(maid)) {
                for(int slot=0;slot<wagon.cargo().capacity();slot++) {
                    var entry=wagon.cargo().entry(slot);
                    if(entry!=null&&entry.kind==CargoEntry.Kind.STOOL&&!wagon.cargo().seats.occupied(slot))
                        candidates.add(new Target(wagon.cargo(),slot,null,true));
                }
            } else for(int seat=0;seat<wagon.seatCapacity();seat++)if(wagon.companionSeatAvailable(seat))candidates.add(new Target(wagon.cargo(),seat,null,false));
        }
        if(rest)for(int x=(int)Math.floor(area.minX)>>4;x<=((int)Math.floor(area.maxX)>>4);x++)
            for(int z=(int)Math.floor(area.minZ)>>4;z<=((int)Math.floor(area.maxZ)>>4);z++) {
                var chunk=level.getChunkSource().getChunkNow(x,z);if(chunk==null)continue;
                for(var be:chunk.getBlockEntities().values())if(be instanceof AssemblyFrameBlockEntity frame
                    &&area.contains(Vec3.atCenterOf(frame.getBlockPos())))addMats(candidates,frame.cargo());
            }
        // Match MaidBedTask: use its search centre/radius and squared block-position
        // distance. Native beds win ties and retain all original occupancy/AI rules.
        double nativeDistance=rest?level.getPoiManager().getInRange(type->type.value().equals(InitPoi.MAID_BED),
            search,range,PoiManager.Occupancy.ANY).map(PoiRecord::getPos)
            .mapToDouble(pos->pos.distSqr(maid.blockPosition())).min().orElse(Double.POSITIVE_INFINITY):Double.POSITIVE_INFINITY;
        candidates.sort(Comparator.comparingDouble(t->rest?BlockPos.containing(t.point()).distSqr(maid.blockPosition()):maid.distanceToSqr(t.point())));
        for(var candidate:candidates) {
            BlockPos pos=BlockPos.containing(candidate.point());
            if(rest&&pos.distSqr(maid.blockPosition())>=nativeDistance)return null;
            if(rest&&pos.distSqr(search)>range*range)continue;
            if(candidate.valid(maid)&&maid.isWithinHome(pos)
                &&(close(maid,candidate.point())||approach(maid,candidate)!=null))return candidate;
        }
        return null;
    }
    private static void addMats(ArrayList<Target> list,CargoHold hold) {
        if(!hold.owner().cargoLive()||hold.owner().cargoBusy())return;
        for(int slot=0;slot<hold.capacity();slot++) {
            var mat=hold.entry(slot);
            if(mat!=null&&hold.slot(mat)==slot&&mat.kind==CargoEntry.Kind.STRAW_MAT&&mat.sleeper==null)list.add(new Target(hold,slot,mat.id,false));
        }
    }
    private void clear(EntityMaid maid) { if(target!=null)stopWalking(maid);MAT_TARGETS.remove(maid);target=null;walkingTo=null; }
    private static void stopWalking(EntityMaid maid) {
        maid.getNavigation().stop();maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
    }
}
