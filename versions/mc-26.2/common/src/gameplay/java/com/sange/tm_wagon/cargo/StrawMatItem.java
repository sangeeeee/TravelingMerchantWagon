package com.sange.tm_wagon.cargo;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A cargo accessory, deliberately not a BlockItem: it cannot place a world block. */
public final class StrawMatItem extends Item {
    public StrawMatItem(Properties properties) { super(properties); }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,net.minecraft.world.item.component.TooltipDisplay display,java.util.function.Consumer<Component> lines,TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.tm_wagon.wagon_straw_mat"));
    }
}
