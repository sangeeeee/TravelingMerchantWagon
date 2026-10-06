package com.sange.tm_wagon.client;

import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.assembly.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.InputEvent;

/** Vanilla retries held right-click every four ticks. A lift needs a fresh
 * button press, even if its animation finishes while the button stays held. */
@EventBusSubscriber(modid=TravelingMerchantWagon.MODID,value=Dist.CLIENT)
public final class AssemblyFrameInput {
    private static boolean consumedPress;
    @SubscribeEvent public static void tick(ClientTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();
        if (mc.level==null || mc.screen!=null || !mc.options.keyUse.isDown()) consumedPress=false;
    }
    @SubscribeEvent public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand()!=InteractionHand.MAIN_HAND) return;
        Minecraft mc=Minecraft.getInstance();
        if (mc.level==null || mc.player==null || !(mc.hitResult instanceof BlockHitResult hit)) return;
        var item=mc.player.getMainHandItem().getItem();
        if (item instanceof WagonPartItem || item instanceof AssemblyFrameItem) return;
        if (mc.player.isSecondaryUseActive() && (!mc.player.getMainHandItem().isEmpty() || !mc.player.getOffhandItem().isEmpty())) return;
        var frame=AssemblyFrameBlockEntity.find(mc.level,hit.getBlockPos());
        if (frame==null) return;
        if (!hit.getBlockPos().equals(frame.getBlockPos())) {
            var cell=frame.layout().get(hit.getBlockPos());
            if (cell==null || !cell.framePart() || frame.hitSlot(hit.getBlockPos(),mc.player.getEyePosition(),
                hit.getLocation().add(mc.player.getLookAngle().scale(.01)))!=null) return;
        }
        if (consumedPress || frame.switching()) { event.setCanceled(true);event.setSwingHand(false); }
        consumedPress=true;
    }
    private AssemblyFrameInput() {}
}
