package com.sange.tm_wagon.compat;

import com.sange.tm_wagon.cargo.CargoEntry;
import com.sange.tm_wagon.cargo.CargoHold;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Optional APIs stay behind these gates, including on dedicated servers. */
public final class BackpackCompat {
    public static boolean traveler(ItemStack stack) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("travelersbackpack") && com.sange.tm_wagon.compat.backpack.TravelerCargo.matches(stack);
    }
    public static boolean sophisticated(ItemStack stack) { return false; }
    public static boolean sleepingBag(ItemStack stack) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("travelersbackpack") && com.sange.tm_wagon.compat.backpack.TravelerCargo.sleepingBag(stack);
    }
    public static boolean matches(ItemStack stack) { return traveler(stack)||sophisticated(stack); }
    public static Block block(ItemStack stack) { return null; }
    public static ItemStack blockItem(Block block) { return new ItemStack(block); }
    public static void register() {
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("travelersbackpack"))com.sange.tm_wagon.compat.backpack.TravelerCargo.register();
    }
    public static void open(CargoHold hold,CargoEntry entry,Player player) {
        if(!hold.valid(entry,player))return;
        if(traveler(entry.item))com.sange.tm_wagon.compat.backpack.TravelerCargo.open(hold,entry,player,false);
    }
    public static ItemStack placementCopy(ItemStack stack,Player player) { return stack; }
    public static boolean canTake(CargoEntry entry,Player player) { return true; }
    public static void visual(ItemStack stack) {
        if(traveler(stack))com.sange.tm_wagon.compat.backpack.TravelerCargo.visual(stack);
    }
    /** Initialize the native BE explicitly: neither mod uses vanilla container Items NBT. */
    public static boolean snapshot(BlockEntity be,ItemStack stack,HolderLookup.Provider lookup) {
        if(traveler(stack)) { com.sange.tm_wagon.compat.backpack.TravelerCargo.snapshot(be,stack);return true; }
        return false;
    }
    public static ItemStack fromBlockEntity(BlockEntity be,ItemStack fallback,HolderLookup.Provider lookup) {
        if(traveler(fallback))return com.sange.tm_wagon.compat.backpack.TravelerCargo.fromBlockEntity(be,fallback);
        com.sange.tm_wagon.cargo.CargoNbt.saveToItem(be,fallback,lookup);return fallback;
    }
    private BackpackCompat() {}
}
