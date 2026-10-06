package com.sange.tm_wagon.platform;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Loader-owned registry bindings; gameplay uses ordinary suppliers. */
public final class FabricRegistry<T> {
    private final Registry<T> registry;
    private final String namespace;
    private FabricRegistry(Registry<T> registry,String namespace) { this.registry=registry;this.namespace=namespace; }
    @SuppressWarnings("unchecked")
    public static <T> FabricRegistry<T> create(ResourceKey<? extends Registry<T>> key,String namespace) {
        return new FabricRegistry<>((Registry<T>)BuiltInRegistries.REGISTRY.get(key.location()),namespace);
    }
    public static FabricRegistry<Block> createBlocks(String namespace) { return create(Registries.BLOCK,namespace); }
    public static FabricRegistry<Item> createItems(String namespace) { return create(Registries.ITEM,namespace); }
    public <V extends T> Supplier<V> register(String name,Supplier<V> factory) {
        V value=Registry.register(registry,ResourceLocation.fromNamespaceAndPath(namespace,name),factory.get());
        return ()->value;
    }
    @SuppressWarnings("unchecked")
    public Supplier<Item> registerSimpleItem(String name) { return (Supplier<Item>)register(name,()->(T)new Item(new Item.Properties())); }
}
