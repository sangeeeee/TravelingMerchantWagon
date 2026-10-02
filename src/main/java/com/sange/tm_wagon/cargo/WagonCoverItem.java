package com.sange.tm_wagon.cargo;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Optional cart accessory, never a placeable world block or a cargo entry. */
public final class WagonCoverItem extends Item {
    public WagonCoverItem(Properties properties) { super(properties); }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.tm_wagon.wagon_cargo_cover"));
    }
}
