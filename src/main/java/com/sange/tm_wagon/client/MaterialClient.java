package com.sange.tm_wagon.client;

import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.material.WagonMaterial;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class MaterialClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(()->{
            var property=ResourceLocation.fromNamespaceAndPath("tm_wagon","wood");
            for(var item:new net.minecraft.world.item.Item[]{WagonContent.STOOL.get(),WagonContent.CABINET.get()})
                ItemProperties.register(item,property,(stack,level,entity,seed)->WagonMaterial.of(stack).wood().ordinal());
        });
    }
    @SubscribeEvent public static void colours(RegisterColorHandlersEvent.Item event) {
        event.register((stack,index)->FabricColours.tint(WagonMaterial.of(stack).colour()),WagonContent.CARGO_COVER.get(),WagonContent.CANOPY.get());
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<Void>() {
            @Override protected Void prepare(net.minecraft.server.packs.resources.ResourceManager manager,net.minecraft.util.profiling.ProfilerFiller profiler) { return null; }
            @Override protected void apply(Void unused,net.minecraft.server.packs.resources.ResourceManager manager,net.minecraft.util.profiling.ProfilerFiller profiler) { MaterialRenderer.reload(manager);TextureTiling.clear(); }
        });
    }
    private MaterialClient() {}
}
