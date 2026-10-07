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
    }
    public VoxelShape shape() { return shape; }
    public void configure(BlockPos owner, List<AABB> boxes) {
        if (this.owner.equals(owner) && this.boxes.equals(boxes)) return;
        this.owner = owner.immutable(); this.source=worldPosition; this.boxes = List.copyOf(boxes); this.shape = WagonGeometry.shape(boxes);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void saveWagonData(CompoundTag tag, HolderLookup.Provider registries) {
         tag.putLong("Owner", owner.asLong());
        tag.putLong("Source",source().asLong());
        ListTag list = new ListTag();
        for (AABB b : boxes) {
            ListTag values = new ListTag();
            for (double v : new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ}) values.add(DoubleTag.valueOf(v));
            list.add(values);
        }
        tag.put("Boxes", list);
    }
    private void loadWagonData(CompoundTag tag, HolderLookup.Provider registries) {
         owner = BlockPos.of(tag.getLongOr("Owner",0L));
        source=BlockPos.of(tag.getLongOr("Source",0L));
        List<AABB> loaded = new ArrayList<>();
        for (Tag entry : tag.getListOrEmpty("Boxes")) {
            ListTag v = (ListTag) entry;
            if (v.size() == 6) loaded.add(new AABB(v.getDoubleOr(0,0),v.getDoubleOr(1,0),v.getDoubleOr(2,0),v.getDoubleOr(3,0),v.getDoubleOr(4,0),v.getDoubleOr(5,0)));
        }
        boxes = List.copyOf(loaded); shape = WagonGeometry.shape(boxes);
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
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
        var frame=AssemblyFrameBlockEntity.find(level,owner);
        if(frame!=null&&!frame.changing()&&frame.layout().containsKey(pos)) {
            if(frame.layout().get(pos).framePart())frame.dismantle(true,true);
            else frame.remove(frame.layout().get(pos).slots(),true);
        }
    }
}
