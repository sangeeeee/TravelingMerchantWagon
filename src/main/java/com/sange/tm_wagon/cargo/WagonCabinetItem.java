package com.sange.tm_wagon.cargo;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** An adaptive seat accessory, never a placeable world block. */
public final class WagonCabinetItem extends Item {
    public WagonCabinetItem(Properties properties) { super(properties); }
    @Override public Component getName(ItemStack stack) { return com.sange.tm_wagon.material.WagonMaterial.name(stack,super.getName(stack)); }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.tm_wagon.wagon_cabinet"));
    }
}
