package com.sange.tm_wagon.compat;

import com.sange.tm_wagon.cargo.CargoHold;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

/** Optional entry points: no maid API types on the dependency-free loading path. */
public final class MaidCompat {
    private static boolean loaded() { return ModList.get().isLoaded("touhou_little_maid"); }
    public static InteractionResult interact(CargoHold hold,Player player,InteractionHand hand,Vec3 local) {
        return loaded()?com.sange.tm_wagon.compat.maid.WagonMaidItems.interact(hold,player,hand,local):InteractionResult.PASS;
    }
    public static InteractionResult capture(Player player,Entity target,InteractionHand hand) {
        return loaded()?com.sange.tm_wagon.compat.maid.WagonMaidItems.capture(player,target,hand):InteractionResult.PASS;
    }
    public static void afterStoolMount(LivingEntity rider) {
        if(loaded())com.sange.tm_wagon.compat.maid.WagonMaidItems.afterStoolMount(rider);
    }
    public static boolean automaticStool(LivingEntity rider) {
        return !loaded()||com.sange.tm_wagon.compat.maid.WagonMaidItems.automaticStool(rider);
    }
    private MaidCompat() {}
}
