package com.sange.tm_wagon.handbook;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Registered only with Patchouli installed; reader linkage stays in the optional bridge. */
public final class CoachmansManualItem extends Item {
    public CoachmansManualItem() { super(new Properties().stacksTo(1)); }

    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(player instanceof ServerPlayer serverPlayer)PatchouliHandbook.open(serverPlayer);
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
