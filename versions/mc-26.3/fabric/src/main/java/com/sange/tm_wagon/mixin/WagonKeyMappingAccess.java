package com.sange.tm_wagon.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface WagonKeyMappingAccess {
    @Accessor("clickCount") int tm_wagon$clickCount();
    @Accessor("clickCount") void tm_wagon$clickCount(int count);
}
