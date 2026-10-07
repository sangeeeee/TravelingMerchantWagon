package com.sange.tm_wagon.cargo;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Registry-aware serialization shared by transfers, rollback and persistent cargo. */
public final class CargoNbt {
    public static Tag save(ItemStack item,HolderLookup.Provider lookup) {
        return ItemStack.CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE),item).getOrThrow();
    }
    public static ItemStack item(HolderLookup.Provider lookup,CompoundTag tag) {
        return ItemStack.CODEC.parse(lookup.createSerializationContext(NbtOps.INSTANCE),tag).result().orElse(ItemStack.EMPTY);
    }
    public static void saveItems(CompoundTag tag,NonNullList<ItemStack> items,HolderLookup.Provider lookup) {
        var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,lookup);
        ContainerHelper.saveAllItems(out,items);tag.merge(out.buildResult());
    }
    public static void loadItems(CompoundTag tag,NonNullList<ItemStack> items,HolderLookup.Provider lookup) {
        ContainerHelper.loadAllItems(TagValueInput.create(ProblemReporter.DISCARDING,lookup,tag),items);
    }
    public static void load(BlockEntity entity,CompoundTag tag,HolderLookup.Provider lookup) {
        entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,lookup,tag));
    }
    public static CompoundTag blockTag(ItemStack stack) {
        var data=stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        if(data==null)return new CompoundTag();
        var tag=data.copyTagWithoutId();tag.putString("id",net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(data.type()).toString());return tag;
    }
    public static void blockTag(ItemStack stack,CompoundTag tag) {
        var id=net.minecraft.resources.Identifier.tryParse(tag.getStringOr("id",""));
        if(id==null)return;
        var type=net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
        if(type!=null)stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.TypedEntityData.of(type,tag));
    }
    public static void saveToItem(BlockEntity entity,ItemStack item,HolderLookup.Provider lookup) {
        var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,lookup);entity.saveCustomOnly(out);
        net.minecraft.world.item.BlockItem.setBlockEntityData(item,entity.getType(),out);item.applyComponents(entity.collectComponents());
    }
    private CargoNbt() {}
}
