package com.sange.tm_wagon;

import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.client.AssemblyRenderer;
import com.sange.tm_wagon.client.PartItemRenderer;
import com.sange.tm_wagon.client.FrameItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = TravelingMerchantWagon.MODID, value = Dist.CLIENT)
public final class TravelingMerchantWagonClient {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WagonContent.WAGON.get(),com.sange.tm_wagon.client.WagonRenderer::new);
        event.registerEntityRenderer(WagonContent.CARGO_SEAT.get(),net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerBlockEntityRenderer(WagonContent.FRAME_ENTITY.get(), AssemblyRenderer::new);
    }
    private TravelingMerchantWagonClient() {}
}