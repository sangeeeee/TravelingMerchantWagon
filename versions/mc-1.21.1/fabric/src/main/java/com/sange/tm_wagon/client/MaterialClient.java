package com.sange.tm_wagon.client;
import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.material.WagonMaterial;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.server.packs.PackType;
public final class MaterialClient {
    public static void register() {
        var property=ResourceLocation.fromNamespaceAndPath("tm_wagon","wood");
        for(var item:new net.minecraft.world.item.Item[]{WagonContent.STOOL.get(),WagonContent.CABINET.get()})
            ItemProperties.register(item,property,new net.minecraft.client.renderer.item.ClampedItemPropertyFunction() {
                @Override public float call(net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){return unclampedCall(stack,level,entity,seed);}
                @Override public float unclampedCall(net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){return WagonMaterial.of(stack).wood().ordinal();}
            });
        ColorProviderRegistry.ITEM.register((stack,index)->FabricColours.tint(WagonMaterial.of(stack).colour()),WagonContent.CARGO_COVER.get(),WagonContent.CANOPY.get());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new Reload());
    }
    private static final class Reload extends net.minecraft.server.packs.resources.SimplePreparableReloadListener<Void> implements IdentifiableResourceReloadListener {
        @Override public ResourceLocation getFabricId(){return ResourceLocation.fromNamespaceAndPath("tm_wagon","materials");}
        @Override protected Void prepare(net.minecraft.server.packs.resources.ResourceManager manager,net.minecraft.util.profiling.ProfilerFiller profiler){return null;}
        @Override protected void apply(Void unused,net.minecraft.server.packs.resources.ResourceManager manager,net.minecraft.util.profiling.ProfilerFiller profiler){MaterialRenderer.reload(manager);TextureTiling.clear();CanopyRenderer.clear();CargoCoverRenderer.clear();}
    }
    private MaterialClient() {}
}
