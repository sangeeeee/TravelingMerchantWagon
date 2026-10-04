package com.sange.tm_wagon.entity;

import com.sange.tm_wagon.cargo.CargoEntry;
import com.sange.tm_wagon.cargo.CargoHold;
import com.sange.tm_wagon.cargo.CargoSeats;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Temporary invisible seat for a stationary block-form stool; not saved and has no collider. */
public final class CargoSeatEntity extends Entity {
    private CargoHold hold;
    private int slot;
    private UUID entryId;
    private boolean releasing;
    public CargoSeatEntity(EntityType<? extends CargoSeatEntity> type,Level level) { super(type,level);setNoGravity(true); }
    public void initialize(CargoHold hold,int slot,UUID id) {
        this.hold=hold;this.slot=slot;entryId=id;
        setPos(hold.owner().cargoPose().point(hold.centreAt(slot).add(0,.5,0)));
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected boolean canAddPassenger(Entity passenger) { return hold!=null&&!hold.cover().covered(slot)&&passenger instanceof LivingEntity&&getPassengers().isEmpty()
        &&com.sange.tm_wagon.cargo.SeatClearance.clear(hold,passenger,this,position()); }
    @Override public Vec3 getPassengerRidingPosition(Entity passenger) { return position(); }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return hold==null?position().add(0,.01,0):CargoSeats.standUp(hold,slot,passenger,false);
    }
    @Override public void tick() {
        super.tick();
        if(!level().isClientSide) {
            var entry=hold==null?null:hold.entry(slot);
            if(hold==null||!hold.owner().cargoLive()||hold.owner().cargoBusy()||entry==null
                ||entry.kind!=CargoEntry.Kind.STOOL||!entry.id.equals(entryId)||getPassengers().isEmpty())release();
        }
    }
    public void release() {
        releasing=true;
        for(var rider:java.util.List.copyOf(getPassengers())) {
            Vec3 target=rider instanceof LivingEntity living&&hold!=null?CargoSeats.standUp(hold,slot,living,true):position().add(0,1,0);
            rider.stopRiding();rider.setPose(Pose.STANDING);rider.teleportTo(target.x,target.y,target.z);rider.setDeltaMovement(Vec3.ZERO);rider.fallDistance=0;
        }
        discard();
    }
    @Override protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if(!level().isClientSide&&getPassengers().isEmpty()&&!isRemoved()&&!releasing)discard();
    }
    @Override public void remove(RemovalReason reason) {
        releasing=true;
        if(!level().isClientSide&&!getPassengers().isEmpty()) {
            for(var rider:java.util.List.copyOf(getPassengers())) {
                Vec3 target=rider instanceof LivingEntity living&&hold!=null?CargoSeats.standUp(hold,slot,living,true):position().add(0,1,0);
                rider.stopRiding();rider.setPose(Pose.STANDING);rider.teleportTo(target.x,target.y,target.z);rider.setDeltaMovement(Vec3.ZERO);rider.fallDistance=0;
            }
        }
        super.remove(reason);
    }
}
