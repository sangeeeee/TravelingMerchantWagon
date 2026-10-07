package com.sange.tm_wagon.mixin;
@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties.class)
public interface WagonRangePropertiesAccess {
    @org.spongepowered.asm.mixin.gen.Accessor("ID_MAPPER")
    static net.minecraft.util.ExtraCodecs.LateBoundIdMapper<net.minecraft.resources.Identifier,com.mojang.serialization.MapCodec<? extends net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty>> wagonMapper() {throw new AssertionError();}
}
