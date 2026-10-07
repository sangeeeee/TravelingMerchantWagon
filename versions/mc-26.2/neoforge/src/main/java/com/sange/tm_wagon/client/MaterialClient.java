package com.sange.tm_wagon.client;

import com.mojang.serialization.MapCodec;
import com.sange.tm_wagon.material.WagonMaterial;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class MaterialClient {
    public record WoodProperty() implements net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty {
        static final MapCodec<WoodProperty> CODEC=MapCodec.unit(new WoodProperty());
        @Override public float get(net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.ItemOwner owner,int seed) { return WagonMaterial.of(stack).wood().ordinal(); }
        @Override public MapCodec<WoodProperty> type() { return CODEC; }
    }
    public record ClothTint() implements net.minecraft.client.color.item.ItemTintSource {
        static final MapCodec<ClothTint> CODEC=MapCodec.unit(new ClothTint());
        @Override public int calculate(net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity owner) { return FabricColours.tint(WagonMaterial.of(stack).colour()); }
        @Override public MapCodec<ClothTint> type() { return CODEC; }
    }
    @SubscribeEvent public static void wood(RegisterRangeSelectItemModelPropertyEvent event) { event.register(Identifier.fromNamespaceAndPath("tm_wagon","wood"),WoodProperty.CODEC); }
    @SubscribeEvent public static void tint(RegisterColorHandlersEvent.ItemTintSources event) { event.register(Identifier.fromNamespaceAndPath("tm_wagon","cloth"),ClothTint.CODEC); }
    @SubscribeEvent public static void reload(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath("tm_wagon","materials"),new net.minecraft.server.packs.resources.SimplePreparableReloadListener<Void>() {
            @Override protected Void prepare(net.minecraft.server.packs.resources.ResourceManager manager,net.minecraft.util.profiling.ProfilerFiller profiler) { return null; }
            @Override protected void apply(Void unused,net.minecraft.server.packs.resources.ResourceManager manager,net.minecraft.util.profiling.ProfilerFiller profiler) { MaterialRenderer.reload(manager);TextureTiling.clear();CanopyRenderer.clear();CargoCoverRenderer.clear();CargoStrawMatRenderer.clear();CabinetRenderer.clear(); }
        });
    }
    private MaterialClient() {}
}
