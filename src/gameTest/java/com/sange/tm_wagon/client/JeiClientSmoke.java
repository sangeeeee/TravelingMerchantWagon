package com.sange.tm_wagon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Starts only a disposable fixture; no JEI linkage when the optional mod is absent. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class JeiClientSmoke {
    private static boolean opened;
    private static int ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("tm_wagon.jeiSmokeTest"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();
        if(++ticks>2400)throw new IllegalStateException("JEI smoke timed out");
        if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
            opened=true;mc.createWorldOpenFlows().openWorld("repro",()->{throw new IllegalStateException("Cannot open JEI fixture");});
        }
        if(mc.level!=null&&mc.player!=null)try {
            Class.forName("com.sange.tm_wagon.client.JeiSmokePlugin").getMethod("tick").invoke(null);
        } catch(ReflectiveOperationException e) { throw new IllegalStateException("JEI smoke failed",e); }
    }
}
