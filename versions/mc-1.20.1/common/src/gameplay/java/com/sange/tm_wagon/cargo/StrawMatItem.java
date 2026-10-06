package com.sange.tm_wagon.cargo;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A wagon module, never a placeable world block and never a generic multi-block exception. */
public final class StrawMatItem extends Item {
    public StrawMatItem(Properties properties) { super(properties); }
    @Override public void appendHoverText(ItemStack stack,net.minecraft.world.level.Level context,List<Component> tooltip,TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tm_wagon.wagon_straw_mat"));
    }
}
