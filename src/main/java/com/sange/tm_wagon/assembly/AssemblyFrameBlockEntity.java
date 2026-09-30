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
import software.bernie.geckolib.util.GeckoLibUtil;

public class AssemblyFrameBlockEntity extends BlockEntity implements GeoBlockEntity {
    public record Cell(List<AABB> boxes, Set<WagonSlot> slots) {}
    private final EnumMap<WagonSlot, WagonPart> parts = new EnumMap<>(WagonSlot.class);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Map<BlockPos, Cell> layout;
    private boolean changing;

    public AssemblyFrameBlockEntity(BlockPos pos, BlockState state) { super(WagonContent.FRAME_ENTITY.get(), pos, state); }
    public Direction facing() { return getBlockState().getValue(AssemblyFrameBlock.FACING); }
    public boolean has(WagonSlot slot) { return parts.containsKey(slot); }
    public WagonPart part(WagonSlot slot) { return parts.get(slot); }
    public boolean changing() { return changing; }
    public Map<WagonSlot, WagonPart> parts() { return Map.copyOf(parts); }

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
        modules.forEach((slot, part) -> WagonGeometry.cells(part, slot, facing()).forEach((relative, boxes) -> {
            BlockPos pos = worldPosition.offset(relative);
            if (pos.equals(worldPosition)) return; // The frame itself keeps its one-block collision.
            Cell cell = result.computeIfAbsent(pos, ignored -> new Cell(new ArrayList<>(), EnumSet.noneOf(WagonSlot.class)));
            cell.boxes.addAll(boxes); cell.slots.add(slot);
        }));
        return result;
    }
    public Map<BlockPos, Cell> layout() {
        if (layout == null) layout = buildLayout(parts);
        return layout;
    }
    public void validateLoadedCells() {
        if (level == null || changing) return;
        Set<WagonSlot> missing = EnumSet.noneOf(WagonSlot.class);
        layout().forEach((pos,cell) -> { if (level.hasChunkAt(pos) && !owned(pos)) missing.addAll(cell.slots); });
        if (!missing.isEmpty()) remove(missing,true);
    }
    private boolean owned(BlockPos pos) {
        return level != null && level.getBlockState(pos).getBlock() instanceof AssemblyPartBlock
            && level.getBlockEntity(pos) instanceof AssemblyCellBlockEntity cell && cell.owner().equals(worldPosition);
    }

    /** Returns a translated error key or null; no state changes occur on failure. */
    public String install(WagonSlot slot, WagonPart part, Player player, ItemStack stack) {
        if (level == null || level.isClientSide) return "message.tm_wagon.server_only";
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
        remove(EnumSet.allOf(WagonSlot.class), drops);
        if (level == null || !removeFrame) return;
        changing = true;
        try { level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), 3); }
        finally { changing = false; }
        if (drops && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) Block.popResource(level, worldPosition, new ItemStack(WagonContent.FRAME_ITEM.get()));
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
        if (found == null && layout().containsKey(pos)) found = layout().get(pos).slots.iterator().next();
        return found;
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
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
        layout = null;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
