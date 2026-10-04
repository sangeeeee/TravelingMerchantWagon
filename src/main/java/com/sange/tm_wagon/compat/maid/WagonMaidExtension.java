package com.sange.tm_wagon.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.mojang.datafixers.util.Pair;
import com.sange.tm_wagon.assembly.WagonContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;

/** Discovered by Little Maid only when that optional mod is installed. */
@LittleMaidExtension
public final class WagonMaidExtension implements ILittleMaid {
    public static final ResourceLocation TASK=ResourceLocation.fromNamespaceAndPath("tm_wagon","wagon_companion");
    public static final ResourceLocation RIDE_TASK=ResourceLocation.fromNamespaceAndPath("tm_wagon","wagon_passenger");
    @Override public void addMaidTask(TaskManager manager) { manager.add(new CompanionTask(false));manager.add(new CompanionTask(true)); }
    @Override public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new IExtraMaidBrain() {
            @Override public List<Pair<Integer,BehaviorControl<? super EntityMaid>>> getCoreBehaviors() {
                return List.of(Pair.of(0,new WagonMaidBehavior()));
            }
        });
    }
    public static boolean selected(EntityMaid maid) { return TASK.equals(maid.getTask().getUid())||ridingTask(maid); }
    public static boolean ridingTask(EntityMaid maid) { return RIDE_TASK.equals(maid.getTask().getUid()); }
    private static final class CompanionTask implements IMaidTask {
        private final boolean stools;
        CompanionTask(boolean stools) { this.stools=stools; }
        @Override public ResourceLocation getUid() { return stools?RIDE_TASK:TASK; }
        @Override public ItemStack getIcon() { return (stools?WagonContent.STOOL.get():WagonContent.MAID_TASK_ICON.get()).getDefaultInstance(); }
        @Override public SoundEvent getAmbientSound(EntityMaid maid) { return TaskManager.getIdleTask().getAmbientSound(maid); }
        @Override public List<Pair<Integer,BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) { return new ArrayList<>(); }
        @Override public boolean enableLookAndRandomWalk(EntityMaid maid) { return false; }
        @Override public boolean workPointTask(EntityMaid maid) { return true; }
    }
}
