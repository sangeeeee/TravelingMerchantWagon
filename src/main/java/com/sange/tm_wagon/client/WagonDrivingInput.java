package com.sange.tm_wagon.client;

import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.network.WagonNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=TravelingMerchantWagon.MODID,value=Dist.CLIENT)
public final class WagonDrivingInput {
    private static int previousId=-1,previousForward,previousSteering,heartbeat;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if (mc.player==null || !(mc.player.getVehicle() instanceof WagonEntity wagon) || wagon.driver()!=mc.player) {
            previousId=-1;return;
        }
        boolean enabled=mc.screen==null && mc.isWindowActive();
        int forward=enabled ? (mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0) : 0;
        int steer=enabled ? (mc.options.keyRight.isDown()?1:0)-(mc.options.keyLeft.isDown()?1:0) : 0;
        if (previousId!=wagon.getId() || forward!=previousForward || steer!=previousSteering || ++heartbeat>=5) {
            PacketDistributor.sendToServer(new WagonNetwork.Input(wagon.getId(),(byte)forward,(byte)steer));
            previousId=wagon.getId();previousForward=forward;previousSteering=steer;heartbeat=0;
        }
    }
    private WagonDrivingInput() {}
}
