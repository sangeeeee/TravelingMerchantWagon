package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.entity.WagonPlatform;
import com.sange.tm_wagon.network.WagonNetwork;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public abstract class WagonStandingLocalPlayerMixin {
    @Redirect(method="sendPosition",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void tm_wagon$relativePosition(ClientPacketListener connection,Packet<?> packet) {
        LocalPlayer player=(LocalPlayer)(Object)this;
        if(packet instanceof ServerboundMovePlayerPacket move&&move.isOnGround()) {
            var wagon=WagonPlatform.supportingWagon(player);
            if(wagon!=null) {
                var local=wagon.pose().local(player.position());
                PacketDistributor.sendToServer(new WagonNetwork.Standing(wagon.getId(),(float)local.x,(float)local.y,(float)local.z,
                    player.getYRot(),player.getXRot(),true));
                return;
            }
        }
        connection.send(packet);
    }
}
