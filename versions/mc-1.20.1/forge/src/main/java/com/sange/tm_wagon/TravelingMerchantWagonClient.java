package com.sange.tm_wagon;

import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.client.AssemblyRenderer;
import com.sange.tm_wagon.client.PartItemRenderer;
import com.sange.tm_wagon.client.FrameItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

@EventBusSubscriber(modid = TravelingMerchantWagon.MODID, value = Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class TravelingMerchantWagonClient {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WagonContent.WAGON.get(),com.sange.tm_wagon.client.WagonRenderer::new);
        event.registerEntityRenderer(WagonContent.CARGO_SEAT.get(),net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerBlockEntityRenderer(WagonContent.FRAME_ENTITY.get(), context -> new AssemblyRenderer());
    }
    private TravelingMerchantWagonClient() {}
}
