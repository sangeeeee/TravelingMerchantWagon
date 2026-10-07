package com.sange.tm_wagon.mixin;
@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.color.item.ItemTintSources.class)
public interface WagonTintSourcesAccess {
    @org.spongepowered.asm.mixin.gen.Accessor("ID_MAPPER")
    static net.minecraft.util.ExtraCodecs.LateBoundIdMapper<net.minecraft.resources.Identifier,com.mojang.serialization.MapCodec<? extends net.minecraft.client.color.item.ItemTintSource>> wagonMapper() {throw new AssertionError();}
}
