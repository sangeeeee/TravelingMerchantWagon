package com.sange.tm_wagon.client;

import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/** A wagon surface still occludes objects behind it, but is not an ordinary attack target. */
@EventBusSubscriber(modid=TravelingMerchantWagon.MODID,value=Dist.CLIENT)
public final class WagonAttackInput {
    @SubscribeEvent public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        if(!event.isAttack())return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||!(mc.hitResult instanceof EntityHitResult hit)||!(hit.getEntity() instanceof WagonEntity))return;
        // Recheck at the click too: the camera/cart may have moved since the frame's outline was picked.
        if(mc.getCameraEntity()!=null) {
            var resolved=WagonWorldBlockPicking.resolve(mc.getCameraEntity(),mc.player.blockInteractionRange(),1,mc.hitResult);
            if(resolved instanceof net.minecraft.world.phys.BlockHitResult block) {
                mc.hitResult=block;mc.crosshairPickEntity=null;return;
            }
        }
        if(!mc.player.getMainHandItem().is(WagonContent.DISMANTLING_HAMMER.get())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
    private WagonAttackInput() {}
}
