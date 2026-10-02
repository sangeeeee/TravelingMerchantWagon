package com.sange.tm_wagon.cargo;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Optional roof item; never a world block or a cargo-slot entry. */
public final class WagonCanopyItem extends Item {
    public WagonCanopyItem(Properties properties) { super(properties); }
    @Override public Component getName(ItemStack stack) { return com.sange.tm_wagon.material.WagonMaterial.name(stack,super.getName(stack)); }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.tm_wagon.wagon_canopy"));
    }
}
