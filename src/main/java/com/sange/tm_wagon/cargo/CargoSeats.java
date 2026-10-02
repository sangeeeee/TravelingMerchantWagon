package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.entity.CargoSeatEntity;
import com.sange.tm_wagon.entity.WagonEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Empty stools have no entities. Moving wagons reuse their existing passenger list. */
public final class CargoSeats {
    private final CargoHold hold;
    private final Map<UUID,CargoSeatEntity> anchors=new HashMap<>();
    public CargoSeats(CargoHold hold) { this.hold=hold; }
    public static boolean eligible(LivingEntity rider) {
        return rider.isAlive()&&!rider.isRemoved()&&!rider.isPassenger()&&!rider.isVehicle()
            &&!rider.isSleeping()&&rider.getBbWidth()<=1.0F&&(!(rider instanceof Player p)||!p.isSpectator());
    }
    public boolean occupied(int slot) {
        if(hold.owner() instanceof WagonEntity w)return w.cargoSeatOccupied(slot);
        var entry=hold.entry(slot);var anchor=entry==null?null:anchors.get(entry.id);
        return anchor!=null&&!anchor.isRemoved()&&!anchor.getPassengers().isEmpty();
    }
    public String sit(int slot,LivingEntity rider) {
        var entry=hold.entry(slot);var level=hold.owner().cargoLevel();
        if(level==null||level.isClientSide||!hold.owner().cargoLive()||hold.owner().cargoBusy()
            ||entry==null||entry.kind!=CargoEntry.Kind.STOOL)return "message.tm_wagon.assembly_busy";
        if(occupied(slot))return "message.tm_wagon.seat_occupied";
        if(!eligible(rider)||rider.level()!=level||rider.distanceToSqr(hold.position(entry))>64)return "message.tm_wagon.stool_cannot_sit";
        boolean seated;
        if(hold.owner() instanceof WagonEntity w)seated=w.boardCargoSeat(rider,slot);
        else {
            var seat=WagonContent.CARGO_SEAT.get().create(level);
            if(seat==null)return "message.tm_wagon.spawn_failed";
            seat.initialize(hold,slot,entry.id);
            if(!level.addFreshEntity(seat))return "message.tm_wagon.spawn_failed";
            seated=rider.startRiding(seat);
            if(seated)anchors.put(entry.id,seat);else seat.discard();
        }
        if(!seated)return "message.tm_wagon.stool_cannot_sit";
        if(rider instanceof Mob mob)mob.getNavigation().stop();
        rider.getVehicle().positionRider(rider);return null;
    }
    /** One bounded query every ten ticks, only if a free stool exists; never capture players automatically. */
    public void tick() {
        if(hold.owner().cargoBusy()||hold.owner().cargoLevel().getGameTime()%10!=0)return;
        anchors.values().removeIf(CargoSeatEntity::isRemoved);
        AABB area=null;
        for(int i=0;i<CargoHold.CAPACITY;i++)if(stool(i)&&!occupied(i)) {
            var box=CargoHold.worldBox(CargoHold.stoolBox(i),hold.owner().cargoPose()).inflate(.2,.5,.2);
            area=area==null?box:area.minmax(box);
        }
        if(area==null)return;
        for(var mob:hold.owner().cargoLevel().getEntitiesOfClass(Mob.class,area,CargoSeats::eligible)) {
            if(mob.isLeashed())continue;
            Vec3 local=hold.owner().cargoPose().local(mob.position());
            for(int i=0;i<CargoHold.CAPACITY;i++)if(stool(i)&&!occupied(i)) {
                Vec3 p=CargoHold.centre(i);
                if(Math.abs(local.x-p.x)<=CargoHold.SCALE/2&&Math.abs(local.z-p.z)<=CargoHold.SCALE/2
                    &&local.y>=CargoHold.FLOOR+.5-.1&&local.y<=CargoHold.FLOOR+.5+.3) {
                    sit(i,mob);break;
                }
            }
        }
    }
    private boolean stool(int slot) { return hold.entry(slot)!=null&&hold.entry(slot).kind==CargoEntry.Kind.STOOL; }
    public void release(CargoEntry entry) {
        if(entry!=null&&entry.kind!=CargoEntry.Kind.STOOL)return;
        if(hold.owner().cargoLevel()==null||hold.owner().cargoLevel().isClientSide)return;
        if(hold.owner() instanceof WagonEntity w)w.releaseCargoPassengers(entry==null?-1:hold.slot(entry));
        if(entry==null) {
            for(var seat:java.util.List.copyOf(anchors.values()))seat.release();anchors.clear();
        } else {
            var seat=anchors.remove(entry.id);if(seat!=null)seat.release();
        }
    }
    /** Standing boxes stay upright even on slopes; start above the rotated collision box. */
    public static Vec3 standUp(CargoHold hold,int slot,LivingEntity rider,boolean outside) {
        var pose=hold.owner().cargoPose();
        if(!outside&&slot>=0&&slot<CargoHold.CAPACITY) {
            Vec3 centre=pose.point(CargoHold.centre(slot).add(0,.5,0));
            double top=Math.max(centre.y,CargoHold.worldBox(CargoHold.stoolBox(slot),pose).maxY);
            for(double rise=.01;rise<=3;rise+=.125) {
                Vec3 target=new Vec3(centre.x,top+rise,centre.z);
                if(clear(rider,target))return target;
            }
        }
        if(hold.owner() instanceof WagonEntity w)return w.safeDismount(rider);
        for(int y=0;y<=5;y++)for(double x:new double[]{-2.5,2.5,-3.5,3.5})for(double z:new double[]{0,-2,2}) {
            Vec3 target=pose.point(new Vec3(x,y,z));if(clear(rider,target))return target;
        }
        return pose.position().add(0,6,0);
    }
    private static boolean clear(LivingEntity rider,Vec3 target) {
        var box=rider.getDimensions(Pose.STANDING).makeBoundingBox(target).deflate(.0001);
        return rider.level().getWorldBorder().isWithinBounds(box)&&rider.level().noCollision(rider,box);
    }
}
