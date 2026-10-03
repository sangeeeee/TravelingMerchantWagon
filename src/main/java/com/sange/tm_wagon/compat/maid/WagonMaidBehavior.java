package com.sange.tm_wagon.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sange.tm_wagon.assembly.AssemblyFrameBlockEntity;
import com.sange.tm_wagon.cargo.CargoEntry;
import com.sange.tm_wagon.cargo.CargoHold;
import com.sange.tm_wagon.cargo.StrawMatSleep;
import com.sange.tm_wagon.entity.WagonEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;

/** Per-maid target cache; searches loaded chunks only, at most once every two seconds. */
public final class WagonMaidBehavior extends Behavior<EntityMaid> {
    private record Target(CargoHold hold,int index,UUID mat) {
        boolean bed() { return mat!=null; }
        Vec3 point() {
            return bed()?hold.owner().cargoPose().point(hold.centreAt(index).add(0,.16,-.7))
                :((WagonEntity)hold.owner()).companionSeatPosition(index);
        }
        boolean valid() {
            if(!hold.owner().cargoLive()||hold.owner().cargoBusy())return false;
            if(!bed())return ((WagonEntity)hold.owner()).companionSeatAvailable(index);
            var entry=hold.entry(index);return entry!=null&&entry.id.equals(mat)&&entry.kind==CargoEntry.Kind.STRAW_MAT&&entry.sleeper==null;
        }
    }
    private Target target;
    private long nextTick,nextSearch,expires,nextPath;
    private Vec3 walkingTo;
    private boolean resting;
    public WagonMaidBehavior() { super(Map.of(),1); }
    @Override protected boolean checkExtraStartConditions(ServerLevel level,EntityMaid maid) { return level.getGameTime()>=nextTick; }
    @Override protected void start(ServerLevel level,EntityMaid maid,long time) {
        nextTick=time+10;
        boolean selected=WagonMaidExtension.selected(maid),rest=maid.getScheduleDetail()==Activity.REST;
        StrawMatSleep.checkMobSleep(maid,selected&&rest);
        if(!selected||!maid.isAlive()||maid.isMaidInSittingPose()||maid.isLeashed()||maid.getBrain().isActive(Activity.PANIC)) { clear(maid);return; }
        if(maid.isSleeping()) { clear(maid);return; }
        if(rest!=resting) { clear(maid);resting=rest;nextSearch=0; }
        if(rest&&maid.getVehicle() instanceof WagonEntity) {
            maid.stopRiding();maid.getBrain().setActiveActivityIfPossible(Activity.REST);
        }
        if(!rest&&maid.getScheduleDetail()!=Activity.WORK) { clear(maid);return; }
        if(maid.isPassenger()) { clear(maid);return; }
        if(rest&&(!level.dimensionType().bedWorks()||!level.dimensionType().natural())) { clear(maid);return; }
        if(target!=null&&(!target.valid()||time>=expires||maid.distanceToSqr(target.point())>24*24))clear(maid);
        if(target==null) {
            if(time<nextSearch)return;
            nextSearch=time+40;target=find(level,maid,rest);expires=time+200;
        }
        if(target==null)return;
        Vec3 point=target.point();
        if(close(maid,point)) {
            stopWalking(maid);
            if(rest) {
                maid.getBrain().setActiveActivityIfPossible(Activity.REST);
                StrawMatSleep.sleepMob(target.hold,target.index,maid);
            } else ((WagonEntity)target.hold.owner()).boardCompanion(maid,target.index);
            clear(maid);return;
        }
        if(walkingTo!=null&&time<nextPath&&maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET))return;
        Vec3 approach=approach(maid,target);
        if(approach==null) { clear(maid);return; }
        walkingTo=approach;nextPath=time+40;
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(approach,.6F,0));
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
        var area=maid.getBoundingBox().inflate(16,8,16);var candidates=new ArrayList<Target>();
        for(var wagon:level.getEntitiesOfClass(WagonEntity.class,area,w->!w.isRemoved()&&!w.cargoBusy())) {
            if(rest)addMats(candidates,wagon.cargo());
            else for(int seat=0;seat<wagon.seatCapacity();seat++)if(wagon.companionSeatAvailable(seat))candidates.add(new Target(wagon.cargo(),seat,null));
        }
        if(rest)for(int x=(int)Math.floor(area.minX)>>4;x<=((int)Math.floor(area.maxX)>>4);x++)
            for(int z=(int)Math.floor(area.minZ)>>4;z<=((int)Math.floor(area.maxZ)>>4);z++) {
                var chunk=level.getChunkSource().getChunkNow(x,z);if(chunk==null)continue;
                for(var be:chunk.getBlockEntities().values())if(be instanceof AssemblyFrameBlockEntity frame
                    &&area.contains(Vec3.atCenterOf(frame.getBlockPos())))addMats(candidates,frame.cargo());
            }
        candidates.sort(Comparator.comparingDouble(t->maid.distanceToSqr(t.point())));
        for(var candidate:candidates)if(candidate.valid()&&maid.isWithinRestriction(BlockPos.containing(candidate.point()))
            &&(close(maid,candidate.point())||approach(maid,candidate)!=null))return candidate;
        return null;
    }
    private static void addMats(ArrayList<Target> list,CargoHold hold) {
        if(!hold.owner().cargoLive()||hold.owner().cargoBusy())return;
        for(int slot=0;slot<hold.capacity();slot++) {
            var mat=hold.entry(slot);
            if(mat!=null&&hold.slot(mat)==slot&&mat.kind==CargoEntry.Kind.STRAW_MAT&&mat.sleeper==null)list.add(new Target(hold,slot,mat.id));
        }
    }
    private void clear(EntityMaid maid) { if(target!=null)stopWalking(maid);target=null;walkingTo=null; }
    private static void stopWalking(EntityMaid maid) {
        maid.getNavigation().stop();maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
    }
}
