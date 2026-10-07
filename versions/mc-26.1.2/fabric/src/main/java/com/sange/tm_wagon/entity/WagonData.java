package com.sange.tm_wagon.entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
public final class WagonData {
    static final EntityDataSerializer<CompoundTag> TAG=new EntityDataSerializer<>() {
        @Override public net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf,CompoundTag> codec() { return ByteBufCodecs.COMPOUND_TAG; }
        @Override public CompoundTag copy(CompoundTag tag) { return tag.copy(); }
    };
    public static void register() {
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityDataRegistry.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("tm_wagon","cargo_tag"),TAG);
    }
}
