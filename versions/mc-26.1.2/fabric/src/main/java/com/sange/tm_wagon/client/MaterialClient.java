package com.sange.tm_wagon.client;

import com.mojang.serialization.MapCodec;
import com.sange.tm_wagon.material.WagonMaterial;
import net.minecraft.resources.Identifier;

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
    public static void register() {
        com.sange.tm_wagon.mixin.WagonRangePropertiesAccess.wagonMapper().put(Identifier.fromNamespaceAndPath("tm_wagon","wood"),WoodProperty.CODEC);
        com.sange.tm_wagon.mixin.WagonTintSourcesAccess.wagonMapper().put(Identifier.fromNamespaceAndPath("tm_wagon","cloth"),ClothTint.CODEC);
        var event=net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
        event.registerReloadListener(Identifier.fromNamespaceAndPath("tm_wagon","materials"),new net.minecraft.server.packs.resources.SimplePreparableReloadListener<Void>() {
            @Override protected Void prepare(net.minecraft.server.packs.resources.ResourceManager manager,net.minecraft.util.profiling.ProfilerFiller profiler) { return null; }
            @Override protected void apply(Void unused,net.minecraft.server.packs.resources.ResourceManager manager,net.minecraft.util.profiling.ProfilerFiller profiler) { MaterialRenderer.reload(manager);TextureTiling.clear();CanopyRenderer.clear();CargoCoverRenderer.clear();CargoStrawMatRenderer.clear();CabinetRenderer.clear(); }
        });
    }
    private MaterialClient() {}
}
