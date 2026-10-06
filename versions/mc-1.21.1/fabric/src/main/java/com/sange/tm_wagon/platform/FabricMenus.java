package com.sange.tm_wagon.platform;
public final class FabricMenus {
    public static void close(net.minecraft.world.entity.player.Player player) {
        if(player instanceof net.minecraft.server.level.ServerPlayer server)server.closeContainer();
    }
    private FabricMenus() {}
}
