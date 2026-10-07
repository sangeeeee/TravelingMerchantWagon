package com.sange.tm_wagon.client;
public final class FabricPortSmoke implements net.fabricmc.api.ClientModInitializer {
    public void onInitializeClient(){
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(PortClientSmoke::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(PortClientSmoke::serverTick);
    }
}
