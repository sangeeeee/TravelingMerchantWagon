package com.sange.tm_wagon.client;

import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.entity.WagonSpatialIndex;
import com.sange.tm_wagon.network.WagonNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/** Contact alone is not a push. Send walking intent while pressed against an actual cart surface. */

public final class WagonPushingInput {
    private static Level previousLevel;
    private static int previousId=-1,previousForward,previousSideways,heartbeat;
    public static void tick() {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=previousLevel) { previousLevel=mc.level;previousId=-1;heartbeat=0; }
        if(mc.player==null||mc.level==null) { previousId=-1;return; }
        boolean enabled=mc.gui.screen()==null&&mc.isWindowActive()&&!mc.isPaused()&&!mc.player.isPassenger();
        int forward=enabled?(mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0):0;
        int sideways=enabled?(mc.options.keyLeft.isDown()?1:0)-(mc.options.keyRight.isDown()?1:0):0;
        WagonEntity target=null;double nearest=Double.POSITIVE_INFINITY;
        // Entity floors set onGround too. Skip even the spatial lookup and push packets on a wagon or in midair.
        if((forward!=0||sideways!=0)&&!mc.options.keyJump.isDown()&&WagonEntity.hasGroundForPushing(mc.player))
        for(var wagon:WagonSpatialIndex.candidates(mc.level,mc.player.getBoundingBox().inflate(WagonEntity.PUSH_CONTACT_MARGIN))) {
            double distance=mc.player.distanceToSqr(wagon);
            if(distance<nearest&&wagon.pushDirection(mc.player,forward,sideways)!=0) { target=wagon;nearest=distance; }
        }
        int id=target==null?-1:target.getId();
        if(id==-1)heartbeat=0;
        if(previousId!=id&&previousId!=-1)com.sange.tm_wagon.network.WagonPackets.sendToServer(new WagonNetwork.Push(previousId,(byte)0,(byte)0));
        if(id!=-1&&(id!=previousId||forward!=previousForward||sideways!=previousSideways||++heartbeat>=2)) {
            com.sange.tm_wagon.network.WagonPackets.sendToServer(new WagonNetwork.Push(id,(byte)forward,(byte)sideways));heartbeat=0;
        }
        previousId=id;previousForward=forward;previousSideways=sideways;
    }
    private WagonPushingInput() {}
}
