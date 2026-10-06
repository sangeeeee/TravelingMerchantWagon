package com.sange.tm_wagon.compat;

import com.sange.tm_wagon.cargo.CargoEntry;
import com.sange.tm_wagon.cargo.CargoHold;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.fabricmc.loader.api.FabricLoader;

/** Optional APIs stay behind these gates, including on dedicated servers. */
public final class BackpackCompat {
    public static boolean traveler(ItemStack stack) {
        return FabricLoader.getInstance().isModLoaded("travelersbackpack") && com.sange.tm_wagon.compat.backpack.TravelerCargo.matches(stack);
    }
    public static boolean sophisticated(ItemStack stack) {
        return FabricLoader.getInstance().isModLoaded("sophisticatedbackpacks") && com.sange.tm_wagon.compat.backpack.SophisticatedCargo.matches(stack);
    }
    public static boolean sleepingBag(ItemStack stack) {
        return FabricLoader.getInstance().isModLoaded("travelersbackpack") && com.sange.tm_wagon.compat.backpack.TravelerCargo.sleepingBag(stack);
    }
    public static boolean matches(ItemStack stack) { return traveler(stack)||sophisticated(stack); }
    public static Block block(ItemStack stack) {
        return sophisticated(stack)?com.sange.tm_wagon.compat.backpack.SophisticatedCargo.block(stack):null;
    }
    public static ItemStack blockItem(Block block) {
        if(FabricLoader.getInstance().isModLoaded("sophisticatedbackpacks")) {
            var stack=com.sange.tm_wagon.compat.backpack.SophisticatedCargo.item(block);
            if(sophisticated(stack))return stack;
        }
        return new ItemStack(block);
    }
    public static void register() {
        if(FabricLoader.getInstance().isModLoaded("travelersbackpack"))com.sange.tm_wagon.compat.backpack.TravelerCargo.register();
        if(FabricLoader.getInstance().isModLoaded("sophisticatedbackpacks"))com.sange.tm_wagon.compat.backpack.SophisticatedCargo.register();
    }
    public static void open(CargoHold hold,CargoEntry entry,Player player) {
        if(!hold.valid(entry,player))return;
        if(traveler(entry.item))com.sange.tm_wagon.compat.backpack.TravelerCargo.open(hold,entry,player,false);
        else if(sophisticated(entry.item))com.sange.tm_wagon.compat.backpack.SophisticatedCargo.open(hold,entry,player);
    }
    public static ItemStack placementCopy(ItemStack stack,Player player) {
        return sophisticated(stack)&&player.isCreative()
            ?com.sange.tm_wagon.compat.backpack.SophisticatedCargo.placementCopy(stack,player):stack;
    }
    public static boolean canTake(CargoEntry entry,Player player) {
        return !sophisticated(entry.item)||com.sange.tm_wagon.compat.backpack.SophisticatedCargo.canTake(entry.item,player);
    }
    public static void visual(ItemStack stack) {
        if(traveler(stack))com.sange.tm_wagon.compat.backpack.TravelerCargo.visual(stack);
    }
    /** Initialize the native BE explicitly: neither mod uses vanilla container Items NBT. */
    public static boolean snapshot(BlockEntity be,ItemStack stack,HolderLookup.Provider lookup) {
        if(traveler(stack)) { com.sange.tm_wagon.compat.backpack.TravelerCargo.snapshot(be,stack);return true; }
        if(sophisticated(stack)) { com.sange.tm_wagon.compat.backpack.SophisticatedCargo.snapshot(be,stack,lookup);return true; }
        return false;
    }
    public static ItemStack fromBlockEntity(BlockEntity be,ItemStack fallback,HolderLookup.Provider lookup) {
        if(traveler(fallback))return com.sange.tm_wagon.compat.backpack.TravelerCargo.fromBlockEntity(be,fallback);
        if(sophisticated(fallback))return com.sange.tm_wagon.compat.backpack.SophisticatedCargo.fromBlockEntity(be,fallback,lookup);
        be.saveToItem(fallback,lookup);return fallback;
    }
    private BackpackCompat() {}
}
