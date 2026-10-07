package com.sange.tm_wagon.assembly;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

public class AssemblyFrameBlockEntity extends BlockEntity implements GeoBlockEntity,com.sange.tm_wagon.cargo.CargoOwner {
    public record Cell(List<AABB> boxes, Set<WagonSlot> slots, boolean framePart) {}
    private final EnumMap<WagonSlot, WagonPart> parts = new EnumMap<>(WagonSlot.class);
    private final EnumMap<WagonSlot,com.sange.tm_wagon.material.WagonMaterial> materials=new EnumMap<>(WagonSlot.class);
    public Map<WagonSlot,com.sange.tm_wagon.material.WagonMaterial> materials() { return Map.copyOf(materials); }
    public com.sange.tm_wagon.material.WagonMaterial material(WagonSlot slot) { return materials.getOrDefault(slot,com.sange.tm_wagon.material.WagonMaterial.DEFAULT); }
    public ItemStack partStack(WagonSlot slot) { return material(slot).stack(WagonContent.PART_ITEMS.get(part(slot)).get()); }
    private final com.sange.tm_wagon.cargo.CargoHold cargo=new com.sange.tm_wagon.cargo.CargoHold(this);
    private com.sange.tm_wagon.cargo.CargoHold layoutCargo;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Map<BlockPos, Cell> layout;
    private boolean changing;
    private boolean frameBuilt;
    private long motionStart = Long.MIN_VALUE;
    private double motionFrom;
    private boolean motionTargetExtended;
    private boolean collisionExtended;
    private int motionDuration = FrameMotion.TICKS;
    private UUID restoringWagon;
    private UUID restoringPlayer;
    // Future optional slots must not become assembly requirements accidentally.
    private static final Set<WagonSlot> REQUIRED = Set.of(WagonSlot.BODY,WagonSlot.SEAT,WagonSlot.SHAFTS,
        WagonSlot.FRONT_LEFT,WagonSlot.FRONT_RIGHT,WagonSlot.REAR_LEFT,WagonSlot.REAR_RIGHT);
    public static boolean complete(Map<WagonSlot,WagonPart> modules) {
        return REQUIRED.stream().allMatch(slot -> modules.containsKey(slot) && slot.accepts(modules.get(slot)))
            &&(modules.get(WagonSlot.SEAT).seatCapacity()<3||modules.get(WagonSlot.BODY)==WagonPart.WIDE_CARGO_BODY);
    }
    public boolean restoring(UUID wagon) { return wagon.equals(restoringWagon); }
    /** Remains locked until the final block state and collision have committed. */
    public boolean switching() { return motionStart!=Long.MIN_VALUE || restoringWagon!=null; }
    public boolean acceptsParts() { return collisionExtended && motionStart==Long.MIN_VALUE && restoringWagon==null; }
    private static final RawAnimation FOLD = RawAnimation.begin().thenPlayAndHold("animation.tm_wagon.frame_fold");
    private static final RawAnimation UNFOLD = RawAnimation.begin().thenPlayAndHold("animation.tm_wagon.frame_unfold");
    private static final RawAnimation EXTENDED = RawAnimation.begin().thenLoop("animation.tm_wagon.frame_extended");
    private static final RawAnimation FOLDED = RawAnimation.begin().thenLoop("animation.tm_wagon.frame_folded");

