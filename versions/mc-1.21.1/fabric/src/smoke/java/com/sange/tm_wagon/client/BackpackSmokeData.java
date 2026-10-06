package com.sange.tm_wagon.client;
import com.sange.tm_wagon.compat.BackpackCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
final class BackpackSmokeData {
    public static ItemStack filled(ServerLevel level,String type) {
        var stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(type)));
        if(stack.isEmpty())throw new IllegalStateException("Missing backpack "+type);
        if(BackpackCompat.traveler(stack))
            new com.tiviacz.travelersbackpack.inventory.BackpackWrapper(stack,1,null,level).getStorage().setStackInSlot(0,new ItemStack(Items.DIAMOND,17));
        else net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper.fromStack(stack).getInventoryHandler().setStackInSlot(0,new ItemStack(Items.DIAMOND,17));
        var tag=new CompoundTag();tag.putString("Marker","kept");stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Kept backpack"));return stack;
    }
    public static int contents(ServerLevel level,ItemStack stack) {
        return BackpackCompat.traveler(stack)
            ?new com.tiviacz.travelersbackpack.inventory.BackpackWrapper(stack,1,null,level).getStorage().getStackInSlot(0).getCount()
            :net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper.fromStack(stack).getInventoryHandler().getStackInSlot(0).getCount();
    }
}
