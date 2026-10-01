package com.sange.tm_wagon.entity;

import com.sange.tm_wagon.assembly.*;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** One tracked entity, with cached compound collision instead of ticking collider entities. */
public class WagonEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<CompoundTag> MODULES = SynchedEntityData.defineId(WagonEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(WagonEntity.class,EntityDataSerializers.INT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private VoxelShape localShape;
    private VoxelShape worldShape;
    private Vec3 shapePosition;
    private Map<WagonSlot,WagonPart> modules;
    private net.minecraft.core.BlockPos assemblyLock;

    public WagonEntity(EntityType<? extends WagonEntity> type, Level level) {
        super(type,level); blocksBuilding = true; setNoGravity(true); rebuildGeometry();
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODULES,encode(defaultParts())); builder.define(FACING,Direction.NORTH.get2DDataValue());
    }
    public static Map<WagonSlot,WagonPart> defaultParts() {
        var parts = new EnumMap<WagonSlot,WagonPart>(WagonSlot.class);
        parts.put(WagonSlot.BODY,WagonPart.CARGO_BODY); parts.put(WagonSlot.SEAT,WagonPart.SINGLE_SEAT);
        parts.put(WagonSlot.SHAFTS,WagonPart.SINGLE_HORSE_SHAFTS);
        parts.put(WagonSlot.FRONT_LEFT,WagonPart.SMALL_WHEEL); parts.put(WagonSlot.FRONT_RIGHT,WagonPart.SMALL_WHEEL);
        parts.put(WagonSlot.REAR_LEFT,WagonPart.LARGE_WHEEL); parts.put(WagonSlot.REAR_RIGHT,WagonPart.LARGE_WHEEL);
        return parts;
    }
    public static CompoundTag encode(Map<WagonSlot,WagonPart> parts) {
        CompoundTag tag = new CompoundTag(); parts.forEach((slot,part) -> tag.putString(slot.name(),part.name())); return tag;
    }
    public static Map<WagonSlot,WagonPart> decode(CompoundTag tag) {
        var parts = new EnumMap<WagonSlot,WagonPart>(WagonSlot.class);
        for (WagonSlot slot : WagonSlot.values()) if (tag.contains(slot.name())) {
            try { var part=WagonPart.valueOf(tag.getString(slot.name())); if (slot.accepts(part)) parts.put(slot,part); }
            catch (IllegalArgumentException ignored) {}
        }
        return Map.copyOf(parts);
    }
    public void configure(Map<WagonSlot,WagonPart> parts, Direction direction) {
        entityData.set(MODULES,encode(parts)); entityData.set(FACING,direction.get2DDataValue()); rebuildGeometry();
        setYRot(direction.toYRot()); yRotO=getYRot();
    }
    public Map<WagonSlot,WagonPart> parts() { return modules; }
    public Direction facing() { return Direction.from2DDataValue(entityData.get(FACING)); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key.equals(MODULES) || key.equals(FACING)) rebuildGeometry();
    }
    private void rebuildGeometry() {
        modules=decode(entityData.get(MODULES)); localShape=WagonGeometry.entityShape(modules,facing()); worldShape=null;
        setBoundingBox(makeBoundingBox());
        if (level()!=null) WagonSpatialIndex.update(this);
    }
    @Override public void setPos(double x,double y,double z) {
        super.setPos(x,y,z);
        if (level()!=null) WagonSpatialIndex.update(this);
    }
    @Override public void refreshDimensions() { super.refreshDimensions();setBoundingBox(makeBoundingBox()); }
    public VoxelShape collisionShape() {
        if (worldShape == null || !position().equals(shapePosition)) {
            shapePosition=position(); worldShape=localShape.move(getX(),getY(),getZ());
        }
        return worldShape;
    }
    @Override protected AABB makeBoundingBox() {
        return localShape == null || localShape.isEmpty() ? super.makeBoundingBox() : localShape.bounds().move(position());
    }
    @Override public boolean canBeCollidedWith() { return !isRemoved(); }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override public void push(double x,double y,double z) {}
    @Override public void tick() {
        super.tick(); setDeltaMovement(Vec3.ZERO); setNoGravity(true);
        // No driving physics in this stage. Passenger positioning is handled by vanilla.
        if (!level().isClientSide && assemblyLock != null && level().hasChunkAt(assemblyLock)) {
            if (!(level().getBlockEntity(assemblyLock) instanceof AssemblyFrameBlockEntity frame) || !frame.restoring(getUUID())) assemblyLock=null;
        }
    }
    public boolean lock(net.minecraft.core.BlockPos frame) {
        if (assemblyLock != null && !assemblyLock.equals(frame)) return false;
        assemblyLock=frame.immutable(); return true;
    }
    public void unlock() { assemblyLock=null; }
    public int seatCapacity() { return parts().get(WagonSlot.SEAT)==WagonPart.DOUBLE_SEAT ? 2 : 1; }
    @Override protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof LivingEntity && getPassengers().size()<seatCapacity() && passenger.getBbWidth()<=1.5F;
    }
    @Override protected boolean couldAcceptPassenger() { return getPassengers().size()<seatCapacity(); }
    @Override public Vec3 getPassengerRidingPosition(Entity passenger) {
        int index=Math.max(0,getPassengers().indexOf(passenger));
        double x=seatCapacity()==1 ? 0 : (index==0 ? -.45 : .45);
        return position().add(WagonSlot.rotate(new Vec3(x,2.15625,-1.875),facing()));
    }
    @Override public InteractionResult interactAt(Player player,Vec3 hit,InteractionHand hand) { return interact(player,hand); }
    @Override public InteractionResult interact(Player player,InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        Vec3 start=player.getEyePosition(),end=start.add(player.getLookAngle().scale(player.entityInteractionRange()));
        AABB seat=new AABB(-1,1.7,-2.25,1,3.05,-1.35);
        Vec3 a=WagonSlot.rotate(new Vec3(seat.minX,seat.minY,seat.minZ),facing());
        Vec3 b=WagonSlot.rotate(new Vec3(seat.maxX,seat.maxY,seat.maxZ),facing());
        if (new AABB(a,b).move(position()).clip(start,end).isEmpty()) return InteractionResult.PASS;
        if (getPassengers().size()>=seatCapacity()) {
            if (!level().isClientSide) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.tm_wagon.seats_full"),true);
            return InteractionResult.FAIL;
        }
        if (!level().isClientSide && !player.startRiding(this)) return InteractionResult.FAIL;
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    /** Search outside the future block footprint, so unseating never traps a rider. */
    public Vec3 safeDismount(Entity passenger) {
        for (int dy=0;dy<=5;dy++) for (double z : new double[]{-2,0,2,3.5}) for (double x : new double[]{-2.5,2.5,-3.5,3.5}) {
            Vec3 p=position().add(WagonSlot.rotate(new Vec3(x,dy,z),facing()));
            AABB box=passenger.getDimensions(passenger.getPose()).makeBoundingBox(p);
            if (level().noCollision(passenger,box) && level().getWorldBorder().isWithinBounds(box)) return p;
        }
        return position().add(0,4,0);
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) { return safeDismount(passenger); }
    public void releasePassengers() {
        var riders=java.util.List.copyOf(getPassengers());
        for (Entity rider : riders) { Vec3 p=safeDismount(rider); rider.stopRiding(); rider.teleportTo(p.x,p.y,p.z); }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Modules",entityData.get(MODULES).copy()); tag.putInt("WagonFacing",facing().get2DDataValue());
        if (assemblyLock != null) tag.putLong("AssemblyLock",assemblyLock.asLong());
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        configure(tag.contains("Modules") ? decode(tag.getCompound("Modules")) : defaultParts(),
            Direction.from2DDataValue(tag.getInt("WagonFacing")));
        assemblyLock=tag.contains("AssemblyLock") ? net.minecraft.core.BlockPos.of(tag.getLong("AssemblyLock")) : null;
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
