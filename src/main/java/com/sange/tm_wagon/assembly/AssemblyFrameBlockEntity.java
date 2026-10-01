package com.sange.tm_wagon.assembly;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class AssemblyFrameBlockEntity extends BlockEntity implements GeoBlockEntity {
    public record Cell(List<AABB> boxes, Set<WagonSlot> slots, boolean framePart) {}
    private final EnumMap<WagonSlot, WagonPart> parts = new EnumMap<>(WagonSlot.class);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Map<BlockPos, Cell> layout;
    private boolean changing;
    private boolean frameBuilt;
    private long motionStart = Long.MIN_VALUE;
    private double motionFrom;
    private boolean motionTargetExtended;
    private boolean collisionExtended;
    private int motionDuration = FrameMotion.TICKS;
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
        if (level == null || level.isClientSide || changing) return "message.tm_wagon.server_only";
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
            if (!level.isUnobstructed(null,added.move(pos.getX(),pos.getY(),pos.getZ()))) return "message.tm_wagon.entity_blocked";
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
        if (level == null || level.isClientSide || changing || motionStart == Long.MIN_VALUE || frameMoving()) return;
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
        modules.forEach((slot, part) -> WagonGeometry.cells(part, slot, facing()).forEach((relative, boxes) -> {
            BlockPos pos = worldPosition.offset(relative);
            if (pos.equals(worldPosition)) return; // The root contains the lower support; the platform uses proxy cells.
            Cell cell = result.computeIfAbsent(pos, ignored -> new Cell(new ArrayList<>(), EnumSet.noneOf(WagonSlot.class),false));
            cell.boxes.addAll(boxes); cell.slots.add(slot);
        }));
        return result;
    }
    public Map<BlockPos, Cell> layout() {
        if (layout == null) layout = buildLayout(parts);
        return layout;
    }
    /** Also upgrades older controllers whose platform did not have world cells. */
    public void ensureFrame() { if (!frameBuilt) initializeFrame(); }
    public String initializeFrame() {
        if (level == null || level.isClientSide || changing) return null;
        Map<BlockPos,Cell> desired = buildLayout(parts);
        for (BlockPos pos : desired.keySet()) {
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return "message.tm_wagon.out_of_bounds";
            var state = level.getBlockState(pos);
            if (!owned(pos) && (!state.canBeReplaced() || !state.getFluidState().isEmpty() || state.hasBlockEntity())) return "message.tm_wagon.blocked";
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
        try { apply(desired,parts,true); layout = desired; frameBuilt = true; }
        catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().error("Frame placement failed at {}",worldPosition,failure);
            for (var entry : snapshot.entrySet()) {
                level.setBlock(entry.getKey(),entry.getValue(),3);
                var entity = level.getBlockEntity(entry.getKey());
                if (entity != null && tags.containsKey(entry.getKey())) entity.loadWithComponents(tags.get(entry.getKey()),level.registryAccess());
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
        if (level == null || level.isClientSide) return "message.tm_wagon.server_only";
        if (slot == WagonSlot.BODY && (!extended() || frameMoving())) return "message.tm_wagon.frame_extend_first";
        if (!slot.accepts(part)) return "message.tm_wagon.wrong_slot";
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
                if (entity != null && tags.containsKey(entry.getKey())) entity.loadWithComponents(tags.get(entry.getKey()), level.registryAccess());
            }
            return "message.tm_wagon.blocked";
        } finally { changing = false; }
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
                if (module.getKey().position(worldPosition, facing()).equals(pos)) {
                    state = WagonContent.PART_BLOCKS.get(module.getValue()).get().defaultBlockState(); break;
                }
            }
            if (level.getBlockState(pos) != state && !level.setBlock(pos, state, 3)) throw new IllegalStateException("Rejected assembly placement");
            if (!(level.getBlockEntity(pos) instanceof AssemblyCellBlockEntity cell)) throw new IllegalStateException("Missing assembly cell");
            cell.configure(worldPosition, entry.getValue().boxes);
        }
    }

    public void remove(Set<WagonSlot> requested, boolean drops) {
        if (level == null || level.isClientSide || changing) return;
        Set<WagonSlot> removing = EnumSet.noneOf(WagonSlot.class); removing.addAll(requested);
        if (removing.contains(WagonSlot.BODY)) removing.addAll(parts.keySet());
        var next = new EnumMap<>(parts);
        var removed = new ArrayList<WagonPart>();
        for (WagonSlot slot : removing) { WagonPart part = next.remove(slot); if (part != null) removed.add(part); }
        Map<BlockPos, Cell> desired = buildLayout(next);
        changing = true;
        try { apply(desired, next, false); parts.clear(); parts.putAll(next); layout = desired; }
        finally { changing = false; }
        if (drops && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            for (WagonPart part : removed) Block.popResource(level, worldPosition.above(), new ItemStack(WagonContent.PART_ITEMS.get(part).get()));
        }
        sync();
    }

    public void dismantle(boolean drops, boolean removeFrame) {
        if (level == null || level.isClientSide || changing) return;
        var removed = new ArrayList<>(parts.values());
        changing = true;
        try {
            for (BlockPos pos : layout().keySet()) if (level.hasChunkAt(pos) && owned(pos)) level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            parts.clear(); layout = Map.of();
            if (removeFrame) level.setBlock(worldPosition,Blocks.AIR.defaultBlockState(),3);
        } finally { changing = false; }
        if (drops && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            for (WagonPart part : removed) Block.popResource(level,worldPosition.above(),new ItemStack(WagonContent.PART_ITEMS.get(part).get()));
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
            List<AABB> boxes = WagonGeometry.cells(module.getValue(), module.getKey(), facing()).get(pos.subtract(worldPosition));
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
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("FrameBuilt",frameBuilt);
        tag.putBoolean("CollisionExtended",collisionExtended);
        tag.putBoolean("MotionTargetExtended",motionTargetExtended);
        tag.putLong("MotionStart",motionStart); tag.putDouble("MotionFrom",motionFrom); tag.putInt("MotionDuration",motionDuration);
        CompoundTag modules = new CompoundTag();
        parts.forEach((slot, part) -> modules.putString(slot.name(), part.name())); tag.put("Modules", modules);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); parts.clear();
        CompoundTag modules = tag.getCompound("Modules");
        for (WagonSlot slot : WagonSlot.values()) {
            if (modules.contains(slot.name())) {
                try { WagonPart part = WagonPart.valueOf(modules.getString(slot.name())); if (slot.accepts(part)) parts.put(slot, part); }
                catch (IllegalArgumentException ignored) { /* Ignore unknown parts from incompatible saves. */ }
            }
        }
        frameBuilt = tag.getBoolean("FrameBuilt");
        motionStart = tag.contains("MotionStart") ? tag.getLong("MotionStart") : Long.MIN_VALUE;
        motionFrom = Math.clamp(tag.getDouble("MotionFrom"),0,1);
        motionDuration = Math.max(1,tag.getInt("MotionDuration"));
        collisionExtended = tag.contains("CollisionExtended") ? tag.getBoolean("CollisionExtended")
            : motionStart == Long.MIN_VALUE ? getBlockState().getValue(AssemblyFrameBlock.EXTENDED) : motionFrom < .5;
        motionTargetExtended = tag.contains("MotionTargetExtended") ? tag.getBoolean("MotionTargetExtended")
            : getBlockState().getValue(AssemblyFrameBlock.EXTENDED);
        layout = null;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this,"frame_lift",0,state -> state.setAndContinue(
            frameMoving() ? (extended()?UNFOLD:FOLD) : (extended()?EXTENDED:FOLDED))));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
