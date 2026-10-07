package com.sange.tm_wagon.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.cargo.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Real optional maid API and native brain smoke; excluded from published artifacts. */
public final class MaidPortSmoke {
    private static EntityMaid maid;
    private static WagonEntity wagon;
    private static int stage;
    private static long due;
    private static void require(boolean b,String message) { if(!b)throw new IllegalStateException(message); }
    public static void prepare(ServerPlayer player) {
        var level=player.level();
        var companion=TaskManager.findTask(WagonMaidExtension.TASK).orElseThrow();
        TaskManager.findTask(WagonMaidExtension.RIDE_TASK).orElseThrow();
        wagon=WagonContent.WAGON.get().create(level,net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        var parts=WagonEntity.defaultParts();parts.put(WagonSlot.SEAT,WagonPart.DOUBLE_WOODEN_SEAT);
        wagon.configure(parts,Direction.NORTH);wagon.setPos(player.position().add(0,0,30));wagon.setNoGravity(true);level.addFreshEntity(wagon);
        maid=new EntityMaid(level);maid.tame(player);maid.setHomeModeEnable(true);maid.getSchedulePos().setHomeModeEnable(maid,net.minecraft.core.BlockPos.containing(wagon.position()));maid.setSchedule(MaidSchedule.DAY);maid.setTask(companion);
        maid.setPos(wagon.companionSeatPosition(1));level.addFreshEntity(maid);
        stage=1;due=level.getGameTime()+45;
    }
    public static void tick(MinecraftServer server) {
        if(stage==0||server.overworld().getGameTime()<due)return;
        if(stage==1) {
            require(maid.getVehicle()==wagon&&wagon.passengerSeat(maid)==1,"Maid companion AI boards non-driver seat");
            maid.setTask(TaskManager.getIdleTask());require(!maid.isPassenger(),"Changing native task immediately dismounts");
            var owner=server.getPlayerList().getPlayers().getFirst();owner.setPos(wagon.pose().point(new Vec3(0,3,0)));
            require(wagon.cargo().place(0,new ItemStack(WagonContent.STOOL.get()),owner)==null,"Maid stool installation");
            maid.setTask(TaskManager.findTask(WagonMaidExtension.RIDE_TASK).orElseThrow());maid.setPos(wagon.pose().point(wagon.cargo().centreAt(0).add(0,.55,0)));
            stage=2;due=server.overworld().getGameTime()+80;
        } else if(stage==2) {
            require(wagon.cargo().seats.occupied(0)&&maid.isPassenger(),"Maid passenger task finds wagon stool: "+maid.getTask().getUid()+" at "+maid.position()+" vehicle "+maid.getVehicle()+" schedule "+maid.getScheduleDetail()+" available "+wagon.cargo().seats.available(0,maid));
            maid.setTask(TaskManager.getIdleTask());require(!maid.isPassenger(),"Changing stool task immediately dismounts");
            var player=server.getPlayerList().getPlayers().getFirst();
            wagon.cargo().load(new net.minecraft.nbt.CompoundTag(),server.registryAccess());
            player.setPos(wagon.pose().point(new Vec3(0,3,4)));
            require(wagon.cargo().place(8,new ItemStack(WagonContent.STRAW_MAT.get()),player)==null,"Maid mat installation");
            maid.setPos(wagon.cargo().position(wagon.cargo().entry(8)));
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 18000");
            stage=3;due=server.overworld().getGameTime()+65;
        } else {
            require(maid.isSleeping()&&StrawMatSleep.sleepingPoint(maid)!=null,"Idle task maid searches straw mat at rest time: "+maid.getScheduleDetail()+" at "+maid.position()+" home "+maid.getHomePosition()+" mat "+wagon.cargo().position(wagon.cargo().entry(8))+" session "+StrawMatSleep.matSleeper(maid));
            for(int i=0;i<20;i++) { wagon.setPos(wagon.position().add(.12,0,.06));wagon.setYRot(wagon.getYRot()+3);StrawMatSleep.follow(maid);
                require(maid.isSleeping()&&maid.position().distanceToSqr(StrawMatSleep.sleepingPoint(maid))<.000001,"Sleeping maid follows moving and turning wagon"); }
            maid.stopSleeping();require(!maid.isSleeping(),"Maid wakes normally");maid.discard();
            var player=server.getPlayerList().getPlayers().getFirst();player.setPos(wagon.pose().point(new Vec3(0,3,0)));
            require(wagon.cargo().place(0,new ItemStack(WagonContent.STOOL.get()),player)==null,"Forced maid stool installation");
            var idleMaid=new EntityMaid(server.overworld());idleMaid.tame(player);idleMaid.setPos(wagon.pose().point(wagon.cargo().centreAt(0).add(0,.5,0)));server.overworld().addFreshEntity(idleMaid);
            require(wagon.cargo().seats.sit(0,idleMaid)==null&&!idleMaid.isPassenger(),"Non-passenger task maid immediately leaves a forced stool seat");
            idleMaid.discard();wagon.discard();stage=0;
            com.mojang.logging.LogUtils.getLogger().info("TM_WAGON_26_1_2_MAID_PASS: native tasks, immediate dismount, all-task rest search, moving sleep and wake");
        }
    }
}
