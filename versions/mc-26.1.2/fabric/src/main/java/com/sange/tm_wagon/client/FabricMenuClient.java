package com.sange.tm_wagon.client;
/** Client-only call site keeps dedicated servers free of client class loading. */
public final class FabricMenuClient {
    public static void close(net.minecraft.world.entity.player.Player player){
        if(player instanceof net.minecraft.client.player.LocalPlayer local)local.closeContainer();
    }
    private FabricMenuClient(){}
}
