package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;

/** Iron axe behavior, with a server-authoritative dismantling action instead of wagon damage. */
public final class DismantlingHammerItem extends Item {
    public DismantlingHammerItem(Properties properties) {
        super(properties.axe(ToolMaterial.IRON,6,-3.1F));
    }
}
