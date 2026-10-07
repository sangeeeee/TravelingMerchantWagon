package com.sange.tm_wagon.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.item.AbstractStoreMaidItem;
import com.sange.tm_wagon.cargo.CargoHold;
import com.sange.tm_wagon.cargo.StrawMatSleep;
import com.sange.tm_wagon.compat.StructureCollision;
import com.sange.tm_wagon.entity.CargoSeatEntity;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Only wagon interactions are adapted. Native item components and conversion events are preserved. */
public final class WagonMaidItems {
    public static boolean automaticStool(LivingEntity rider) {
        return !(rider instanceof EntityMaid maid)||WagonMaidExtension.ridingTask(maid);
    }
    public static void afterStoolMount(LivingEntity rider) {
        if(!automaticStool(rider))rider.stopRiding();
    }
    private static boolean onWagon(EntityMaid maid) {
        return maid.getVehicle() instanceof WagonEntity||maid.getVehicle() instanceof CargoSeatEntity||StrawMatSleep.matSleeper(maid);
    }
    private static boolean captureItem(ItemStack stack) {
        return stack.is(InitItems.SMART_SLAB_EMPTY.get())||stack.is(InitItems.CAMERA.get());
    }
    public static InteractionResult interact(CargoHold hold,Player player,InteractionHand hand,Vec3 local) {
        var stack=player.getItemInHand(hand);
        if(captureItem(stack)) {
            Vec3 start=player.getEyePosition(),end=start.add(player.getLookAngle().scale(Math.max(player.entityInteractionRange(),player.blockInteractionRange())));
            double best=start.distanceToSqr(hold.owner().cargoPose().point(local))+.001;EntityMaid target=null;
            for(var maid:player.level().getEntitiesOfClass(EntityMaid.class,new AABB(start,end).inflate(3),WagonMaidItems::onWagon)) {
                var hit=StrawMatSleep.matSleeper(maid)?StrawMatSleep.pickSleeper(maid,start,end):maid.getBoundingBox().clip(start,end);
                if(hit.isPresent()&&start.distanceToSqr(hit.get())<best) {
                    var block=player.level().clip(new ClipContext(start,hit.get(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
                    if(block.getType()!=HitResult.Type.MISS&&start.distanceToSqr(block.getLocation())+.001<start.distanceToSqr(hit.get()))continue;
                    best=start.distanceToSqr(hit.get());target=maid;
                }
            }
            if(target!=null)return capture(player,target,hand);
        }
        boolean photo=stack.is(InitItems.PHOTO.get()),slab=stack.is(InitItems.SMART_SLAB_HAS_MAID.get());
        if((!photo&&!slab)||!stack.has(InitDataComponent.MAID_INFO))return InteractionResult.PASS;
        if(player.level().isClientSide())return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer server)||!hold.owner().cargoLive()||hold.owner().cargoBusy()||player.isSpectator()
            ||player.getCooldowns().isOnCooldown(stack))return InteractionResult.FAIL;
        Vec3 point=hold.owner().cargoPose().point(local);
        if(player.getEyePosition().distanceToSqr(point)>Math.pow(Math.max(player.entityInteractionRange(),player.blockInteractionRange())+.1,2)
            ||!player.level().mayInteract(player,BlockPos.containing(point)))return InteractionResult.FAIL;
        var tag=stack.get(InitDataComponent.MAID_INFO).copyTag();
        if(!player.getUUID().equals(tag.read(net.minecraft.core.UUIDUtil.CODEC.fieldOf("Owner")).orElse(null))) {
            player.sendOverlayMessage(Component.translatable("tooltips.touhou_little_maid.smart_slab.not_your_maid"));return InteractionResult.FAIL;
        }
        var maid=EntityMaid.TYPE.create(player.level(),net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);if(maid==null)return InteractionResult.FAIL;
        tag.remove("SleepingX");tag.remove("SleepingY");tag.remove("SleepingZ");
        com.github.tartaricacid.touhoulittlemaid.util.MaidItemStorageHelper.loadMaid(stack,maid,tag);maid.setPose(Pose.STANDING);maid.setSilent(false);maid.setDeltaMovement(Vec3.ZERO);maid.fallDistance=0;
        // Check duplicate UUIDs before committing the item, including other dimensions.
        for(var level:server.level().getServer().getAllLevels())if(level.getEntity(maid.getUUID())!=null)return InteractionResult.FAIL;
        Vec3 placement=null;
        for(double rise=.002;rise<=.502;rise+=.05) {
            Vec3 at=point.add(0,rise,0);var box=maid.getDimensions(Pose.STANDING).makeBoundingBox(at).deflate(.0001);
            if(player.level().getWorldBorder().isWithinBounds(box)&&player.level().noCollision(maid,box)&&StructureCollision.clear(player.level(),box)) {
                placement=at;break;
            }
        }
        if(placement==null) {
            player.sendOverlayMessage(Component.translatable("message.touhou_little_maid.photo.not_suitable_for_place_maid"));return InteractionResult.FAIL;
        }
        maid.snapTo(placement.x,placement.y,placement.z,player.getYRot(),0);
        if(!server.level().addFreshEntity(maid))return InteractionResult.FAIL;
        // Only the successful world insertion consumes the source, even in creative mode.
        if(photo)stack.shrink(1);
        else {
            player.setItemInHand(hand,InitItems.SMART_SLAB_EMPTY.get().getDefaultInstance());
            player.getCooldowns().addCooldown(InitItems.SMART_SLAB_EMPTY.get().getDefaultInstance(),20);
        }
        maid.spawnExplosionParticle();maid.playSound(SoundEvents.PLAYER_SPLASH,1,.9F+player.getRandom().nextFloat()*.1F);
        return InteractionResult.SUCCESS;
    }
    public static InteractionResult capture(Player player,Entity target,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(hand!=InteractionHand.MAIN_HAND||!(target instanceof EntityMaid maid)||!onWagon(maid)||!captureItem(stack))return InteractionResult.PASS;
        if(player.level().isClientSide())return InteractionResult.SUCCESS;
        if(!maid.isAlive()||!maid.isOwnedBy(player)||player.isSpectator()||player.getCooldowns().isOnCooldown(stack))return InteractionResult.FAIL;
        // A mat has a longer visual body than its vanilla sleeping AABB.
        if(player.distanceToSqr(maid)>Math.pow(player.entityInteractionRange()+3,2))return InteractionResult.FAIL;
        boolean camera=stack.is(InitItems.CAMERA.get());
        if(camera&&maid.isSleeping())return InteractionResult.SUCCESS;
        if(maid.isSleeping())maid.stopSleeping();
        maid.stopRiding();maid.setHomeModeEnable(false);
        var stored=(camera?InitItems.PHOTO.get():InitItems.SMART_SLAB_HAS_MAID.get()).getDefaultInstance();
        com.github.tartaricacid.touhoulittlemaid.util.MaidItemStorageHelper.saveMaid(stored,maid,t->{});
        maid.spawnExplosionParticle();maid.discard();
        if(camera) {
            player.getInventory().placeItemBackInInventory(stored,false);player.getCooldowns().addCooldown(stack,20);
            stack.hurtAndBreak(1,player,hand==InteractionHand.MAIN_HAND?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND);
            player.playSound(InitSounds.CAMERA_USE.get(),1,1);
            if(player instanceof ServerPlayer server)InitTrigger.MAID_EVENT.get().trigger(server,com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType.PHOTO_MAID);
        } else {
            player.setItemInHand(hand,stored);player.getCooldowns().addCooldown(stored,20);
            player.playSound(SoundEvents.PLAYER_SPLASH,1,1);
        }
        return InteractionResult.SUCCESS;
    }
    private WagonMaidItems() {}
}
