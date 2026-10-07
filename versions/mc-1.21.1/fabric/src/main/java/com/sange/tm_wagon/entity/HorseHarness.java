package com.sange.tm_wagon.entity;

import com.sange.tm_wagon.TravelingMerchantWagon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.item.Items;

public final class HorseHarness {
    public static final String OWNER="TMWagon", GRAVITY="TMPreviousNoGravity", OWNER_POS="TMWagonPosition";
    public static boolean eligible(Entity entity) {
        return (entity instanceof Horse || entity instanceof Donkey || entity instanceof Mule || entity instanceof Camel || entity instanceof SkeletonHorse || entity instanceof ZombieHorse)
            && !((AbstractHorse)entity).isBaby() && entity.isAlive() && !entity.isPassenger() && !entity.isVehicle();
    }
    public static boolean attached(AbstractHorse horse) {
        return horse.getLeashHolder() instanceof WagonEntity || (!horse.level().isClientSide && com.sange.tm_wagon.platform.EntityData.of(horse).hasUUID(OWNER));
    }
    public static WagonEntity owner(AbstractHorse horse) {
        if (horse.getLeashHolder() instanceof WagonEntity wagon) return wagon;
        if (horse.level() instanceof ServerLevel server && com.sange.tm_wagon.platform.EntityData.of(horse).hasUUID(OWNER)
            && server.getEntity(com.sange.tm_wagon.platform.EntityData.of(horse).getUUID(OWNER)) instanceof WagonEntity wagon) return wagon;
        return null;
    }
    public static boolean pulling(AbstractHorse horse) {
        WagonEntity wagon=owner(horse);
        return wagon!=null&&wagon.isMoving();
    }
    /** Match a ridden horse's forward head/body alignment without disabling its idle AI. */
    public static void updateDrivingPose(AbstractHorse horse) {
        if(horse instanceof Camel camel && attached(horse) && camel.isCamelSitting())camel.standUpInstantly();
        if(!pulling(horse))return;
        horse.setEating(false);horse.setStanding(false);horse.setXRot(0);
        horse.setYHeadRot(horse.getYRot());horse.setYBodyRot(horse.getYRot());
        horse.yHeadRotO=horse.yBodyRotO=horse.yRotO;
    }
    public static void mark(AbstractHorse horse,WagonEntity wagon) {
        if(horse instanceof Camel camel) { camel.standUpInstantly();camel.setDashing(false); }
        var tag=com.sange.tm_wagon.platform.EntityData.of(horse);tag.putUUID(OWNER,wagon.getUUID());tag.putBoolean(GRAVITY,horse.isNoGravity());
        tag.putLong(OWNER_POS,wagon.blockPosition().asLong());horse.setNoGravity(true);horse.getNavigation().stop();
        horse.dropLeash(true,false);horse.setLeashedTo(wagon,true);
    }
    public static void clear(AbstractHorse horse) {
        var tag=com.sange.tm_wagon.platform.EntityData.of(horse);boolean gravity=tag.getBoolean(GRAVITY);
        horse.dropLeash(true,false);tag.remove(OWNER);tag.remove(GRAVITY);tag.remove(OWNER_POS);tag.remove("TMOrphanTicks");
        horse.setNoGravity(gravity);horse.getNavigation().stop();horse.clearRestriction();
    }
    /** Both interaction paths must be intercepted, including offhand scissors. */
    public static boolean interact(net.minecraft.world.entity.player.Player player,Entity target,net.minecraft.world.InteractionHand hand) {
        if (!(target instanceof AbstractHorse horse) || !attached(horse)) return false;
        if (player.getItemInHand(hand).is(Items.SHEARS) && !horse.level().isClientSide) {
            WagonEntity wagon=owner(horse);if(wagon!=null)wagon.detachHorse(horse.getUUID(),true);
        }
        return true; // Attached horses cannot be mounted or re-leashed independently.
    }
    
    
    public static void death(net.minecraft.world.entity.LivingEntity entity) {
        if(entity instanceof AbstractHorse horse && horse.level() instanceof ServerLevel) {
            var wagon=owner(horse);if(wagon!=null)wagon.detachHorse(horse.getUUID(),true);
        }
    }
    public static void leaving(Entity entity) {
        if(entity instanceof AbstractHorse horse && horse.level() instanceof ServerLevel && horse.getRemovalReason()!=null && horse.getRemovalReason().shouldDestroy()) {
            var wagon=owner(horse);if(wagon!=null)wagon.detachHorse(horse.getUUID(),true);
        }
    }
    public static void joining(Entity entity,net.minecraft.world.level.Level level) {
        if(entity instanceof AbstractHorse horse && level instanceof ServerLevel server
            && com.sange.tm_wagon.platform.EntityData.of(horse).hasUUID(OWNER) && HarnessSavedData.get(server).consume(horse.getUUID())) clear(horse);
    }
    /** Missing unloaded owners are preserved; only a loaded owner region can prove an orphan. */
    public static void recover(AbstractHorse horse) {
        if(!(horse.level() instanceof ServerLevel server) || !com.sange.tm_wagon.platform.EntityData.of(horse).hasUUID(OWNER)) return;
        var tag=com.sange.tm_wagon.platform.EntityData.of(horse);var wagon=owner(horse);
        if(wagon!=null) {
            if(!wagon.hasHorse(horse.getUUID())) clear(horse);
            else if(horse.getLeashHolder()!=wagon)horse.setLeashedTo(wagon,true);
            return;
        }
        if(!server.hasChunkAt(net.minecraft.core.BlockPos.of(tag.getLong(OWNER_POS))))return;
        int ticks=tag.getInt("TMOrphanTicks")+1;tag.putInt("TMOrphanTicks",ticks);
        if(ticks>=100) {
            boolean alreadyRefunded=HarnessSavedData.get(server).consume(horse.getUUID());clear(horse);
            if(!alreadyRefunded)horse.spawnAtLocation(Items.LEAD);
        }
    }
    private HorseHarness() {}
}
