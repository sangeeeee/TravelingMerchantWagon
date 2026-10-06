package com.sange.tm_wagon.cargo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
/** Copies prevent inventory state from being shared by cargo and source stacks. */
public final class ItemNbt {
    public static CompoundTag blockEntity(ItemStack stack) {var tag=stack.getTagElement("BlockEntityTag");return tag==null?new CompoundTag():tag.copy();}
    public static void blockEntity(ItemStack stack,CompoundTag tag) {if(tag.isEmpty())stack.removeTagKey("BlockEntityTag");else stack.addTagElement("BlockEntityTag",tag);}
    public static void consume(ItemStack stack,int count,Player player) {if(!player.getAbilities().instabuild)stack.shrink(count);}
    private ItemNbt() {}
}
