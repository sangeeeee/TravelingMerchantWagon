package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;

/** Iron axe behavior, with a server-authoritative dismantling action instead of wagon damage. */
public final class DismantlingHammerItem extends AxeItem {
    public DismantlingHammerItem() {
        super(Tiers.IRON,6,-3.1F,new Properties());
    }
    @Override public boolean onLeftClickEntity(ItemStack stack,Player player,Entity entity) {
        if(!(entity instanceof WagonEntity wagon))return super.onLeftClickEntity(stack,player,entity);
        if(!player.level().isClientSide)wagon.dismantle(player);
        return true;
    }
}
