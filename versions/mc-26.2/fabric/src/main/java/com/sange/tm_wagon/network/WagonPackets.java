package com.sange.tm_wagon.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class WagonPackets {
    public static void sendToServer(CustomPacketPayload payload) { net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload); }
    public static void sendToPlayer(ServerPlayer player,CustomPacketPayload payload) {
        if(player.connection!=null&&ServerPlayNetworking.canSend(player,payload.type()))ServerPlayNetworking.send(player,payload);
    }
    public static void sendToPlayersTrackingEntity(Entity entity,CustomPacketPayload payload) {
        for(var player:PlayerLookup.tracking(entity))sendToPlayer(player,payload);
    }
    public static void sendToPlayersTrackingEntityAndSelf(Entity entity,CustomPacketPayload payload) {
        sendToPlayersTrackingEntity(entity,payload);
        if(entity instanceof ServerPlayer player)sendToPlayer(player,payload);
    }
}