    public AssemblyFrameBlockEntity(BlockPos pos, BlockState state) {
        super(WagonContent.FRAME_ENTITY.get(),pos,state);
        collisionExtended = state.getValue(AssemblyFrameBlock.EXTENDED);
        motionTargetExtended = collisionExtended;
    }
    public Direction facing() { return getBlockState().getValue(AssemblyFrameBlock.FACING); }
    public boolean has(WagonSlot slot) { return parts.containsKey(slot); }
    public WagonPart part(WagonSlot slot) { return parts.get(slot); }
    public boolean changing() { return changing; }
    public Map<WagonSlot, WagonPart> parts() { return Map.copyOf(parts); }
    @Override public com.sange.tm_wagon.cargo.CargoHold cargo() { return cargo; }
    @Override public WagonPart cargoSeat() { return part(WagonSlot.SEAT); }
    @Override public WagonPart cargoBody() { return parts.getOrDefault(WagonSlot.BODY,WagonPart.CARGO_BODY); }
    @Override public net.minecraft.world.level.Level cargoLevel() { return level; }
    @Override public com.sange.tm_wagon.physics.WagonPose cargoPose() { return new com.sange.tm_wagon.physics.WagonPose(Vec3.atBottomCenterOf(worldPosition),facing().toYRot(),0,0); }
    @Override public boolean cargoLive() { return level!=null&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this&&has(WagonSlot.BODY); }
    @Override public boolean cargoBusy() { return changing||switching(); }
    @Override public String cargoGeometryChanged() { return initializeFrame(); }
    @Override public void cargoChanged(boolean visible) { if(visible)sync();else setChanged(); }
    public void onUnloaded() { cargo.closeMenus();com.sange.tm_wagon.compat.WagonShadowCasters.removed(this); }
    public void onLoaded() {
        com.sange.tm_wagon.compat.WagonShadowCasters.loaded(this);
    }
    @Override public void setRemoved() { cargo.closeMenus();com.sange.tm_wagon.compat.WagonShadowCasters.removed(this);super.setRemoved(); }

    /** Requested visual state; the block state and collision commit at the end. */
    public boolean extended() { return motionStart == Long.MIN_VALUE ? collisionExtended : motionTargetExtended; }
    public double frameProgress(double partialTick) {
        double target = extended() ? 0 : 1;
        if (motionStart == Long.MIN_VALUE) return target;
        if (level == null) return motionFrom;
        double t = Math.clamp((level.getGameTime()+partialTick-motionStart)/motionDuration,0,1);
        double eased = t*t*(3-2*t);
        return motionFrom+(target-motionFrom)*eased;
    }
    public boolean frameMoving() {
        return level != null && motionStart != Long.MIN_VALUE && level.getGameTime()-motionStart < motionDuration;
    }
    public Map<BlockPos,List<AABB>> frameCells() { return WagonGeometry.frameCells(facing(),collisionExtended?0:1); }

