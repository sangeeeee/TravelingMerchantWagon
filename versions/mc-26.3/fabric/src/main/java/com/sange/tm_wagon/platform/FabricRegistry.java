package com.sange.tm_wagon.platform;
import java.util.function.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
/** Immediate Fabric bindings with vanilla resource keys set before construction. */
public final class FabricRegistry<T> {
    private final Registry<T> registry;private final String namespace;
    private FabricRegistry(Registry<T> registry,String namespace) { this.registry=registry;this.namespace=namespace; }
    @SuppressWarnings("unchecked") public static <T> FabricRegistry<T> create(ResourceKey<? extends Registry<T>> key,String namespace) { return new FabricRegistry<>((Registry<T>)BuiltInRegistries.REGISTRY.getValue(key.identifier()),namespace); }
    public static FabricRegistry<Block> createBlocks(String namespace) { return create(Registries.BLOCK,namespace); }
    public static FabricRegistry<Item> createItems(String namespace) { return create(Registries.ITEM,namespace); }
    public <V extends T> Supplier<V> register(String name,Supplier<V> factory) { V value=Registry.register(registry,Identifier.fromNamespaceAndPath(namespace,name),factory.get());return ()->value; }
    @SuppressWarnings("unchecked") public <V extends Item> Supplier<V> registerItem(String name,Function<Item.Properties,V> factory,Supplier<Item.Properties> properties) { return (Supplier<V>)register(name,()->(T)factory.apply(properties.get().setId(ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath(namespace,name))))); }
    public <V extends Item> Supplier<V> registerItem(String name,Function<Item.Properties,V> factory) { return registerItem(name,factory,Item.Properties::new); }
    public Supplier<Item> registerSimpleItem(String name) { return registerItem(name,Item::new); }
    @SuppressWarnings("unchecked") public <V extends Block> Supplier<V> registerBlock(String name,Function<BlockBehaviour.Properties,V> factory,Supplier<BlockBehaviour.Properties> properties) { return (Supplier<V>)register(name,()->(T)factory.apply(properties.get().setId(ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath(namespace,name))))); }
    public void register() {}
}
