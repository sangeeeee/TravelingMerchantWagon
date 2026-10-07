package com.sange.tm_wagon.client;

import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.network.WagonNetwork;
import net.minecraft.client.Minecraft;

public final class WagonDrivingInput {
    private static boolean previousSprint;
    private static int previousId=-1,previousForward,previousSteering,heartbeat;
    public static void tick() {
        Minecraft mc=Minecraft.getInstance();
        if (mc.player==null || !(mc.player.getVehicle() instanceof WagonEntity wagon) || wagon.driver()!=mc.player) {
            previousId=-1;return;
        }
        boolean enabled=mc.screen==null && mc.isWindowActive();
        // Brake/reverse wins when both movement keys are held.
        int forward=enabled ? (mc.options.keyDown.isDown()?-1:mc.options.keyUp.isDown()?1:0) : 0;
        int steer=enabled ? (mc.options.keyRight.isDown()?1:0)-(mc.options.keyLeft.isDown()?1:0) : 0;
        boolean sprint=enabled&&mc.options.keySprint.isDown();
        if (sprint!=previousSprint || previousId!=wagon.getId() || forward!=previousForward || steer!=previousSteering || ++heartbeat>=5) {
            com.sange.tm_wagon.network.WagonPackets.sendToServer(new WagonNetwork.Input(wagon.getId(),(byte)forward,(byte)steer,sprint));
            previousSprint=sprint;previousId=wagon.getId();previousForward=forward;previousSteering=steer;heartbeat=0;
        }
    }
    private WagonDrivingInput() {}
}
