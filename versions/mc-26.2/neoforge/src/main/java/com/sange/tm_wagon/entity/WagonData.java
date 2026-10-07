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
    public static void register(net.neoforged.bus.api.IEventBus bus) {
        var registry=net.neoforged.neoforge.registries.DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS,"tm_wagon");
        registry.register("cargo_tag",()->TAG);registry.register(bus);
    }
}
