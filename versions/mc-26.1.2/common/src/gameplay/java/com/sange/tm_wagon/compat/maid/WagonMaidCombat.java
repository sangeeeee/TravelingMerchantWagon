package com.sange.tm_wagon.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.api.task.FunctionCallSwitchResult;
import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskBowAttack;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskCrossBowAttack;
import com.mojang.datafixers.util.Pair;
import com.sange.tm_wagon.entity.WagonEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Delegate shooting and targeting rules to Little Maid's native mounted weapon tasks. */
public final class WagonMaidCombat extends WagonMaidExtension.CompanionTask implements IRangedAttackTask {
    private static final IRangedAttackTask BOW=new TaskBowAttack(),CROSSBOW=new TaskCrossBowAttack();
    public WagonMaidCombat() { super(false); }

    private static IRangedAttackTask weapon(ItemStack stack) {
        if(stack.getItem() instanceof BowItem)return BOW;
        if(stack.getItem() instanceof CrossbowItem)return CROSSBOW;
        // This NeoForge target has no supported gun integration.
        return null;
    }
    private static IRangedAttackTask weapon(EntityMaid maid) { return weapon(maid.getMainHandItem()); }
    private static boolean mounted(EntityMaid maid) {
        if(!(maid.getVehicle() instanceof WagonEntity wagon))return false;
        int seat=wagon.passengerSeat(maid);
        return seat>=0&&seat<wagon.seatCapacity()&&seat!=wagon.cargoSeat().driverSeat();
    }
    @Override public boolean enablePanic(EntityMaid maid) { return false; }
    // A weapon is optional: this remains a companion task when its hands are empty.
    @Override public FunctionCallSwitchResult onFunctionCallSwitch(EntityMaid maid) { return FunctionCallSwitchResult.OK; }
    @Override public boolean isWeapon(EntityMaid maid,ItemStack stack) { return weapon(stack)!=null; }
    @Override public float searchRadius(EntityMaid maid) {
        var task=mounted(maid)?weapon(maid):null;return task==null?super.searchRadius(maid):task.searchRadius(maid);
    }
    @Override public AABB searchDimension(EntityMaid maid) {
        var task=mounted(maid)?weapon(maid):null;return task==null?super.searchDimension(maid):task.searchDimension(maid);
    }
    @Override public boolean canSee(EntityMaid maid,LivingEntity target) {
        var task=weapon(maid);return task==null?IRangedAttackTask.super.canSee(maid,target):task.canSee(maid,target);
    }
    @Override public void performRangedAttack(EntityMaid maid,LivingEntity target,float power) {
        var task=weapon(maid);
        if(task!=null&&mounted(maid)&&maid.getScheduleDetail()==Activity.WORK)task.performRangedAttack(maid,target,power);
    }
    @Override public List<Pair<Integer,BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        return new ArrayList<>(List.of(Pair.of(5,new MountedWeapons())));
    }

    /** Each maid owns one active native weapon controller; switching never runs two shooters together. */
    private static final class MountedWeapons extends Behavior<EntityMaid> {
        private IRangedAttackTask active;
        private List<Pair<Integer,BehaviorControl<? super EntityMaid>>> controls=List.of();
        MountedWeapons() { super(Map.of(),1200); }
        @Override protected boolean checkExtraStartConditions(ServerLevel level,EntityMaid maid) {
            return mounted(maid)&&maid.getScheduleDetail()==Activity.WORK&&!maid.isSleeping();
        }
        @Override protected boolean canStillUse(ServerLevel level,EntityMaid maid,long time) {
            return checkExtraStartConditions(level,maid);
        }
        @Override protected void tick(ServerLevel level,EntityMaid maid,long time) {
            var next=weapon(maid);
            if(next!=active) {
                release(level,maid,time);
                active=next;
                if(active!=null)controls=active.createRideBrainTasks(maid);
            }
            for(var pair:controls) {
                var control=pair.getSecond();
                if(control.getStatus()==Status.STOPPED)control.tryStart(level,maid,time);
                if(control.getStatus()==Status.RUNNING)control.tickOrStop(level,maid,time);
            }
        }
        @Override protected void stop(ServerLevel level,EntityMaid maid,long time) { release(level,maid,time); }
        private void release(ServerLevel level,EntityMaid maid,long time) {
            for(var pair:controls)pair.getSecond().doStop(level,maid,time);
            controls=List.of();active=null;
            maid.stopUsingItem();maid.setSwingingArms(false);
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }
}
