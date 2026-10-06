package com.sange.tm_wagon.assembly;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Collision is stored locally so it survives an unloaded controller chunk. */
public class AssemblyCellBlockEntity extends BlockEntity {
    private BlockPos owner = BlockPos.ZERO;
    private BlockPos source;
    private List<AABB> boxes = List.of();
    private VoxelShape shape = Shapes.empty();

    public AssemblyCellBlockEntity(BlockPos pos, BlockState state) { super(WagonContent.CELL_ENTITY.get(), pos, state); }
    public BlockPos owner() { return owner; }
    public BlockPos source() { return source==null?worldPosition:source; }
    @Override public void onLoad() {
        super.onLoad();com.sange.tm_wagon.compat.StructureAssemblyGuard.loaded(this);
    }
    public VoxelShape shape() { return shape; }
    public void configure(BlockPos owner, List<AABB> boxes) {
        if (this.owner.equals(owner) && this.boxes.equals(boxes)) return;
        this.owner = owner.immutable(); this.source=worldPosition; this.boxes = List.copyOf(boxes); this.shape = WagonGeometry.shape(boxes);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putLong("Owner", owner.asLong());
        tag.putLong("Source",source().asLong());
        ListTag list = new ListTag();
        for (AABB b : boxes) {
            ListTag values = new ListTag();
            for (double v : new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ}) values.add(DoubleTag.valueOf(v));
            list.add(values);
        }
        tag.put("Boxes", list);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); owner = BlockPos.of(tag.getLong("Owner"));
        source=BlockPos.of(tag.getLong("Source"));
        List<AABB> loaded = new ArrayList<>();
        for (Tag entry : tag.getList("Boxes", Tag.TAG_LIST)) {
            ListTag v = (ListTag) entry;
            if (v.size() == 6) loaded.add(new AABB(v.getDouble(0),v.getDouble(1),v.getDouble(2),v.getDouble(3),v.getDouble(4),v.getDouble(5)));
        }
        boxes = List.copyOf(loaded); shape = WagonGeometry.shape(boxes);
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
