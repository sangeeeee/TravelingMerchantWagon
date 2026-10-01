package com.sange.tm_wagon;

import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.client.AssemblyRenderer;
import com.sange.tm_wagon.client.PartItemRenderer;
import com.sange.tm_wagon.client.FrameItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = TravelingMerchantWagon.MODID, value = Dist.CLIENT)
public final class TravelingMerchantWagonClient {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WagonContent.WAGON.get(),com.sange.tm_wagon.client.WagonRenderer::new);
        event.registerBlockEntityRenderer(WagonContent.FRAME_ENTITY.get(), context -> new AssemblyRenderer());
    }
    @SubscribeEvent
    public static void items(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private final FrameItemRenderer renderer = new FrameItemRenderer();
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
        },WagonContent.FRAME_ITEM.get());
        WagonContent.PART_ITEMS.forEach((part, item) -> event.registerItem(new IClientItemExtensions() {
            private final PartItemRenderer renderer = new PartItemRenderer(part);
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
        }, item.get()));
    }
    private TravelingMerchantWagonClient() {}
}
