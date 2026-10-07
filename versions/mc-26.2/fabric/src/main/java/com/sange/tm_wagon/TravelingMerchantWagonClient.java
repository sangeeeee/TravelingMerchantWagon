package com.sange.tm_wagon;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.client.*;
public final class TravelingMerchantWagonClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        com.sange.tm_wagon.network.WagonNetworkClient.registerClient();
        EntityRendererRegistry.register(WagonContent.WAGON.get(),WagonRenderer::new);
        EntityRendererRegistry.register(WagonContent.CARGO_SEAT.get(),net.minecraft.client.renderer.entity.NoopRenderer::new);
        BlockEntityRendererRegistry.register(WagonContent.FRAME_ENTITY.get(),AssemblyRenderer::new);
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(context->{CabinetRenderer.models(context);CanopyRenderer.models(context);CargoCoverRenderer.models(context);CargoStrawMatRenderer.models(context);});
        MaterialClient.register();BackpackScreens.register();
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            AssemblyFrameInput.tick();WagonDrivingInput.tick();WagonPushingInput.tick();WagonEffects.tick();
            if(client.player!=null)com.sange.tm_wagon.cargo.BackpackSessions.tick(client.player);
            if(client.level!=null)com.sange.tm_wagon.cargo.StrawMatSleep.followAfterLevel(client.level);
        });
        ClientEntityEvents.ENTITY_LOAD.register((entity,world)->com.sange.tm_wagon.entity.WagonSpatialIndex.joined(entity,world));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity,world)->{com.sange.tm_wagon.entity.WagonSpatialIndex.left(entity);com.sange.tm_wagon.cargo.StrawMatSleep.leave(entity);});
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity,world)->{if(entity instanceof AssemblyFrameBlockEntity f)f.onLoaded();});
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity,world)->{if(entity instanceof AssemblyFrameBlockEntity f)f.onUnloaded();});
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{
            if(client.level!=null) {com.sange.tm_wagon.cargo.StrawMatSleep.unload(client.level);com.sange.tm_wagon.cargo.BackpackSessions.unload(client.level);com.sange.tm_wagon.entity.WagonSpatialIndex.unloaded(client.level);}
        });
    }
}