    public String toggleFrame(Player player) {
        if (level == null || level.isClientSide() || changing) return "message.tm_wagon.server_only";
        if (switching()) return "message.tm_wagon.assembly_busy";
        if(cargo.gateMoving())return "message.tm_wagon.assembly_busy";
        if (!parts.isEmpty()) {
            if (!acceptsParts()) return "message.tm_wagon.frame_extend_first";
            if (!complete(parts)) return "message.tm_wagon.incomplete_assembly";
            for (BlockPos pos : layout().keySet()) {
                if (!level.hasChunkAt(pos) || !owned(pos)) return "message.tm_wagon.blocked";
                if (player != null && !level.mayInteract(player,pos)) return "message.tm_wagon.protected";
            }
            Map<WagonSlot,WagonPart> modules=parts();var styles=materials();
            cargo.closeMenusForTransfer();
            long oldStart=motionStart; double oldFrom=motionFrom; int oldDuration=motionDuration;
            boolean oldTarget=motionTargetExtended;
            String error=animateFrame(player,null);
            if (error != null) return error;
            WagonEntity wagon=WagonContent.WAGON.get().create(level,net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            if (wagon != null) { wagon.configure(modules,facing());wagon.setMaterials(styles); wagon.setPos(Vec3.atBottomCenterOf(worldPosition)); }
            boolean spawned=wagon != null && level.addFreshEntity(wagon);
            error=spawned ? replaceModules(Map.of(),player,wagon,false) : "message.tm_wagon.spawn_failed";
            if (error != null) {
                if (spawned) wagon.discard();
                motionStart=oldStart; motionFrom=oldFrom; motionDuration=oldDuration; motionTargetExtended=oldTarget;
                initializeFrame(); return error;
            }
            cargo.transferTo(wagon.cargo());
            wagon.cargoGeometryChanged();
            return null;
        }
        if (motionStart==Long.MIN_VALUE && !collisionExtended) {
            WagonEntity wagon=nearbyWagon();
            if (wagon != null) {
                if (!complete(wagon.parts())) return "message.tm_wagon.incomplete_assembly";
                if (!wagon.lock(worldPosition)) return "message.tm_wagon.assembly_busy";
                restoringWagon=wagon.getUUID(); restoringPlayer=player==null ? null : player.getUUID();
                String error=animateFrame(player,wagon);
                if (error != null) { wagon.unlock(); restoringWagon=null; restoringPlayer=null; }
                return error;
            }
        }
        return animateFrame(player,null);
    }
    private WagonEntity nearbyWagon() {
        Vec3 center=Vec3.atBottomCenterOf(worldPosition);
        return level.getEntitiesOfClass(WagonEntity.class,new AABB(worldPosition).inflate(3),
                wagon -> !wagon.isRemoved() && reasonablePosition(wagon))
            .stream().min(java.util.Comparator.comparingDouble(wagon -> wagon.position().distanceToSqr(center))).orElse(null);
    }
    private boolean reasonablePosition(WagonEntity wagon) {
        Vec3 delta=wagon.position().subtract(Vec3.atBottomCenterOf(worldPosition));
        return delta.x*delta.x+delta.z*delta.z<=1.5*1.5 && Math.abs(delta.y)<=.75;
    }
    private String animateFrame(Player player,WagonEntity ignored) {
        if (level == null || level.isClientSide() || changing) return "message.tm_wagon.server_only";
        double from = frameProgress(0), target = extended() ? 1 : 0;
        var sweep = WagonGeometry.frameSweep(facing(),from,target);
        for (var entry : sweep.entrySet()) {
            var pos = worldPosition.offset(entry.getKey());
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return "message.tm_wagon.out_of_bounds";
            if (player != null && !level.mayInteract(player,pos)) return "message.tm_wagon.protected";
            var state = level.getBlockState(pos);
            if (!pos.equals(worldPosition) && !owned(pos) && (!state.canBeReplaced() || !state.getFluidState().isEmpty() || state.hasBlockEntity())) return "message.tm_wagon.blocked";
            List<AABB> oldBoxes = pos.equals(worldPosition) ? frameCells().getOrDefault(BlockPos.ZERO,List.of())
                : layout().containsKey(pos) ? layout().get(pos).boxes : List.of();
            var added = net.minecraft.world.phys.shapes.Shapes.join(WagonGeometry.shape(entry.getValue()),WagonGeometry.shape(oldBoxes),net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);
            if (!unobstructed(added.move(pos.getX(),pos.getY(),pos.getZ()),ignored)) return "message.tm_wagon.entity_blocked";
        }
        long oldStart = motionStart; double oldFrom = motionFrom; int oldDuration = motionDuration;
        boolean oldTarget = motionTargetExtended;
        motionFrom = from; motionStart = level.getGameTime(); motionDuration = Math.max(1,(int)Math.round(FrameMotion.TICKS*Math.abs(target-from)));
        // One BE update carries the target, start pose and timeline together.
        // Sending a block-state update first can briefly expose the endpoint.
        motionTargetExtended = target == 0;
        String error = initializeFrame();
        if (error != null) {
            motionStart = oldStart; motionFrom = oldFrom; motionDuration = oldDuration;
            motionTargetExtended = oldTarget; return error;
        }
        level.playSound(null,worldPosition,net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_CLOSE,net.minecraft.sounds.SoundSource.BLOCKS,.65F,extended()?1.15F:.85F);
        return null;
    }
    public void tickFrame() {
        if (level == null || level.isClientSide() || changing || motionStart == Long.MIN_VALUE || frameMoving()) return;
        long previousStart = motionStart;
        boolean previousCollision = collisionExtended;
        collisionExtended = motionTargetExtended;
        motionStart = Long.MIN_VALUE;
        String error = initializeFrame();
        if (error != null) {
            collisionExtended = previousCollision;
            motionStart = previousStart;
            return;
        }
        level.setBlock(worldPosition,getBlockState().setValue(AssemblyFrameBlock.EXTENDED,collisionExtended),3);
        if (collisionExtended && restoringWagon != null) finishRestoration();
    }

    private void finishRestoration() {
        if (!(level instanceof ServerLevel server)) return;
        Entity found=server.getEntity(restoringWagon);
        WagonEntity wagon=found instanceof WagonEntity value ? value : null;
        Player player=restoringPlayer==null ? null : server.getPlayerByUUID(restoringPlayer);
        // Preview the source's actual cargo/gate geometry without copying inventory ownership.
        layoutCargo=wagon==null?null:wagon.cargo();
        String error;
        try {
            error=restoringPlayer!=null && player==null ? "message.tm_wagon.protected"
                : wagon==null || !reasonablePosition(wagon) ? "message.tm_wagon.wagon_missing"
                : !complete(wagon.parts()) ? "message.tm_wagon.incomplete_assembly"
                : replaceModules(wagon.parts(),player,wagon,true);
        } finally { layoutCargo=null; }
        restoringWagon=null; restoringPlayer=null;
        if (wagon != null) wagon.unlock();
        if (error == null) {
            materials.clear();materials.putAll(wagon.materials());
            wagon.cargo().transferTo(cargo);
            wagon.releasePassengers();
            var attached=new java.util.ArrayList<net.minecraft.world.entity.animal.equine.AbstractHorse>();
            for(int i=0;i<wagon.horseCapacity();i++)if(wagon.horse(i)!=null)attached.add(wagon.horse(i));
            wagon.detachAllHorses();
            for(var horse:attached)if(!level.noCollision(horse,horse.getBoundingBox())) {
                Vec3 p=wagon.safeDismount(horse);horse.teleportTo(p.x,p.y,p.z);
            }
            wagon.discard();
        } else {
            if (player != null) player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.tm_wagon.restore_failed",
                net.minecraft.network.chat.Component.translatable(error)));
            // Return to the folded state even if a new entity entered the lift area.
            motionFrom=0; motionStart=level.getGameTime(); motionDuration=FrameMotion.TICKS; motionTargetExtended=false;
            initializeFrame();
        }
        sync();
    }
    private boolean unobstructed(net.minecraft.world.phys.shapes.VoxelShape shape,Entity ignored) {
        if (shape.isEmpty()) return true;
        for (Entity entity : level.getEntities((Entity)null,shape.bounds())) {
            if (entity.isRemoved() || !entity.blocksBuilding || entity==ignored || (ignored!=null && entity.isPassengerOfSameVehicle(ignored))
                || (ignored instanceof WagonEntity wagon && wagon.hasHorse(entity.getUUID()))) continue;
            if(entity instanceof WagonEntity wagon) {
                if(shape.toAabbs().stream().anyMatch(wagon::intersects))return false;
            } else if(net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(shape,net.minecraft.world.phys.shapes.Shapes.create(entity.getBoundingBox()),net.minecraft.world.phys.shapes.BooleanOp.AND))return false;
        }
        return true;
    }

    public static AssemblyFrameBlockEntity find(BlockGetter level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof AssemblyFrameBlockEntity frame) return frame;
        if (entity instanceof AssemblyCellBlockEntity cell) {
            if (level instanceof net.minecraft.world.level.LevelReader reader && !reader.hasChunkAt(cell.owner())) return null;
            if (level.getBlockEntity(cell.owner()) instanceof AssemblyFrameBlockEntity frame) return frame;
        }
        return null;
    }

    private Map<BlockPos, Cell> buildLayout(Map<WagonSlot, WagonPart> modules) {
        Map<BlockPos, Cell> result = new HashMap<>();
        frameCells().forEach((relative, boxes) -> {
            BlockPos pos = worldPosition.offset(relative);
            if (!pos.equals(worldPosition)) result.put(pos,new Cell(new ArrayList<>(boxes),EnumSet.noneOf(WagonSlot.class),true));
        });
        if (frameMoving()) WagonGeometry.frameSweep(facing(),motionFrom,extended()?0:1).forEach((relative,boxes) -> {
            BlockPos pos=worldPosition.offset(relative);
            if (!pos.equals(worldPosition)) result.computeIfAbsent(pos,ignored -> new Cell(new ArrayList<>(),EnumSet.noneOf(WagonSlot.class),true));
        });
        modules.forEach((slot, part) -> (slot==WagonSlot.BODY
            ?WagonGeometry.customCells((layoutCargo==null?cargo:layoutCargo).bodyBoxes(part),facing())
            :WagonGeometry.cells(part,slot,facing(),modules.getOrDefault(WagonSlot.BODY,WagonPart.CARGO_BODY))).forEach((relative, boxes) -> {
            BlockPos pos = worldPosition.offset(relative);
            if (pos.equals(worldPosition)) return; // The root contains the lower support; the platform uses proxy cells.
            Cell cell = result.computeIfAbsent(pos, ignored -> new Cell(new ArrayList<>(), EnumSet.noneOf(WagonSlot.class),false));
            cell.boxes.addAll(boxes); cell.slots.add(slot);
        }));
        if(modules.containsKey(WagonSlot.BODY)) {
            // Covers and decorative canopy rims stay clickable without solid colliders.
            var hold=layoutCargo==null?cargo:layoutCargo;
            for(var selection:List.of(hold.cover().selectionCells(modules.get(WagonSlot.BODY),facing()),
                hold.canopy().selectionCells(modules.get(WagonSlot.BODY),facing())))selection.forEach((relative,boxes)->{
                BlockPos pos=worldPosition.offset(relative);if(pos.equals(worldPosition))return;
                result.computeIfAbsent(pos,ignored->new Cell(new ArrayList<>(),EnumSet.noneOf(WagonSlot.class),false)).slots.add(WagonSlot.BODY);
            });
        }
        return result;
    }
    public Map<BlockPos, Cell> layout() {
        if (layout == null) layout = buildLayout(parts);
        return layout;
    }
    /** Creates the platform cells for a newly placed frame. */
    public void ensureFrame() { if (!frameBuilt) initializeFrame(); }
    public String initializeFrame() {
        return replaceModules(parts,null,null,false);
    }
    /** Atomic full-layout replacement used by both directions of conversion. */
    private String replaceModules(Map<WagonSlot,WagonPart> modules,Player player,Entity ignored,boolean checkEntities) {
        if (level == null || level.isClientSide() || changing) return null;
        var next=new EnumMap<WagonSlot,WagonPart>(WagonSlot.class); next.putAll(modules);
        Map<BlockPos,Cell> desired = buildLayout(next);
        for (BlockPos pos : desired.keySet()) {
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return "message.tm_wagon.out_of_bounds";
            var state = level.getBlockState(pos);
            if (!owned(pos) && (!state.canBeReplaced() || !state.getFluidState().isEmpty() || state.hasBlockEntity())) return "message.tm_wagon.blocked";
            if (player != null && !level.mayInteract(player,pos)) return "message.tm_wagon.protected";
            if (checkEntities && !unobstructed(WagonGeometry.shape(desired.get(pos).boxes).move(pos.getX(),pos.getY(),pos.getZ()),ignored)) return "message.tm_wagon.entity_blocked";
        }
        Map<BlockPos,BlockState> snapshot = new HashMap<>();
        Map<BlockPos,CompoundTag> tags = new HashMap<>();
        var affected = new java.util.HashSet<>(layout().keySet()); affected.addAll(desired.keySet());
        affected.forEach(pos -> {
            snapshot.put(pos,level.getBlockState(pos));
            var entity = level.getBlockEntity(pos);
            if (entity != null) tags.put(pos,entity.saveWithFullMetadata(level.registryAccess()));
        });
        changing = true;
        try { apply(desired,next,true); parts.clear(); parts.putAll(next);materials.keySet().retainAll(next.keySet()); layout = desired; frameBuilt = true; }
        catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().error("Frame placement failed at {}",worldPosition,failure);
            for (var entry : snapshot.entrySet()) {
                level.setBlock(entry.getKey(),entry.getValue(),3);
                var entity = level.getBlockEntity(entry.getKey());
                if (entity != null && tags.containsKey(entry.getKey())) com.sange.tm_wagon.cargo.CargoNbt.load(entity,tags.get(entry.getKey()),level.registryAccess());
            }
            return "message.tm_wagon.blocked";
        }
        finally { changing = false; }
        sync();
        return null;
    }
    public void validateLoadedCells() {
        if (level == null || changing) return;
        if (!frameBuilt && initializeFrame() != null) return;
        Set<WagonSlot> missing = EnumSet.noneOf(WagonSlot.class);
        for (var entry : layout().entrySet()) {
            if (level.hasChunkAt(entry.getKey()) && !owned(entry.getKey())) {
                if (entry.getValue().framePart) { dismantle(true,true); return; }
                missing.addAll(entry.getValue().slots);
            }
        }
        if (!missing.isEmpty()) remove(missing,true);
    }
    private boolean owned(BlockPos pos) {
        return level != null && level.getBlockState(pos).getBlock() instanceof AssemblyPartBlock
            && level.getBlockEntity(pos) instanceof AssemblyCellBlockEntity cell && cell.owner().equals(worldPosition);
    }

    /** Returns a translated error key or null; no state changes occur on failure. */
    public String install(WagonSlot slot, WagonPart part, Player player, ItemStack stack) {
        if (level == null || level.isClientSide()) return "message.tm_wagon.server_only";
        if (!acceptsParts()) return "message.tm_wagon.frame_extend_first";
        if (!slot.accepts(part)||part.seatCapacity()==3&&cargoBody()!=WagonPart.WIDE_CARGO_BODY) return "message.tm_wagon.wrong_slot";
        if (parts.containsKey(slot)) return "message.tm_wagon.occupied";
        if (slot != WagonSlot.BODY && !has(WagonSlot.BODY)) return "message.tm_wagon.body_required";
        if (stack.isEmpty() || stack.getItem() != WagonContent.PART_ITEMS.get(part).get()) return "message.tm_wagon.invalid_item";
        if (player != null && !level.mayInteract(player,worldPosition)) return "message.tm_wagon.protected";
        var next = new EnumMap<>(parts); next.put(slot, part);
        Map<BlockPos, Cell> proposed = buildLayout(next);
        // Validate every cell before replacing even a single block or consuming an item.
        for (var entry : proposed.entrySet()) {
            BlockPos pos = entry.getKey();
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)) return "message.tm_wagon.out_of_bounds";
            if (player != null && (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, stack))) return "message.tm_wagon.protected";
            BlockState state = level.getBlockState(pos);
            if (!owned(pos) && (!state.canBeReplaced() || !state.getFluidState().isEmpty() || state.hasBlockEntity())) return "message.tm_wagon.blocked";
            // Check new volume even when another module already uses this cell.
            var previous = layout().containsKey(pos) ? WagonGeometry.shape(layout().get(pos).boxes) : net.minecraft.world.phys.shapes.Shapes.empty();
            var added = net.minecraft.world.phys.shapes.Shapes.join(WagonGeometry.shape(entry.getValue().boxes),previous,net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);
            if (!level.isUnobstructed(null,added.move(pos.getX(),pos.getY(),pos.getZ()))) return "message.tm_wagon.entity_blocked";
        }
        Map<BlockPos, BlockState> snapshot = new HashMap<>();
        Map<BlockPos, CompoundTag> tags = new HashMap<>();
        proposed.keySet().forEach(pos -> {
            snapshot.put(pos, level.getBlockState(pos));
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null) tags.put(pos, entity.saveWithFullMetadata(level.registryAccess()));
        });
        changing = true;
        try {
            apply(proposed, next, true);
            parts.clear(); parts.putAll(next); layout = proposed;
        } catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().error("Assembly placement failed at {}",worldPosition,failure);
            for (var entry : snapshot.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue(), 3);
                BlockEntity entity = level.getBlockEntity(entry.getKey());
                if (entity != null && tags.containsKey(entry.getKey())) com.sange.tm_wagon.cargo.CargoNbt.load(entity,tags.get(entry.getKey()),level.registryAccess());
            }
            return "message.tm_wagon.blocked";
        } finally { changing = false; }
        materials.put(slot,com.sange.tm_wagon.material.WagonMaterial.forPart(part,stack));
        if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        sync();
        return null;
    }

    private void apply(Map<BlockPos, Cell> desired, Map<WagonSlot, WagonPart> modules, boolean placing) {
        // Remove only our own cells; externally replaced blocks are preserved.
        for (BlockPos old : layout().keySet()) {
            if (!desired.containsKey(old) && level.hasChunkAt(old) && owned(old)) level.setBlock(old, Blocks.AIR.defaultBlockState(), 3);
        }
        for (var entry : desired.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!level.hasChunkAt(pos)) continue;
            if (!placing && !owned(pos)) continue;
            BlockState state = WagonContent.PROXY.get().defaultBlockState();
            for (var module : modules.entrySet()) {
                if (module.getKey().position(worldPosition,facing(),modules.getOrDefault(WagonSlot.BODY,WagonPart.CARGO_BODY)).equals(pos)) {
                    state = WagonContent.PART_BLOCKS.get(module.getValue()).get().defaultBlockState(); break;
                }
            }
            if (level.getBlockState(pos) != state && !level.setBlock(pos, state, 3)) throw new IllegalStateException("Rejected assembly placement");
            if (!(level.getBlockEntity(pos) instanceof AssemblyCellBlockEntity cell)) throw new IllegalStateException("Missing assembly cell");
            cell.configure(worldPosition, entry.getValue().boxes);
        }
    }

    public void remove(Set<WagonSlot> requested, boolean drops) {
        if (level == null || level.isClientSide() || changing) return;
        Set<WagonSlot> removing = EnumSet.noneOf(WagonSlot.class); removing.addAll(requested);
        if (removing.contains(WagonSlot.BODY)) removing.addAll(parts.keySet());
        var cabinetItem=cargo.cabinet().material().stack(WagonContent.CABINET.get());
        boolean returnCabinet=!removing.contains(WagonSlot.BODY)&&removing.contains(WagonSlot.SEAT)&&cargo.cabinet().installed();
        if(removing.contains(WagonSlot.BODY))cargo.destroy(((net.minecraft.server.level.ServerLevel)level).getGameRules().get(GameRules.BLOCK_DROPS));
        else if(removing.contains(WagonSlot.SEAT))cargo.cabinet().destroy(((net.minecraft.server.level.ServerLevel)level).getGameRules().get(GameRules.BLOCK_DROPS));
        var next = new EnumMap<>(parts);
        var removed = new ArrayList<ItemStack>();
        for (WagonSlot slot : removing) { if(next.containsKey(slot))removed.add(partStack(slot));next.remove(slot); }
        Map<BlockPos, Cell> desired = buildLayout(next);
        changing = true;
        try { apply(desired, next, false); parts.clear(); parts.putAll(next);materials.keySet().retainAll(next.keySet()); layout = desired; }
        finally { changing = false; }
        if (drops && ((net.minecraft.server.level.ServerLevel)level).getGameRules().get(GameRules.BLOCK_DROPS)) {
            if(returnCabinet)Block.popResource(level,worldPosition.above(),cabinetItem);
            for (ItemStack stack : removed) Block.popResource(level, worldPosition.above(), stack);
        }
        sync();
    }

    public void dismantle(boolean drops, boolean removeFrame) {
        if (level == null || level.isClientSide() || changing) return;
        var removed = parts.keySet().stream().map(this::partStack).toList();
        changing = true;
        try {
            cargo.destroy(((net.minecraft.server.level.ServerLevel)level).getGameRules().get(GameRules.BLOCK_DROPS));
            for (BlockPos pos : layout().keySet()) if (level.hasChunkAt(pos) && owned(pos)) level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            parts.clear();materials.clear(); layout = Map.of();
            if (removeFrame) level.setBlock(worldPosition,Blocks.AIR.defaultBlockState(),3);
        } finally { changing = false; }
        if (drops && ((net.minecraft.server.level.ServerLevel)level).getGameRules().get(GameRules.BLOCK_DROPS)) {
            for (ItemStack stack : removed) Block.popResource(level,worldPosition.above(),stack);
            if (removeFrame) Block.popResource(level,worldPosition,new ItemStack(WagonContent.FRAME_ITEM.get()));
        }
    }

    public boolean platformTop(net.minecraft.world.phys.BlockHitResult hit) {
        if (!extended() || frameMoving()) return false;
        if (hit.getDirection() != Direction.UP) return false;
        List<AABB> boxes = frameCells().get(hit.getBlockPos().subtract(worldPosition));
        if (boxes == null) return false;
        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos()));
        for (AABB box : boxes) {
            if (Math.abs(hit.getBlockPos().getY()+box.maxY-worldPosition.getY()-1.375) < 1e-6
                && Math.abs(local.y-box.maxY)<.002 && local.x>=box.minX-.002 && local.x<=box.maxX+.002
                && local.z>=box.minZ-.002 && local.z<=box.maxZ+.002) return true;
        }
        return false;
    }

    /** A ray against each module resolves shared cells without deleting neighbours. */
    public WagonSlot hitSlot(BlockPos pos, Vec3 eye, Vec3 end) {
        WagonSlot found = null; double distance = Double.POSITIVE_INFINITY;
        for (var module : parts.entrySet()) {
            List<AABB> boxes = WagonGeometry.cells(module.getValue(),module.getKey(),facing(),cargoBody()).get(pos.subtract(worldPosition));
            if (boxes == null) continue;
            var hit = WagonGeometry.shape(boxes).clip(eye, end, pos);
            if (hit != null && hit.getLocation().distanceToSqr(eye) < distance) {
                found = module.getKey(); distance = hit.getLocation().distanceToSqr(eye);
            }
        }
        List<AABB> support = frameCells().get(pos.subtract(worldPosition));
        if (support != null) {
            var hit = WagonGeometry.shape(support).clip(eye,end,pos);
            if (hit != null && hit.getLocation().distanceToSqr(eye) < distance) return null;
        }
        if (found == null && layout().containsKey(pos) && !layout().get(pos).framePart && !layout().get(pos).slots.isEmpty()) found = layout().get(pos).slots.iterator().next();
        return found;
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    private void saveWagonData(CompoundTag tag, HolderLookup.Provider registries) {
        
        saveFrame(tag,registries,false);
    }
    private void saveFrame(CompoundTag tag,HolderLookup.Provider registries,boolean visual) {
        tag.putBoolean("FrameBuilt",frameBuilt);
        tag.putBoolean("CollisionExtended",collisionExtended);
        tag.putBoolean("MotionTargetExtended",motionTargetExtended);
        if (restoringWagon != null) tag.store("RestoringWagon",net.minecraft.core.UUIDUtil.CODEC,restoringWagon);
        if (restoringPlayer != null) tag.store("RestoringPlayer",net.minecraft.core.UUIDUtil.CODEC,restoringPlayer);
        tag.putLong("MotionStart",motionStart); tag.putDouble("MotionFrom",motionFrom); tag.putInt("MotionDuration",motionDuration);
        CompoundTag modules = new CompoundTag();
        parts.forEach((slot, part) -> modules.putString(slot.name(), part.name())); tag.put("Modules", modules);
        tag.put("Materials",com.sange.tm_wagon.material.WagonMaterial.save(materials));
        tag.put("Cargo",cargo.save(registries,visual));
    }
    private void loadWagonData(CompoundTag tag, HolderLookup.Provider registries) {
         parts.clear();materials.clear();materials.putAll(com.sange.tm_wagon.material.WagonMaterial.loadSlots(tag.getCompoundOrEmpty("Materials")));
        CompoundTag modules = tag.getCompoundOrEmpty("Modules");
        for (WagonSlot slot : WagonSlot.values()) {
            if (modules.contains(slot.name())) {
                try { WagonPart part = WagonPart.valueOf(modules.getStringOr(slot.name(),"")); if (slot.accepts(part)) parts.put(slot, part); }
                catch (IllegalArgumentException ignored) { /* Discard invalid module identifiers. */ }
            }
        }
        frameBuilt = tag.getBooleanOr("FrameBuilt",false);
        motionStart = tag.getLongOr("MotionStart",0L);
        motionFrom = Math.clamp(tag.getDoubleOr("MotionFrom",0D),0,1);
        motionDuration = Math.max(1,tag.getIntOr("MotionDuration",0));
        collisionExtended = tag.getBooleanOr("CollisionExtended",false);
        motionTargetExtended = tag.getBooleanOr("MotionTargetExtended",false);
        restoringWagon=tag.read("RestoringWagon",net.minecraft.core.UUIDUtil.CODEC).isPresent() ? tag.read("RestoringWagon",net.minecraft.core.UUIDUtil.CODEC).orElseThrow() : null;
        restoringPlayer=tag.read("RestoringPlayer",net.minecraft.core.UUIDUtil.CODEC).isPresent() ? tag.read("RestoringPlayer",net.minecraft.core.UUIDUtil.CODEC).orElseThrow() : null;
        cargo.load(tag.getCompoundOrEmpty("Cargo"),registries);
        layout = null;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag=new CompoundTag();saveFrame(tag,registries,true);var result=new CompoundTag();result.put("Wagon",tag);return result;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("frame_lift",0,state -> state.setAndContinue(
            frameMoving() ? (extended()?UNFOLD:FOLD) : (extended()?EXTENDED:FOLDED))));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        var tag=new CompoundTag();saveWagonData(tag,level.registryAccess());output.store("Wagon",CompoundTag.CODEC,tag);
    }
    @Override protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        loadWagonData(input.read("Wagon",CompoundTag.CODEC).orElseGet(CompoundTag::new),input.lookup());
    }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state) {
        if(level==null||level.isClientSide())return;
        if(!changing()) {
            dismantle(true,false);
            Block.popResource(level,pos,new ItemStack(WagonContent.FRAME_ITEM.get()));
        }
    }
}
