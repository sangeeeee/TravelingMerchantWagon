package com.sange.tm_wagon;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.client.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
public final class TravelingMerchantWagonClient implements ClientModInitializer {
    @Override public void onInitializeClient(){
        com.sange.tm_wagon.network.WagonNetworkClient.registerClient();
        EntityRendererRegistry.register(WagonContent.WAGON.get(),WagonRenderer::new);
        EntityRendererRegistry.register(WagonContent.CARGO_SEAT.get(),net.minecraft.client.renderer.entity.NoopRenderer::new);
        BlockEntityRendererRegistry.register(WagonContent.FRAME_ENTITY.get(),context->new AssemblyRenderer());
        var frame=new FrameItemRenderer();
        BuiltinItemRendererRegistry.INSTANCE.register(WagonContent.FRAME_ITEM.get(),frame::renderByItem);
        WagonContent.PART_ITEMS.forEach((part,item)->{
            var renderer=new PartItemRenderer(part);
            BuiltinItemRendererRegistry.INSTANCE.register(item.get(),renderer::renderByItem);
        });
        ModelLoadingPlugin.register(context->{
            CabinetRenderer.models(id->context.addModels(id));
            CanopyRenderer.models(id->context.addModels(id));
            CargoCoverRenderer.models(id->context.addModels(id));
        });
        MaterialClient.register();BackpackScreens.register();
        net.fabricmc.fabric.api.event.client.player.ClientPickBlockGatherCallback.EVENT.register((player,hit)->{
            if(hit instanceof net.minecraft.world.phys.BlockHitResult block&&hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK){
                var state=player.level().getBlockState(block.getBlockPos());
                if(state.getBlock() instanceof AssemblyPartBlock part)return part.getCloneItemStack(state,hit,player.level(),block.getBlockPos(),player);
            }
            return net.minecraft.world.item.ItemStack.EMPTY;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(Boolean.getBoolean("tm_wagon.fabricSmoke")) {
                String preview=switch(System.getProperty("tm_wagon.fabricSmokeMode","models")){
                    case "backpacks"->"BackpackClientSmoke";
                    case "creative"->"CreativeInventoryClientSmoke";
                    default->"StageOneClientSmoke";
                };
                try {Class.forName("com.sange.tm_wagon.client."+preview).getMethod("tick").invoke(null);}
                catch(ReflectiveOperationException e){throw new IllegalStateException("Fabric smoke test failed",e);}
            }
            AssemblyFrameInput.tick();WagonDrivingInput.tick();WagonPushingInput.tick();WagonEffects.tick();
            if(client.player!=null)com.sange.tm_wagon.cargo.BackpackSessions.tick(client.player);
            if(client.level!=null)com.sange.tm_wagon.cargo.StrawMatSleep.followAfterLevel(client.level);
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{
            if(client.level!=null){
                com.sange.tm_wagon.cargo.StrawMatSleep.unload(client.level);
                com.sange.tm_wagon.cargo.BackpackSessions.unload(client.level);
                com.sange.tm_wagon.entity.WagonSpatialIndex.unloaded(client.level);
            }
        });
        ClientEntityEvents.ENTITY_LOAD.register((entity,world)->com.sange.tm_wagon.entity.WagonSpatialIndex.joined(entity,world));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity,world)->{com.sange.tm_wagon.entity.WagonSpatialIndex.left(entity);com.sange.tm_wagon.cargo.StrawMatSleep.leave(entity);});
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity,world)->{if(entity instanceof AssemblyFrameBlockEntity f)f.onLoaded();});
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity,world)->{if(entity instanceof AssemblyFrameBlockEntity f)f.onUnloaded();});
    }
}
