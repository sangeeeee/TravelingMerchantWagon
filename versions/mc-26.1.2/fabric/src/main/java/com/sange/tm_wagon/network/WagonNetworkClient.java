package com.sange.tm_wagon.network;
import com.sange.tm_wagon.network.WagonNetwork.*;
public final class WagonNetworkClient {
    public static void registerClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(WagonNetwork.HandbookRecipes.TYPE,(packet,context)->com.sange.tm_wagon.handbook.HandbookRecipes.receive(packet.recipes()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(BackpackView.TYPE,(input,context)->
            com.sange.tm_wagon.cargo.BackpackSessions.receive(context.player(),input.entry(),input.stack()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(BackpackUpdate.TYPE,(input,context)->{
            if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("travelersbackpack"))
                com.sange.tm_wagon.compat.backpack.TravelerCargo.receive(context.player(),input.entry(),input.patch());
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(MatSleep.TYPE,(input,context)->
            com.sange.tm_wagon.cargo.StrawMatSleep.receive(context.player().level(),input));
    }
}
