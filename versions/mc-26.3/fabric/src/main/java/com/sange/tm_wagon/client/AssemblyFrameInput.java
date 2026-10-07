package com.sange.tm_wagon.client;

import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.assembly.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

/** Vanilla retries held right-click every four ticks. A lift needs a fresh
 * button press, even if its animation finishes while the button stays held. */
public final class AssemblyFrameInput {
    private static boolean consumedPress;
    public static void tick() {
        Minecraft mc=Minecraft.getInstance();
        if (mc.level==null || mc.gui.screen()!=null || !mc.options.keyUse.isDown()) consumedPress=false;
    }
    public static boolean blockUse() {
        Minecraft mc=Minecraft.getInstance();
        if (mc.level==null || mc.player==null || !(mc.hitResult instanceof BlockHitResult hit)) return false;
        var item=mc.player.getMainHandItem().getItem();
        if (item instanceof WagonPartItem || item instanceof AssemblyFrameItem) return false;
        if (mc.player.isSecondaryUseActive() && (!mc.player.getMainHandItem().isEmpty() || !mc.player.getOffhandItem().isEmpty())) return false;
        var frame=AssemblyFrameBlockEntity.find(mc.level,hit.getBlockPos());
        if (frame==null) return false;
        if (!hit.getBlockPos().equals(frame.getBlockPos())) {
            var cell=frame.layout().get(hit.getBlockPos());
            if (cell==null || !cell.framePart() || frame.hitSlot(hit.getBlockPos(),mc.player.getEyePosition(),
                hit.getLocation().add(mc.player.getLookAngle().scale(.01)))!=null) return false;
        }
        boolean blocked=consumedPress || frame.switching();consumedPress=true;return blocked;
    }
    private AssemblyFrameInput() {}
}
