package com.sange.tm_wagon.entity;

import com.sange.tm_wagon.assembly.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import com.sange.tm_wagon.physics.WagonPose;
import com.sange.tm_wagon.physics.WagonPhysics;
import com.sange.tm_wagon.physics.OrientedBox;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.Items;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.BlockPos;
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
public class WagonEntity extends Entity implements GeoEntity,com.sange.tm_wagon.cargo.CargoOwner {
    public static final int HORSE_HANG_TIMEOUT=60;
    public static final double PUSH_CONTACT_MARGIN=.15;
    private static final int PUSH_INPUT_TIMEOUT=6;
    private static final EntityDataAccessor<CompoundTag> MODULES = SynchedEntityData.defineId(WagonEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> MATERIALS=SynchedEntityData.defineId(WagonEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private Map<WagonSlot,com.sange.tm_wagon.material.WagonMaterial> materials=Map.of();
    public Map<WagonSlot,com.sange.tm_wagon.material.WagonMaterial> materials() { return materials; }
    public com.sange.tm_wagon.material.WagonMaterial material(WagonSlot slot) { return materials.getOrDefault(slot,com.sange.tm_wagon.material.WagonMaterial.DEFAULT); }
    public void setMaterials(Map<WagonSlot,com.sange.tm_wagon.material.WagonMaterial> values) { entityData.set(MATERIALS,com.sange.tm_wagon.material.WagonMaterial.save(values));materials=Map.copyOf(values); }
    private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(WagonEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> SEATS = SynchedEntityData.defineId(WagonEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> MOTION = SynchedEntityData.defineId(WagonEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> CARGO=SynchedEntityData.defineId(WagonEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private final com.sange.tm_wagon.cargo.CargoHold cargo=new com.sange.tm_wagon.cargo.CargoHold(this);
    private final WagonPhysics physics=new WagonPhysics();
    private final WagonCrowd crowd=new WagonCrowd(this);
    private float pitch,roll,steering,shaftPitch,oldPitch,oldRoll,oldSteering,oldShaftPitch;
    private final float[] wheels=new float[4],oldWheels=new float[4];
    private final UUID[] horses=new UUID[2];
    private final int[] hangingTicks=new int[2];
    private final double[] horseContactHeights={Double.NaN,Double.NaN};
    private int supportMask=15,forwardInput,steeringInput;
    private boolean boostedDrive;
    public boolean boostedDrive() { return boostedDrive; }
    private long lastInput=Long.MIN_VALUE;
    private UUID inputDriver;
    private record PushRequest(Player player,int forward,int sideways,int expires) {}
    private final Map<UUID,PushRequest> pushRequests=new java.util.HashMap<>();
    private int pushTick;
    private Vec3 lerpPosition;
    private float lerpYaw;
    private int lerpSteps;
    private boolean dismantled;
    private boolean motionStarted;
    private record Component(AABB box,WagonSlot slot) {}
    // Exact wool bounds in wagon-local space; independent of the solid lower-seat collider.
    private static final List<AABB> SINGLE_CUSHIONS=List.of(new AABB(-7.7/16,31.5/16,-35.0/16,7.7/16,34.4/16,-24.0/16));
    private static final List<AABB> DOUBLE_CUSHIONS=List.of(
        new AABB(-14.7/16,31.5/16,-35.0/16,-.15/16,34.4/16,-24.0/16),
        new AABB(.15/16,31.5/16,-35.0/16,14.7/16,34.4/16,-24.0/16));
    private static final List<AABB> SINGLE_WOODEN_SURFACES=List.of(new AABB(-8.5/16,30.02/16,-35.5/16,8.5/16,31.48/16,-23.5/16));
    private static final List<AABB> DOUBLE_WOODEN_SURFACES=List.of(new AABB(-15.5/16,30.02/16,-35.5/16,15.5/16,31.48/16,-23.5/16));
    private List<Component> components=List.of();
    private List<Component> pickingComponents=List.of();
    private List<AABB> worldBoxes;
    private List<OrientedBox> orientedBoxes;
    private WagonPose boxesPose;
    private float boxesSteering,boxesShaftPitch;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private VoxelShape localShape;
    private VoxelShape worldShape;
    private Vec3 shapePosition;
    private Map<WagonSlot,WagonPart> modules;
    private net.minecraft.core.BlockPos assemblyLock;
    private int requestedSeat=-1;
    private final Map<java.util.UUID,Integer> departingSeats=new java.util.HashMap<>();

    public WagonEntity(EntityType<? extends WagonEntity> type, Level level) {
        super(type,level); blocksBuilding = true; setNoGravity(true); setYRot(180); rebuildGeometry();
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MATERIALS,new CompoundTag());builder.define(MODULES,encode(defaultParts())); builder.define(FACING,Direction.NORTH.get2DDataValue());
        builder.define(SEATS,new CompoundTag());builder.define(MOTION,new CompoundTag());
        builder.define(CARGO,new CompoundTag());
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
    public Direction facing() { return Direction.fromYRot(getYRot()); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key.equals(MODULES) || key.equals(FACING)) rebuildGeometry();
        if(key.equals(MATERIALS))materials=com.sange.tm_wagon.material.WagonMaterial.loadSlots(entityData.get(MATERIALS));
        if(key.equals(CARGO)&&cargo!=null&&level()!=null&&level().isClientSide) {
            cargo.load(entityData.get(CARGO),level().registryAccess());rebuildGeometry();
        }
    }
    private void rebuildGeometry() {
        modules=decode(entityData.get(MODULES)); localShape=WagonGeometry.entityShape(modules,facing()); worldShape=null;worldBoxes=null;
        var list=new java.util.ArrayList<Component>();
        var picking=new java.util.ArrayList<Component>();
        modules.forEach((slot,part)->{
            for(AABB box:slot==WagonSlot.BODY&&cargo!=null?cargo.bodyBoxes():WagonGeometry.partBoxes(part)) {
                if(slot==WagonSlot.FRONT_RIGHT||slot==WagonSlot.REAR_RIGHT)box=new AABB(-box.maxX,box.minY,box.minZ,-box.minX,box.maxY,box.maxZ);
                box=box.move(slot.geometryOffset(cargoBody()));
                if(slot!=WagonSlot.SEAT)picking.add(new Component(box,slot));
                list.add(new Component(box,slot));
            }
            if(slot==WagonSlot.SEAT) {
                var seat=WagonGeometry.partBoxes(part);var lower=seat.getFirst();
                // Keep the simplified cabinet/support volume below the wool. Its wider
                // upper portion must not hide the visible cushion sides behind an invisible box.
                double baseTop=part.isWoodenSeat()?30.02/16:31.5/16;
                picking.add(new Component(new AABB(lower.minX,lower.minY,lower.minZ,lower.maxX,baseTop,lower.maxZ).move(0,0,cargoBody().frontOffset()),slot));
                for(int i=1;i<seat.size();i++)picking.add(new Component(seat.get(i).move(0,0,cargoBody().frontOffset()),slot));
                for(AABB cushion:driverSeatSurfaces())picking.add(new Component(cushion,slot));
            }
        });components=List.copyOf(list);
        if(cargo!=null) {
            for(AABB box:cargo.cover().selectionBoxes(cargoBody()))picking.add(new Component(box,WagonSlot.BODY));
            for(AABB box:cargo.canopy().rimBoxes(cargoBody()))picking.add(new Component(box,WagonSlot.BODY));
        }
        pickingComponents=List.copyOf(picking);
        setBoundingBox(makeBoundingBox());if(level()!=null)WagonSpatialIndex.update(this);
    }
    @Override public void setPos(double x,double y,double z) {
        super.setPos(x,y,z);
        if (level()!=null) WagonSpatialIndex.update(this);
    }
    @Override public void refreshDimensions() { super.refreshDimensions();setBoundingBox(makeBoundingBox());if(level()!=null)WagonSpatialIndex.update(this); }
    /** Compatibility shape is built lazily; movement and entity queries use individual boxes. */
    public VoxelShape collisionShape() {
        if(worldShape==null || !position().equals(shapePosition)) {
            shapePosition=position();
            if(cargo.empty()&&!cargo.gateOpen()&&Math.abs(pitch)<1e-6&&Math.abs(roll)<1e-6&&Math.abs(steering)<1e-6&&Math.abs(shaftPitch)<1e-6
                && Math.abs(Mth.wrapDegrees(getYRot()-facing().toYRot()))<1e-5)
                worldShape=WagonGeometry.entityShape(modules,facing()).move(getX(),getY(),getZ());
            else worldShape=WagonGeometry.shape(collisionBoxes());
        }return worldShape;
    }
    public WagonPose pose() { return new WagonPose(position(),getYRot(),pitch,roll); }
    @Override public com.sange.tm_wagon.cargo.CargoHold cargo() { return cargo; }
    @Override public WagonPart cargoBody() { return modules==null?WagonPart.CARGO_BODY:modules.getOrDefault(WagonSlot.BODY,WagonPart.CARGO_BODY); }
    public double wheelbase() { return cargoBody().rearWheelZ()-cargoBody().frontWheelZ(); }
    @Override public Level cargoLevel() { return level(); }
    @Override public WagonPose cargoPose() { return pose(); }
    @Override public WagonPart cargoSeat() { return parts().get(WagonSlot.SEAT); }
    @Override public boolean cargoLive() { return !isRemoved()&&level()!=null; }
    @Override public boolean cargoBusy() { return assemblyLock!=null; }
    @Override public String cargoGeometryChanged() { rebuildGeometry();return null; }
    @Override public void cargoChanged(boolean visible) {
        if(visible&&!level().isClientSide)entityData.set(CARGO,cargo.save(level().registryAccess(),true));
    }
    public WagonCrowd crowd() { return crowd; }
    public float pitch() { return pitch; }
    public float roll() { return roll; }
    public float steering() { return steering; }
    public float shaftPitch() { return shaftPitch; }
    public int supportMask() { return supportMask; }
    public void setSupportMask(int mask) { supportMask=mask; }
    public void setSteering(float value) { steering=value;worldShape=null;worldBoxes=null; }
    public void applyPose(WagonPose pose) {
        pitch=pose.pitch();roll=pose.roll();setYRot(pose.yaw());worldShape=null;worldBoxes=null;
        setPos(pose.position());
    }
    private Vec3 articulated(Vec3 point,WagonSlot slot) {
        return articulated(point,slot,steering,shaftPitch);
    }
    private Vec3 articulated(Vec3 point,WagonSlot slot,float steering,float shaftPitch) {
        if(slot==WagonSlot.SHAFTS)point=WagonPose.rotate(point.subtract(new Vec3(0,1.25,-26.0/16+cargoBody().frontOffset())),shaftPitch,0,0).add(0,1.25,-26.0/16+cargoBody().frontOffset());
        if(slot==WagonSlot.SHAFTS||slot==WagonSlot.FRONT_LEFT||slot==WagonSlot.FRONT_RIGHT)
            point=WagonPose.rotate(point.subtract(new Vec3(0,10.5/16,cargoBody().frontWheelZ())),0,0,steering).add(0,10.5/16,cargoBody().frontWheelZ());
        return point;
    }
    /** Vanilla leash rendering asks its holder for an interpolated world-space endpoint. */
    @Override public Vec3 getRopeHoldPosition(float partialTick) {
        boolean single=horseCapacity()==1;
        Vec3 beam=new Vec3(0,(single?19.75:20.15)/16,(single?-38.0:-39.0)/16+cargoBody().frontOffset());
        WagonPose rendered=new WagonPose(getPosition(partialTick),Mth.rotLerp(partialTick,yRotO,getYRot()),renderPitch(partialTick),renderRoll(partialTick));
        return rendered.point(articulated(beam,WagonSlot.SHAFTS,renderSteering(partialTick),renderShaftPitch(partialTick)));
    }
    public boolean isMoving() {
        if(level()!=null&&level().isClientSide) {
            double dx=getX()-xo,dz=getZ()-zo;
            return dx*dx+dz*dz>1e-6;
        }
        return getDeltaMovement().horizontalDistanceSqr()>1e-6;
    }
    /** Bounds are only used for queries and diagnostics, never as solid rotated geometry. */
    public List<AABB> motionBoxesAt(WagonPose pose) { return motionCollidersAt(pose).stream().map(OrientedBox::bounds).toList(); }
    public List<AABB> boxesAt(WagonPose pose) { return collidersAt(pose,false).stream().map(OrientedBox::bounds).toList(); }
    public List<OrientedBox> collidersAt(WagonPose pose) { return collidersAt(pose,false); }
    public List<OrientedBox> motionCollidersAt(WagonPose pose) {
        var boxes=new java.util.ArrayList<>(collidersAt(pose,true));
        for(Entity rider:getPassengers())if(rider instanceof LivingEntity)
            boxes.add(OrientedBox.of(com.sange.tm_wagon.cargo.SeatClearance.body(rider,this,seatPosition(passengerSeat(rider),pose))));
        return boxes;
    }
    private List<OrientedBox> collidersAt(WagonPose pose,boolean motion) {
        var boxes=new java.util.ArrayList<OrientedBox>(components.size());
        var frames=new EnumMap<WagonSlot,OrientedBox.Frame>(WagonSlot.class);
        for(Component part:components) {
            if(motion&&(part.slot==WagonSlot.FRONT_LEFT||part.slot==WagonSlot.FRONT_RIGHT||part.slot==WagonSlot.REAR_LEFT||part.slot==WagonSlot.REAR_RIGHT))continue;
            OrientedBox.Frame frame=frames.computeIfAbsent(part.slot,slot->{
                Vec3 origin=articulated(Vec3.ZERO,slot);
                return new OrientedBox.Frame(pose.vector(articulated(new Vec3(1,0,0),slot).subtract(origin)),
                    pose.vector(articulated(new Vec3(0,1,0),slot).subtract(origin)),
                    pose.vector(articulated(new Vec3(0,0,1),slot).subtract(origin)));
            });
            AABB b=part.box;
            boxes.add(new OrientedBox(pose.point(articulated(b.getCenter(),part.slot)),
                new Vec3(b.getXsize()/2,b.getYsize()/2,b.getZsize()/2),frame));
        }
        return List.copyOf(boxes);
    }
    public List<OrientedBox> colliders() {
        WagonPose current=pose();
        if(worldBoxes==null||orientedBoxes==null||!current.equals(boxesPose)||boxesSteering!=steering||boxesShaftPitch!=shaftPitch) {
            orientedBoxes=collidersAt(current,false);worldBoxes=orientedBoxes.stream().map(OrientedBox::bounds).toList();
            boxesPose=current;boxesSteering=steering;boxesShaftPitch=shaftPitch;
        }
        return orientedBoxes;
    }
    public List<AABB> collisionBoxes() { colliders();return worldBoxes; }
    public boolean intersects(AABB area) { return colliders().stream().anyMatch(box->box.intersects(area)); }
    /** Pick in each component's own frame, never its enlarged world-axis bounds. */
    public java.util.Optional<Vec3> pick(Vec3 start,Vec3 end) {
        WagonPose current=pose();Vec3 a=current.local(start),b=current.local(end);
        Vec3 wheelA=unsteered(a),wheelB=unsteered(b),shaftA=unpitched(wheelA),shaftB=unpitched(wheelB);
        Vec3 best=null;double distance=Double.POSITIVE_INFINITY;
        for(Component component:pickingComponents) {
            boolean front=component.slot==WagonSlot.FRONT_LEFT||component.slot==WagonSlot.FRONT_RIGHT;
            Vec3 localA=component.slot==WagonSlot.SHAFTS?shaftA:front?wheelA:a;
            Vec3 localB=component.slot==WagonSlot.SHAFTS?shaftB:front?wheelB:b;
            if(component.box.contains(localA))return java.util.Optional.of(start);
            var hit=component.box.clip(localA,localB);
            if(hit.isPresent()) {
                double candidate=localA.distanceToSqr(hit.get());
                if(candidate<distance) { best=current.point(articulated(hit.get(),component.slot));distance=candidate; }
            }
        }return java.util.Optional.ofNullable(best);
    }
    public boolean containsPickPoint(Vec3 point) {
        Vec3 local=pose().local(point),wheel=unsteered(local),shaft=unpitched(wheel);
        for(Component component:pickingComponents) {
            boolean front=component.slot==WagonSlot.FRONT_LEFT||component.slot==WagonSlot.FRONT_RIGHT;
            if(component.box.contains(component.slot==WagonSlot.SHAFTS?shaft:front?wheel:local))return true;
        }return false;
    }
    private Vec3 unsteered(Vec3 point) {
        return WagonPose.rotate(point.subtract(new Vec3(0,10.5/16,cargoBody().frontWheelZ())),0,0,-steering).add(0,10.5/16,cargoBody().frontWheelZ());
    }
    private Vec3 unpitched(Vec3 point) {
        return WagonPose.rotate(point.subtract(new Vec3(0,1.25,-26.0/16+cargoBody().frontOffset())),-shaftPitch,0,0).add(0,1.25,-26.0/16+cargoBody().frontOffset());
    }
    @Override protected AABB makeBoundingBox() {
        if(components==null||components.isEmpty())return localShape==null||localShape.isEmpty()?super.makeBoundingBox():localShape.bounds().move(position());
        var boxes=collisionBoxes();AABB bounds=boxes.getFirst();for(AABB b:boxes)bounds=bounds.minmax(b);
        // Include the visual roll in broad-phase picking, but never in collisionBoxes().
        if(cargo.cover().installed()&&cargo.cover().openRows()>0)
            bounds=bounds.minmax(com.sange.tm_wagon.cargo.CargoHold.worldBox(cargo.cover().rollBox(cargoBody()),pose()));
        return bounds;
    }
    public Vec3 wheelCentre(int i,WagonPose pose) {
        Vec3 point=new Vec3((i%2==0?-1:1)*cargoBody().wheelHalfTrack(),WagonPhysics.radius(i),i<2?cargoBody().frontWheelZ():cargoBody().rearWheelZ());
        return pose.point(articulated(point,i==0?WagonSlot.FRONT_LEFT:i==1?WagonSlot.FRONT_RIGHT:i==2?WagonSlot.REAR_LEFT:WagonSlot.REAR_RIGHT));
    }
    public Vec3 wheelForward(int i) { return pose().vector(WagonPose.rotate(new Vec3(0,0,-1),0,0,i<2?steering:0)); }
    public void addWheelAngle(int i,float value) { wheels[i]+=value; }
    public float renderPitch(float tick) { return Mth.lerp(tick,oldPitch,pitch); }
    public float renderRoll(float tick) { return Mth.lerp(tick,oldRoll,roll); }
    public float renderSteering(float tick) { return Mth.lerp(tick,oldSteering,steering); }
    public float renderShaftPitch(float tick) { return Mth.lerp(tick,oldShaftPitch,shaftPitch); }
    public float renderWheel(int i,float tick) { return Mth.lerp(tick,oldWheels[i],wheels[i]); }
    // The large entity AABB is for discovery/picking, never a solid collider.
    // Optimizers may collect vanilla entity collisions without EntityGetter's hooks.
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override public void push(double x,double y,double z) {}
    @Override public void tick() {
        super.tick();setNoGravity(true);
        if(!level().isClientSide)cargo.tick();
        oldPitch=pitch;oldRoll=roll;oldSteering=steering;oldShaftPitch=shaftPitch;System.arraycopy(wheels,0,oldWheels,0,4);
        if(level().isClientSide) {
            WagonPose previous=pose();
            var motion=entityData.get(MOTION);
            // Vanilla tracking quantizes yaw to 1/256 of a turn. At tilted walls that
            // changes the stepped collision faces enough to disagree with the server.
            float targetYaw=motion.contains("Yaw")?motion.getFloat("Yaw"):lerpYaw;
            Vec3 nextPosition=position();float nextYaw=getYRot();
            if(lerpSteps>0) {
                nextPosition=position().add(lerpPosition.subtract(position()).scale(1.0/lerpSteps));
                nextYaw=getYRot()+Mth.wrapDegrees(targetYaw-getYRot())/lerpSteps;lerpSteps--;
            } else if(motion.contains("Yaw")) {
                // Settle even when vanilla emits no new position/rotation packet.
                nextYaw=getYRot()+Mth.wrapDegrees(targetYaw-getYRot())*.5F;
            }
            float nextPitch=Mth.lerp(.5F,pitch,motion.getFloat("Pitch")),nextRoll=Mth.lerp(.5F,roll,motion.getFloat("Roll"));
            steering=Mth.lerp(.5F,steering,motion.getFloat("Steering"));shaftPitch=Mth.lerp(.5F,shaftPitch,motion.getFloat("ShaftPitch"));
            for(int i=0;i<4;i++)wheels[i]=Mth.lerp(.5F,wheels[i],motion.getFloat("Wheel"+i));
            supportMask=motion.contains("Support")?motion.getInt("Support"):15;worldShape=null;
            WagonPose next=new WagonPose(nextPosition,nextYaw,nextPitch,nextRoll);
            if(!previous.equals(next)) {
                applyPose(next);
            } else if(boxesSteering!=steering||boxesShaftPitch!=shaftPitch) {
                setBoundingBox(makeBoundingBox());WagonSpatialIndex.update(this);
            }
            return;
        }
        if(assemblyLock!=null&&level().hasChunkAt(assemblyLock)
            && (!(level().getBlockEntity(assemblyLock) instanceof AssemblyFrameBlockEntity frame)||!frame.restoring(getUUID())))assemblyLock=null;
        crowd.beginTick();
        Player driver=driver();
        if(driver==null||inputDriver==null||!driver.getUUID().equals(inputDriver)||level().getGameTime()-lastInput>10) { forwardInput=steeringInput=0;boostedDrive=false; }
        pushTick++;int pushing=manualPushInput();
        if(assemblyLock==null) {
            boolean staged=!motionStarted && level().getBlockEntity(blockPosition()) instanceof AssemblyFrameBlockEntity;
            if((forwardInput!=0&&readyToPull())||pushing!=0) { motionStarted=true;staged=false; }
            if(!staged)physics.tick(this,forwardInput,steeringInput,readyToPull(),pushing);else setDeltaMovement(Vec3.ZERO);
        } else { setDeltaMovement(Vec3.ZERO);physics.reset(); }
        updateHorses();
        if(tickCount%2==0)syncMotion();
        if(getY()<level().getMinBuildHeight()-64)discard();
    }
    @Override public void lerpTo(double x,double y,double z,float yaw,float xRot,int steps) {
        lerpPosition=new Vec3(x,y,z);lerpYaw=yaw;lerpSteps=Math.max(1,Math.min(5,steps));
    }
    public Player driver() {
        var seat=cargoSeat();
        if(seat==null)return null;
        for(Entity passenger:getPassengers())if(passenger instanceof Player player&&passengerSeat(player)==seat.driverSeat())return player;
        return null;
    }
    public void acceptInput(Player player,int forward,int steer) { acceptInput(player,forward,steer,false); }
    public void acceptInput(Player player,int forward,int steer,boolean sprint) {
        if(level().isClientSide||driver()!=player||assemblyLock!=null)return;
        if(!player.getUUID().equals(inputDriver))boostedDrive=false;
        if(forward<=0)boostedDrive=false;
        else if(sprint)boostedDrive=true;
        forwardInput=Mth.clamp(forward,-1,1);steeringInput=Mth.clamp(steer,-1,1);lastInput=level().getGameTime();inputDriver=player.getUUID();
    }
    public boolean hasAttachedHorses() {
        if(level()!=null&&level().isClientSide) { var tag=entityData.get(MOTION);return tag.hasUUID("Horse0")||tag.hasUUID("Horse1"); }
        return horses[0]!=null||horses[1]!=null;
    }
    /** Grounded players may also push stranded, tipped or unsupported wagons. */
    public boolean canBeManuallyPushed() {
        return !isRemoved()&&assemblyLock==null&&!hasAttachedHorses();
    }
    /** onGround also includes entity platforms; only real block support permits pushing. */
    public static boolean hasGroundForPushing(Player player) {
        if(!player.isAlive()||player.isRemoved()||player.isSpectator()||player.isPassenger()
            ||!player.onGround()||player.getAbilities().flying||player.getDeltaMovement().y>.1)return false;
        AABB feet=player.getBoundingBox();
        // Recheck the current feet, not the cached supporting block (which can survive a position update).
        return player.level().findSupportingBlock(player,new AABB(feet.minX,feet.minY-.005,feet.minZ,
            feet.maxX,feet.minY+1e-6,feet.maxZ)).isPresent();
    }
    /** Walking intent is reported because a player's actual movement becomes zero against a solid cart. */
    public int pushDirection(Player player,int forward,int sideways) {
        if(Math.abs(forward)>1||Math.abs(sideways)>1||(forward==0&&sideways==0)
            ||player.level()!=level()||!hasGroundForPushing(player)||!canBeManuallyPushed())return 0;
        Vec3 intent=new Vec3(sideways,0,forward).normalize().yRot(-(float)Math.toRadians(player.getYRot()));
        Vec3 heading=new WagonPose(position(),getYRot(),0,0).forward();
        double along=intent.dot(heading);
        // Allow a little steering/camera tolerance, but reject transverse/diagonal pressure before scanning parts.
        if(Math.abs(along)<Math.cos(Math.toRadians(30)))return 0;
        AABB contact=player.getBoundingBox().inflate(PUSH_CONTACT_MARGIN,.05,PUSH_CONTACT_MARGIN);
        // Use the surface touched, rather than fixed car-front/car-rear zones. Walking
        // alongside a contacted wheel or axle is also a useful longitudinal push.
        for(OrientedBox box:colliders())if(box.intersects(contact)) {
            Vec3 nearest=box.closestPoint(player.getBoundingBox().getCenter());
            double dx=nearest.x-player.getX();
            double dz=nearest.z-player.getZ();
            // Moving away from the contact is not pulling the cart along behind the player.
            if(intent.x*dx+intent.z*dz>=-1e-4)return along>0?1:-1;
        }
        return 0;
    }
    public void acceptPush(Player player,int forward,int sideways) {
        if(level().isClientSide)return;
        if(pushDirection(player,forward,sideways)==0)pushRequests.remove(player.getUUID());
        else pushRequests.put(player.getUUID(),new PushRequest(player,forward,sideways,pushTick+PUSH_INPUT_TIMEOUT));
    }
    private int manualPushInput() {
        if(pushRequests.isEmpty())return 0;
        if(!canBeManuallyPushed()) { pushRequests.clear();return 0; }
        int force=0;
        for(var iterator=pushRequests.values().iterator();iterator.hasNext();) {
            var request=iterator.next();int direction=pushDirection(request.player,request.forward,request.sideways);
            if(pushTick>request.expires||direction==0)iterator.remove();else force+=direction;
        }
        return Integer.signum(force);
    }
    public boolean falling() { return level()!=null&&level().isClientSide?entityData.get(MOTION).getBoolean("Falling"):physics.falling(); }
    public void syncMotion() {
        CompoundTag tag=new CompoundTag();tag.putFloat("Yaw",getYRot());tag.putFloat("Pitch",pitch);tag.putFloat("Roll",roll);tag.putFloat("Steering",steering);tag.putFloat("ShaftPitch",shaftPitch);
        tag.putInt("Support",supportMask);
        tag.putBoolean("Falling",physics.falling());
        for(int i=0;i<4;i++)tag.putFloat("Wheel"+i,wheels[i]);
        for(int i=0;i<2;i++)if(horses[i]!=null)tag.putUUID("Horse"+i,horses[i]);
        entityData.set(MOTION,tag);
    }
    public boolean lock(net.minecraft.core.BlockPos frame) {
        if (assemblyLock != null && !assemblyLock.equals(frame)) return false;
        if(cargo.gateMoving())return false;
        cargo.closeMenus();assemblyLock=frame.immutable();forwardInput=steeringInput=0;boostedDrive=false;pushRequests.clear();physics.reset();return true;
    }
    public void unlock() { assemblyLock=null; }
    public int seatCapacity() { var seat=parts().get(WagonSlot.SEAT);return seat==null?0:seat.seatCapacity(); }
    public static final int CARGO_SEAT_BASE=3;
    private boolean validSeat(int seat) {
        if(seat>=0&&seat<seatCapacity())return true;
        int slot=seat-CARGO_SEAT_BASE;
        return slot>=0&&slot<cargo.capacity()&&cargo.entry(slot)!=null
            &&cargo.entry(slot).kind==com.sange.tm_wagon.cargo.CargoEntry.Kind.STOOL;
    }
    private int availableSeat(Entity passenger) {
        if(requestedSeat>=0)return validSeat(requestedSeat)&&!seatOccupied(requestedSeat,passenger)?requestedSeat:-1;
        var seats=entityData.get(SEATS);String key=passenger.getUUID().toString();
        if(seats.contains(key)&&validSeat(seats.getInt(key))&&!seatOccupied(seats.getInt(key),passenger))return seats.getInt(key);
        for(int i=0;i<seatCapacity();i++)if(!seatOccupied(i,passenger))return i;
        return -1;
    }
    @Override protected boolean canAddPassenger(Entity passenger) {
        int seat=availableSeat(passenger);
        return passenger instanceof LivingEntity&&seat>=0
            &&passenger.getBbWidth()<=(seat<CARGO_SEAT_BASE?1.5F:1F)&&seatClear(passenger,seat);
    }
    @Override protected boolean couldAcceptPassenger() {
        for(int i=0;i<CARGO_SEAT_BASE+cargo.capacity();i++)
            if(validSeat(i)&&!seatOccupied(i,null))return true;
        return false;
    }
    /** Stable IDs 0..2 are driver seats; cargo stools start at 3, independent of passenger ordering. */
    public int passengerSeat(Entity passenger) {
        var seats=entityData.get(SEATS);String key=passenger.getUUID().toString();
        if(seats.contains(key))return seats.getInt(key);
        return Math.clamp(getPassengers().indexOf(passenger),0,seatCapacity()-1);
    }
    private boolean seatOccupied(int seat,Entity except) {
        return getPassengers().stream().anyMatch(passenger -> passenger!=except&&passengerSeat(passenger)==seat);
    }
    public boolean cargoSeatOccupied(int slot) { return seatOccupied(CARGO_SEAT_BASE+slot,null); }
    /** AI may board a passenger seat directly, without bypassing the driver's assignment. */
    public int freeCompanionSeat() {
        if(isRemoved()||cargoBusy()||seatCapacity()<2)return -1;
        for(int seat=0;seat<seatCapacity();seat++)if(companionSeatAvailable(seat))return seat;
        return -1;
    }
    public boolean companionSeatAvailable(int seat) {
        return !isRemoved()&&!cargoBusy()&&seat>=0&&seat<seatCapacity()&&seat!=cargoSeat().driverSeat()&&!seatOccupied(seat,null);
    }
    public boolean seatClear(Entity rider,int seat) {
        return validSeat(seat)&&com.sange.tm_wagon.cargo.SeatClearance.clear(cargo,rider,this,seatPosition(seat));
    }
    public Vec3 companionSeatPosition(int seat) { return seatPosition(seat); }
    public boolean boardCompanion(LivingEntity rider,int seat) {
        if(level().isClientSide||!companionSeatAvailable(seat)
            ||!com.sange.tm_wagon.cargo.CargoSeats.eligible(rider)||rider.level()!=level())return false;
        requestedSeat=seat;
        try { boolean boarded=rider.startRiding(this);if(boarded)positionRider(rider);return boarded; }
        finally { requestedSeat=-1; }
    }
    public boolean boardCargoSeat(LivingEntity rider,int slot) {
        requestedSeat=CARGO_SEAT_BASE+slot;
        try { return rider.startRiding(this); }finally { requestedSeat=-1; }
    }
    public void releaseCargoPassengers(int slot) {
        for(Entity rider:List.copyOf(getPassengers())) {
            int seat=passengerSeat(rider);
            if(seat>=CARGO_SEAT_BASE&&(slot<0||seat==CARGO_SEAT_BASE+slot)) {
                Vec3 target=safeDismount(rider);rider.stopRiding();rider.setPose(Pose.STANDING);
                rider.teleportTo(target.x,target.y,target.z);rider.setDeltaMovement(Vec3.ZERO);rider.fallDistance=0;
            }
        }
    }
    @Override protected void addPassenger(Entity passenger) {
        int seat=availableSeat(passenger);
        super.addPassenger(passenger);
        if(!level().isClientSide) {
            var seats=entityData.get(SEATS).copy();seats.putInt(passenger.getUUID().toString(),seat);entityData.set(SEATS,seats);
            departingSeats.remove(passenger.getUUID());
        }
    }
    @Override protected void removePassenger(Entity passenger) {
        int seat=passengerSeat(passenger);
        super.removePassenger(passenger);
        if (!level().isClientSide) {
            if(passenger.getUUID().equals(inputDriver)) { forwardInput=steeringInput=0;boostedDrive=false;inputDriver=null; }
            if (!passenger.isRemoved()) departingSeats.put(passenger.getUUID(),seat);
            var seats=entityData.get(SEATS).copy();seats.remove(passenger.getUUID().toString());entityData.set(SEATS,seats);
        }
    }
    private Vec3 seatPosition(int seat) { return seatPosition(seat,pose()); }
    private Vec3 seatPosition(int seat,WagonPose pose) {
        if(seat>=CARGO_SEAT_BASE&&seat<CARGO_SEAT_BASE+cargo.capacity())
            return pose.point(cargo.centreAt(seat-CARGO_SEAT_BASE).add(0,.5,0));
        double x=(seat-(seatCapacity()-1)/2.0)*cargoSeat().seatSpacing();
        return pose.point(new Vec3(x,cargoSeat()!=null&&cargoSeat().isWoodenSeat()?31.5/16:2.15625,-1.875+cargoBody().frontOffset()));
    }
    @Override public Vec3 getPassengerRidingPosition(Entity passenger) {
        return seatPosition(passengerSeat(passenger));
    }
    private Vec3 localPosition(Vec3 relative) { return pose().local(position().add(relative)); }
    private List<AABB> driverSeatSurfaces() {
        List<AABB> surfaces;
        if(seatCapacity()==3) {
            var result=new java.util.ArrayList<AABB>();
            for(int seat=0;seat<3;seat++) {
                double spacing=cargoSeat().seatSpacing(),centre=(seat-1)*spacing;
                boolean wooden=cargoSeat().isWoodenSeat();
                double half=wooden?spacing/2:(spacing-.3/16)/2;
                double left=wooden&&seat==0?-cargoSeat().seatHalfWidth():centre-half;
                double right=wooden&&seat==2?cargoSeat().seatHalfWidth():centre+half;
                result.add(new AABB(left,wooden?30.02/16:31.5/16,wooden?-35.5/16:-35.0/16,
                    right,wooden?31.48/16:34.4/16,wooden?-23.5/16:-24.0/16));
            }
            surfaces=result;
        } else if(cargoSeat()!=null&&cargoSeat().isWoodenSeat())surfaces=seatCapacity()==2?DOUBLE_WOODEN_SURFACES:SINGLE_WOODEN_SURFACES;
        else surfaces=seatCapacity()==2?DOUBLE_CUSHIONS:SINGLE_CUSHIONS;
        return surfaces.stream().map(b->b.move(0,0,cargoBody().frontOffset())).toList();
    }
    /** Only the first visible cushion or plain wooden top can board; supplied points cannot bypass an obstruction. */
    private InteractionResult boardVisibleDriverSeat(Player player,java.util.Optional<Vec3> actual) {
        if(player.isSecondaryUseActive()||actual.isEmpty())return InteractionResult.PASS;
        Vec3 world=actual.get(),local=pose().local(world),eye=player.getEyePosition(),localEye=pose().local(eye);
        var cushions=driverSeatSurfaces();
        for(int seat=0;seat<cushions.size();seat++) {
            AABB cushion=cushions.get(seat);
            if(!cushion.inflate(.00001).contains(local)||cushion.contains(localEye)||local.y<=cushion.minY+.00001)continue;
            boolean wooden=cargoSeat()!=null&&cargoSeat().isWoodenSeat();
            if(wooden&&(Math.abs(local.y-cushion.maxY)>.00001||localEye.y<=cushion.maxY+.00001))continue;
            var block=level().clip(new net.minecraft.world.level.ClipContext(eye,world,
                net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
            if(block.getType()!=net.minecraft.world.phys.HitResult.Type.MISS&&block.getLocation().distanceToSqr(eye)+.0001<world.distanceToSqr(eye))return InteractionResult.PASS;
            return boardSeat(player,wooden&&seatCapacity()==2?(local.x<0?0:1):seat);
        }
        return InteractionResult.PASS;
    }
    public boolean canRiderInteract() { return true; }
    @Override public InteractionResult interactAt(Player player,Vec3 hit,InteractionHand hand) {
        Vec3 local=localPosition(hit);
        Vec3 eye=player.getEyePosition();var actual=pick(eye,eye.add(player.getLookAngle().scale(player.entityInteractionRange())));
        if(actual.isPresent())local=pose().local(actual.get());
        InteractionResult freight=cargo.interact(player,hand,local);if(freight!=InteractionResult.PASS)return freight;
        int hitch=hitchSlot(local);
        if(hitch>=0)return bindAt(player,hitch);
        return boardVisibleDriverSeat(player,actual);
    }
    @Override public InteractionResult interact(Player player,InteractionHand hand) {
        Vec3 start=player.getEyePosition(),end=start.add(player.getLookAngle().scale(player.entityInteractionRange()));
        var cargoHit=pick(start,end);
        if(cargoHit.isPresent()) { var freight=cargo.interact(player,hand,pose().local(cargoHit.get()));if(freight!=InteractionResult.PASS)return freight; }
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        var hitchHit=cargoHit;
        if(hitchHit.isPresent()) { int slot=hitchSlot(pose().local(hitchHit.get()));if(slot>=0)return bindAt(player,slot); }
        return boardVisibleDriverSeat(player,cargoHit);
    }
    private InteractionResult boardSeat(Player player,int seat) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (seatOccupied(seat,null)) {
            if (!level().isClientSide) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.tm_wagon.seat_occupied"),true);
            return InteractionResult.FAIL;
        }
        if (!level().isClientSide) {
            requestedSeat=seat;
            try { if (!player.startRiding(this)) return InteractionResult.FAIL; }
            finally { requestedSeat=-1; }
        }
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
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Integer previous=departingSeats.remove(passenger.getUUID());
        int seatId=previous==null?passengerSeat(passenger):previous;
        if(seatId>=CARGO_SEAT_BASE)return com.sange.tm_wagon.cargo.CargoSeats.standUp(cargo,seatId-CARGO_SEAT_BASE,passenger,false);
        Vec3 seat=seatPosition(seatId);
        // Stand on the cushion directly above the chosen seat. Wider riders or
        // obstructions may require more vertical clearance, preserving X/Z.
        for (double offset=.001;offset<=3;offset+=.125) {
            Vec3 target=seat.add(0,offset,0);
            if (level().noCollision(passenger,passenger.getDimensions(Pose.STANDING).makeBoundingBox(target))) return target;
        }
        return seat.add(0,3,0);
    }
    public void releasePassengers() {
        var riders=java.util.List.copyOf(getPassengers());
        for (Entity rider : riders) { Vec3 p=safeDismount(rider); rider.stopRiding(); rider.teleportTo(p.x,p.y,p.z); }
    }
    public int horseCapacity() { return parts().get(WagonSlot.SHAFTS)==WagonPart.DOUBLE_HORSE_SHAFTS?2:1; }
    public boolean hasHorse(UUID id) {
        if(level().isClientSide) { var tag=entityData.get(MOTION);return (tag.hasUUID("Horse0")&&id.equals(tag.getUUID("Horse0")))||(tag.hasUUID("Horse1")&&id.equals(tag.getUUID("Horse1"))); }
        return id.equals(horses[0])||id.equals(horses[1]);
    }
    public AbstractHorse horse(int slot) {
        if(!(level() instanceof ServerLevel server)||horses[slot]==null)return null;
        return server.getEntity(horses[slot]) instanceof AbstractHorse horse?horse:null;
    }
    public boolean readyToPull() {
        for(int i=0;i<horseCapacity();i++) { var h=horse(i);if(h==null||!h.isAlive())return false; }
        return true;
    }
    public Vec3 horsePosition(int slot) {
        double x=horseCapacity()==1?0:slot==0?-1.05:1.05;
        return pose().point(articulated(new Vec3(x,0,(horseCapacity()==1?-4.35:-4.45)+cargoBody().frontOffset()),WagonSlot.SHAFTS));
    }
    private Vec3 horseBasePosition(int slot) {
        return horseBasePosition(slot,pose());
    }
    private Vec3 horseBasePosition(int slot,WagonPose pose) {
        double x=horseCapacity()==1?0:slot==0?-1.05:1.05;
        Vec3 p=new Vec3(x,0,(horseCapacity()==1?-4.35:-4.45)+cargoBody().frontOffset());
        p=WagonPose.rotate(p.subtract(new Vec3(0,10.5/16,cargoBody().frontWheelZ())),0,0,steering).add(0,10.5/16,cargoBody().frontWheelZ());
        return pose.point(p);
    }
    /** Solve uphill articulation before moving: the collision poles must not lag behind
     * the horses' next legal foothold. Downhill suspension keeps its existing damping. */
    public void prepareUphillShafts(WagonPose ahead) {
        double desired=0;int count=0;
        for(int i=0;i<horseCapacity();i++) {
            var horse=horse(i);if(horse==null)continue;
            Vec3 base=horseBasePosition(i,ahead);var ground=horseGround(horse,base);
            if(!ground.present()||ground.forbidden())return;
            desired+=Math.atan2(ground.height()-base.y,horseCapacity()==1?2.725:2.825);count++;
        }
        if(count>0) {
            float maximum=(float)Math.toRadians(55);
            float target=Math.max(shaftPitch,(float)Math.min(maximum,desired/count));
            // The double pole extends beyond the horses' feet. Anticipate the ground
            // under that overhang too; this changes articulation, never horse step height.
            for(var part:components)if(part.slot==WagonSlot.SHAFTS&&part.box.getZsize()>1) {
                var b=part.box;
                // A tip can already be outside a ledge while the wood just behind it
                // still crosses the raised ground, especially during diagonal climbs.
                for(int sample=0;sample<3;sample++) {
                    Vec3 tip=new Vec3((b.minX+b.maxX)/2,b.minY,b.minZ+sample*.4);
                    Vec3 world=ahead.point(articulated(tip,WagonSlot.SHAFTS,steering,target));
                    var ground=WagonPhysics.ground(level(),world,1.001,1.001,Math.max(.15,b.getXsize()/2+.02));
                    if(!ground.present()||ground.forbidden()||ground.height()+.025<=world.y)continue;
                    float low=target,high=maximum;
                    for(int j=0;j<10;j++) {
                        float mid=(low+high)/2;
                        if(ahead.point(articulated(tip,WagonSlot.SHAFTS,steering,mid)).y<ground.height()+.025)low=mid;else high=mid;
                    }
                    target=high;
                }
            }
            if(target>shaftPitch)setShaftPitchClear(target);
        }
    }
    private List<OrientedBox> shaftBoxes(float angle) {
        var pose=pose();var boxes=new java.util.ArrayList<OrientedBox>();
        Vec3 origin=articulated(Vec3.ZERO,WagonSlot.SHAFTS,steering,angle);
        var frame=new OrientedBox.Frame(pose.vector(articulated(new Vec3(1,0,0),WagonSlot.SHAFTS,steering,angle).subtract(origin)),
            pose.vector(articulated(new Vec3(0,1,0),WagonSlot.SHAFTS,steering,angle).subtract(origin)),
            pose.vector(articulated(new Vec3(0,0,1),WagonSlot.SHAFTS,steering,angle).subtract(origin)));
        for(var part:components)if(part.slot==WagonSlot.SHAFTS) {
            var box=part.box;
            boxes.add(new OrientedBox(pose.point(articulated(box.getCenter(),WagonSlot.SHAFTS,steering,angle)),
                new Vec3(box.getXsize()/2,box.getYsize()/2,box.getZsize()/2),frame));
        }
        return boxes;
    }
    /** Articulation remains solid at low roofs too; never teleport raised poles through
     * a beam. Only query the small shaft volume, once for the whole angular sweep. */
    private void setShaftPitchClear(float target) {
        float start=shaftPitch;if(Math.abs(target-start)<1e-5F)return;
        var before=shaftBoxes(start);if(before.isEmpty())return;
        AABB area=before.getFirst().bounds();
        for(var box:before)area=area.minmax(box.bounds());
        for(var box:shaftBoxes(target))area=area.minmax(box.bounds());
        double radius=0,pivotZ=-26.0/16+cargoBody().frontOffset();
        for(var part:components)if(part.slot==WagonSlot.SHAFTS) {
            var b=part.box;double y=Math.max(Math.abs(b.minY-1.25),Math.abs(b.maxY-1.25));
            double z=Math.max(Math.abs(b.minZ-pivotZ),Math.abs(b.maxZ-pivotZ));radius=Math.max(radius,Math.hypot(y,z));
        }
        area=area.inflate(.001+radius*(1-Math.cos((target-start)/2)));
        var terrain=new java.util.ArrayList<OrientedBox>();
        for(var shape:level().getBlockCollisions(this,area))for(var box:shape.toAabbs())terrain.add(OrientedBox.of(box));
        for(var surface:com.sange.tm_wagon.compat.StructureCollision.surfaces(level(),area))terrain.add(surface.box());
        if(terrain.isEmpty()) { shaftPitch=target;worldBoxes=null;worldShape=null;return; }
        int steps=Math.max(1,(int)Math.ceil(Math.abs(target-start)/.025));
        for(int i=1;i<=steps;i++) {
            float angle=Mth.lerp((float)i/steps,start,target);var next=shaftBoxes(angle);
            boolean blocked=false;
            for(var block:terrain) {
                boolean wasInside=false;for(var box:before)if(box.intersects(block)) { wasInside=true;break; }
                if(wasInside)continue;
                for(var box:next)if(box.intersects(block)) { blocked=true;break; }
                if(blocked)break;
            }
            if(blocked)break;
            shaftPitch=angle;before=next;
        }
        if(shaftPitch!=start) { worldBoxes=null;worldShape=null; }
    }
    public boolean horsesCanAdvance(Vec3 delta) {
        for(int i=0;i<horseCapacity();i++) {
            AbstractHorse h=horse(i);if(h==null)continue;
            Vec3 base=horseBasePosition(i).add(delta);var g=horseGround(h,base);
            if(g.forbidden())return false;
            Vec3 target=g.present()?new Vec3(base.x,g.height(),base.z):horsePosition(i).add(delta);
            AABB horseSpace=h.getDimensions(Pose.STANDING).makeBoundingBox(target).deflate(.002);
            if(!level().noBlockCollision(h,horseSpace)||!com.sange.tm_wagon.compat.StructureCollision.clear(level(),horseSpace))return false;
        }return true;
    }
    /** Each horse steps from its own last foot height, even while the rear axle is on an earlier stair. */
    private WagonPhysics.Ground horseGround(AbstractHorse horse,Vec3 next) {
        int slot=horse.getUUID().equals(horses[0])?0:1;
        // Lowering the shaft over a cliff must not create another "step" from thin air.
        double reference=hangingTicks[slot]>0&&!falling()&&Double.isFinite(horseContactHeights[slot])?horseContactHeights[slot]:horse.getY();
        return WagonPhysics.ground(level(),new Vec3(next.x,reference,next.z),1.001,1.001,horse.getBbWidth()/2);
    }
    private int hitchSlot(Vec3 local) {
        Vec3 p=unpitched(unsteered(local));
        WagonPart shafts=parts().get(WagonSlot.SHAFTS);
        if(shafts==null||WagonGeometry.partBoxes(shafts).stream().noneMatch(box->box.move(0,0,cargoBody().frontOffset()).inflate(.025).contains(p)))return -1;
        if(horseCapacity()==1)return 0;
        // Any pole or crossbar can attach a horse. Prefer the clicked side,
        // then the other free hitch; centre-pole clicks fill the left first.
        int preferred=p.x>0?1:0;
        return horses[preferred]!=null&&horses[1-preferred]==null?1-preferred:preferred;
    }
    private InteractionResult bindAt(Player player,int slot) {
        if(level().isClientSide)return InteractionResult.SUCCESS;
        String error=horses[slot]!=null?"message.tm_wagon.hitch_occupied":null;
        if(error==null&&assemblyLock!=null)error="message.tm_wagon.assembly_busy";
        if(error==null) {
            var candidates=level().getEntitiesOfClass(AbstractHorse.class,getBoundingBox().inflate(5),h->HorseHarness.eligible(h)&&h.getLeashHolder()==player&&!HorseHarness.attached(h));
            var nearest=candidates.stream().min(java.util.Comparator.comparingDouble(h->h.position().distanceToSqr(horsePosition(slot)))).orElse(null);
            error=nearest==null?"message.tm_wagon.lead_horse_required":attachHorse(player,nearest,slot);
        }
        if(error!=null)player.displayClientMessage(net.minecraft.network.chat.Component.translatable(error),true);
        return InteractionResult.CONSUME;
    }
    /** Transfers the already-paid player leash; never consumes a second inventory lead. */
    public String attachHorse(Player player,AbstractHorse horse,int slot) {
        if(level().isClientSide||assemblyLock!=null||slot<0||slot>=horseCapacity()||horses[slot]!=null)return "message.tm_wagon.hitch_occupied";
        if(horse.level()!=level()||!HorseHarness.eligible(horse)||HorseHarness.attached(horse)||horse.getLeashHolder()!=player)return "message.tm_wagon.lead_horse_required";
        if(horse.position().distanceToSqr(horsePosition(slot))>64)return "message.tm_wagon.lead_horse_required";
        Vec3 target=horsePosition(slot);var ground=WagonPhysics.ground(level(),target,1,1,horse.getBbWidth()/2);
        if(!ground.present()||ground.forbidden())return "message.tm_wagon.hitch_blocked";
        target=new Vec3(target.x,ground.height(),target.z);
        AABB space=horse.getDimensions(Pose.STANDING).makeBoundingBox(target);
        if(!level().getWorldBorder().isWithinBounds(space)||!level().noBlockCollision(horse,space.deflate(.001))||!com.sange.tm_wagon.compat.StructureCollision.clear(level(),space.deflate(.001)))return "message.tm_wagon.hitch_blocked";
        for(Entity e:level().getEntities(horse,space))if(e!=this&&e!=player&&!e.isPassengerOfSameVehicle(this)&&e.isAlive())return "message.tm_wagon.hitch_blocked";
        horses[slot]=horse.getUUID();hangingTicks[slot]=0;horseContactHeights[slot]=target.y;HorseHarness.mark(horse,this);horse.setPos(target);horse.setYRot(getYRot());horse.setYBodyRot(getYRot());syncMotion();return null;
    }
    public void detachHorse(UUID id,boolean refund) {
        for(int i=0;i<2;i++)if(id.equals(horses[i])) {
            AbstractHorse h=horse(i);horses[i]=null;hangingTicks[i]=0;horseContactHeights[i]=Double.NaN;
            if(h!=null) { HorseHarness.clear(h);h.setDeltaMovement(getDeltaMovement().add(0,-.08,0)); }
            else if(level() instanceof ServerLevel server)HarnessSavedData.get(server).release(id);
            if(refund&&!level().isClientSide) {
                if(h!=null)h.spawnAtLocation(Items.LEAD);else spawnAtLocation(Items.LEAD);
            }
            syncMotion();return;
        }
    }
    public void detachAllHorses() { for(UUID id:horses.clone())if(id!=null)detachHorse(id,true); }
    private void updateHorses() {
        double desired=0;int count=0;
        for(int i=0;i<horseCapacity();i++) {
            AbstractHorse h=horse(i);if(h==null)continue;
            if(!h.isAlive()) { detachHorse(h.getUUID(),true);continue; }
            if(!com.sange.tm_wagon.platform.EntityData.of(h).hasUUID(HorseHarness.OWNER)||!com.sange.tm_wagon.platform.EntityData.of(h).getUUID(HorseHarness.OWNER).equals(getUUID())) { detachHorse(h.getUUID(),true);continue; }
            h.getNavigation().stop();h.setNoGravity(true);h.setDeltaMovement(Vec3.ZERO);h.fallDistance=0;
            com.sange.tm_wagon.platform.EntityData.of(h).putLong(HorseHarness.OWNER_POS,blockPosition().asLong());
            if(h.getLeashHolder()!=this)h.setLeashedTo(this,true);
            Vec3 base=horseBasePosition(i);var ground=horseGround(h,base);
            double length=horseCapacity()==1?2.725:2.825;
            if(ground.present()&&!ground.forbidden())desired+=Math.atan2(ground.height()-base.y,length);
            else desired-=Math.atan2(1,length);
            count++;
        }
        float max=(float)Math.atan2(1,horseCapacity()==1?2.725:2.825);
        // Several individually legal steps may put the horse more than one block above
        // the rear axle. Upward articulation follows that grade; downward reach stays limited.
        setShaftPitchClear(Mth.lerp(.4F,shaftPitch,Mth.clamp(count==0?0:(float)(desired/count),-max,(float)Math.toRadians(55))));
        worldBoxes=null;worldShape=null;
        for(int i=0;i<horseCapacity();i++) {
            AbstractHorse h=horse(i);if(h==null)continue;
            Vec3 target=horsePosition(i);var ground=horseGround(h,target);
            boolean grounded=ground.present()&&!ground.forbidden();
            if(grounded)target=new Vec3(target.x,ground.height(),target.z);
            AABB space=h.getDimensions(Pose.STANDING).makeBoundingBox(target).deflate(.002);
            if(!level().noBlockCollision(h,space)) {
                var actual=WagonPhysics.ground(level(),h.position(),.03,.03,h.getBbWidth()/2);
                updateHorseSuspension(h,i,actual.present()&&!actual.forbidden());continue;
            }
            if(grounded)horseContactHeights[i]=ground.height();
            Vec3 movement=target.subtract(h.position());h.setPos(target);h.setYRot(getYRot()+(float)Math.toDegrees(steering));h.setYBodyRot(h.getYRot());
            HorseHarness.updateDrivingPose(h);
            h.walkAnimation.update((float)Math.min(1,movement.horizontalDistance()*4),.4F);h.setOnGround(grounded);
            updateHorseSuspension(h,i,grounded);
        }
        setBoundingBox(makeBoundingBox());if(level()!=null)WagonSpatialIndex.update(this);
    }
    private void updateHorseSuspension(AbstractHorse horse,int slot,boolean grounded) {
        if(grounded)hangingTicks[slot]=0;
        else if(++hangingTicks[slot]>=HORSE_HANG_TIMEOUT)detachHorse(horse.getUUID(),true);
    }
    @Override public boolean canCollideWith(Entity entity) {
        return !com.sange.tm_wagon.cargo.StrawMatSleep.attachedTo(entity,this)
            &&!crowd.yields(entity)&&!(entity instanceof AbstractHorse h&&hasHorse(h.getUUID()))&&!entity.isPassengerOfSameVehicle(this)
            &&(entity instanceof WagonEntity||entity.canBeCollidedWith()||entity.isPushable());
    }
    // Keep right-click picking/collision, but never enter normal combat (cooldown, sounds or knockback).
    // NeoForge calls the hammer's onLeftClickEntity before checking isAttackable.
    @Override public boolean isAttackable() { return false; }
    @Override public boolean isInvulnerable() { return true; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public boolean hurt(DamageSource source,float amount) {
        return false;
    }
    /** A direct hammer action, not damage: no attack cooldown, sweeping or projectile side effects. */
    public boolean dismantle(Player player) {
        if(level().isClientSide||isRemoved()||dismantled||cargoBusy()||player.level()!=level()||!player.isAlive()
            ||player.isSpectator()||!player.mayBuild()||!player.getMainHandItem().is(WagonContent.DISMANTLING_HAMMER.get()))return false;
        Vec3 eye=player.getEyePosition();var hit=pick(eye,eye.add(player.getLookAngle().scale(player.entityInteractionRange())));
        if(hit.isEmpty()||!level().mayInteract(player,BlockPos.containing(hit.get())))return false;
        var block=level().clip(new net.minecraft.world.level.ClipContext(eye,hit.get(),net.minecraft.world.level.ClipContext.Block.OUTLINE,
            net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        if(block.getType()!=HitResult.Type.MISS&&eye.distanceToSqr(block.getLocation())+.0001<eye.distanceToSqr(hit.get()))return false;
        dismantled=true;
        boolean drops=level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOENTITYDROPS);
        releasePassengers();detachAllHorses();cargo.destroy(drops,true);
        if(drops)parts().forEach((slot,part)->spawnAtLocation(material(slot).stack(WagonContent.PART_ITEMS.get(part).get())));
        player.getMainHandItem().hurtAndBreak(1,player,EquipmentSlot.MAINHAND);
        level().playSound(null,getX(),getY(),getZ(),net.minecraft.sounds.SoundEvents.WOOD_BREAK,net.minecraft.sounds.SoundSource.BLOCKS,1,.8F);
        discard();return true;
    }
    @Override public void remove(RemovalReason reason) {
        if(level()!=null&&!level().isClientSide) {
            cargo.closeMenus();
            if(reason.shouldDestroy()) { cargo.destroy(level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOENTITYDROPS));detachAllHorses(); }
        }
        super.remove(reason);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Materials",entityData.get(MATERIALS).copy());tag.put("Modules",entityData.get(MODULES).copy()); tag.putInt("WagonFacing",facing().get2DDataValue());
        tag.put("Cargo",cargo.save(level().registryAccess(),false));
        tag.put("Seats",entityData.get(SEATS).copy());
        tag.putFloat("Yaw",getYRot());tag.putFloat("Pitch",pitch);tag.putFloat("Roll",roll);tag.putBoolean("MotionStarted",motionStarted);
        tag.putFloat("ShaftPitch",shaftPitch);tag.putFloat("Steering",steering);
        for(int i=0;i<2;i++)if(horses[i]!=null) { tag.putUUID("Horse"+i,horses[i]);tag.putInt("Hanging"+i,hangingTicks[i]);if(Double.isFinite(horseContactHeights[i]))tag.putDouble("HorseContact"+i,horseContactHeights[i]); }
        for(int i=0;i<4;i++)tag.putFloat("Wheel"+i,wheels[i]);
        var simulation=new CompoundTag();physics.save(simulation);tag.put("Simulation",simulation);
        if (assemblyLock != null) tag.putLong("AssemblyLock",assemblyLock.asLong());
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        pushRequests.clear();pushTick=0;forwardInput=steeringInput=0;boostedDrive=false;inputDriver=null;lastInput=Long.MIN_VALUE;
        configure(tag.contains("Modules") ? decode(tag.getCompound("Modules")) : defaultParts(),
            Direction.from2DDataValue(tag.getInt("WagonFacing")));
        setMaterials(com.sange.tm_wagon.material.WagonMaterial.loadSlots(tag.getCompound("Materials")));
        cargo.load(tag.getCompound("Cargo"),level().registryAccess());rebuildGeometry();cargoChanged(true);
        assemblyLock=tag.contains("AssemblyLock") ? net.minecraft.core.BlockPos.of(tag.getLong("AssemblyLock")) : null;
        entityData.set(SEATS,tag.getCompound("Seats").copy());
        pitch=tag.getFloat("Pitch");roll=tag.getFloat("Roll");motionStarted=tag.getBoolean("MotionStarted");
        shaftPitch=tag.getFloat("ShaftPitch");steering=tag.getFloat("Steering");
        if(tag.contains("Yaw"))setYRot(tag.getFloat("Yaw"));
        for(int i=0;i<2;i++) { horses[i]=tag.hasUUID("Horse"+i)?tag.getUUID("Horse"+i):null;hangingTicks[i]=tag.getInt("Hanging"+i);horseContactHeights[i]=tag.contains("HorseContact"+i)?tag.getDouble("HorseContact"+i):Double.NaN; }
        for(int i=0;i<4;i++)wheels[i]=tag.getFloat("Wheel"+i);
        physics.load(tag.getCompound("Simulation"));worldBoxes=null;worldShape=null;setBoundingBox(makeBoundingBox());if(level()!=null)WagonSpatialIndex.update(this);syncMotion();
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
