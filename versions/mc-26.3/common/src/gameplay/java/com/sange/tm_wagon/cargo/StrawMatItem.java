package com.sange.tm_wagon.cargo;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A wagon module, never a placeable world block and never a generic multi-block exception. */
public final class StrawMatItem extends Item {
    public StrawMatItem(Properties properties) { super(properties); }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip,TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.tm_wagon.wagon_straw_mat"));
    }
}
