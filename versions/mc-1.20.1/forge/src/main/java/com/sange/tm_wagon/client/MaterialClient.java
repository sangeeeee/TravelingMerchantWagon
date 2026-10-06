package com.sange.tm_wagon.client;

import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.material.WagonMaterial;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class MaterialClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(()->{
            var property=new ResourceLocation("tm_wagon","wood");
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
            @Override protected void apply(Void unused,net.minecraft.server.packs.resources.ResourceManager manager,net.minecraft.util.profiling.ProfilerFiller profiler) { MaterialRenderer.reload(manager);TextureTiling.clear();CanopyRenderer.clear();CargoCoverRenderer.clear(); }
        });
    }
    private MaterialClient() {}
}
