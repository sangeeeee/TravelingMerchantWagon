package com.sange.tm_wagon.entity;

import com.sange.tm_wagon.TravelingMerchantWagon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid=TravelingMerchantWagon.MODID)
public final class HorseHarness {
    public static final String OWNER="TMWagon", GRAVITY="TMPreviousNoGravity", OWNER_POS="TMWagonPosition";
    public static boolean eligible(Entity entity) {
        return (entity instanceof Horse || entity instanceof Donkey || entity instanceof Mule || entity instanceof SkeletonHorse || entity instanceof ZombieHorse)
            && !((AbstractHorse)entity).isBaby() && entity.isAlive() && !entity.isPassenger() && !entity.isVehicle();
    }
    public static boolean attached(AbstractHorse horse) {
        return horse.getLeashHolder() instanceof WagonEntity || (!horse.level().isClientSide && horse.getPersistentData().hasUUID(OWNER));
    }
    public static WagonEntity owner(AbstractHorse horse) {
        if (horse.getLeashHolder() instanceof WagonEntity wagon) return wagon;
        if (horse.level() instanceof ServerLevel server && horse.getPersistentData().hasUUID(OWNER)
            && server.getEntity(horse.getPersistentData().getUUID(OWNER)) instanceof WagonEntity wagon) return wagon;
        return null;
    }
    public static boolean pulling(AbstractHorse horse) {
        WagonEntity wagon=owner(horse);
        return wagon!=null&&wagon.isMoving();
    }
    /** Match a ridden horse's forward head/body alignment without disabling its idle AI. */
    public static void updateDrivingPose(AbstractHorse horse) {
        if(!pulling(horse))return;
        horse.setEating(false);horse.setStanding(false);horse.setXRot(0);
        horse.setYHeadRot(horse.getYRot());horse.setYBodyRot(horse.getYRot());
        horse.yHeadRotO=horse.yBodyRotO=horse.yRotO;
    }
    public static void mark(AbstractHorse horse,WagonEntity wagon) {
        var tag=horse.getPersistentData();tag.putUUID(OWNER,wagon.getUUID());tag.putBoolean(GRAVITY,horse.isNoGravity());
        tag.putLong(OWNER_POS,wagon.blockPosition().asLong());horse.setNoGravity(true);horse.getNavigation().stop();
        horse.dropLeash(true,false);horse.setLeashedTo(wagon,true);
    }
    public static void clear(AbstractHorse horse) {
        var tag=horse.getPersistentData();boolean gravity=tag.getBoolean(GRAVITY);
        horse.dropLeash(true,false);tag.remove(OWNER);tag.remove(GRAVITY);tag.remove(OWNER_POS);tag.remove("TMOrphanTicks");
        horse.setNoGravity(gravity);horse.getNavigation().stop();horse.clearRestriction();
    }
    /** Both interaction paths must be intercepted, including offhand scissors. */
    private static boolean interact(net.minecraft.world.entity.player.Player player,Entity target,net.minecraft.world.InteractionHand hand) {
        if (!(target instanceof AbstractHorse horse) || !attached(horse)) return false;
        if (player.getItemInHand(hand).is(Items.SHEARS) && !horse.level().isClientSide) {
            WagonEntity wagon=owner(horse);if(wagon!=null)wagon.detachHorse(horse.getUUID(),true);
        }
        return true; // Attached horses cannot be mounted or re-leashed independently.
    }
    @SubscribeEvent public static void specific(PlayerInteractEvent.EntityInteractSpecific event) {
        if(interact(event.getEntity(),event.getTarget(),event.getHand())) { event.setCanceled(true);event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide)); }
    }
    @SubscribeEvent public static void general(PlayerInteractEvent.EntityInteract event) {
        if(interact(event.getEntity(),event.getTarget(),event.getHand())) { event.setCanceled(true);event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide)); }
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if(event.getEntity() instanceof AbstractHorse horse && horse.level() instanceof ServerLevel) {
            var wagon=owner(horse);if(wagon!=null)wagon.detachHorse(horse.getUUID(),true);
        }
    }
    @SubscribeEvent public static void leaving(EntityLeaveLevelEvent event) {
        if(event.getEntity() instanceof AbstractHorse horse && horse.level() instanceof ServerLevel && horse.getRemovalReason()!=null && horse.getRemovalReason().shouldDestroy()) {
            var wagon=owner(horse);if(wagon!=null)wagon.detachHorse(horse.getUUID(),true);
        }
    }
    @SubscribeEvent public static void joining(EntityJoinLevelEvent event) {
        if(event.getEntity() instanceof AbstractHorse horse && event.getLevel() instanceof ServerLevel server
            && horse.getPersistentData().hasUUID(OWNER) && HarnessSavedData.get(server).consume(horse.getUUID())) clear(horse);
    }
    /** Missing unloaded owners are preserved; only a loaded owner region can prove an orphan. */
    public static void recover(AbstractHorse horse) {
        if(!(horse.level() instanceof ServerLevel server) || !horse.getPersistentData().hasUUID(OWNER)) return;
        var tag=horse.getPersistentData();var wagon=owner(horse);
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
