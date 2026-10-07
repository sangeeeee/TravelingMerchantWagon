package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.assembly.AssemblyFrameBlockEntity;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Before BlockItem placement, including sneaking: cargo must never become a world block. */
@EventBusSubscriber(modid=TravelingMerchantWagon.MODID)
public final class CargoInteractions {
    // Carry On handles world placement at HIGH; cargo must consume the selected slot first.
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void block(PlayerInteractEvent.RightClickBlock event) {
        var frame=AssemblyFrameBlockEntity.find(event.getLevel(),event.getPos());
        if(frame==null||event.getPos().equals(frame.getBlockPos()))return;
        var result=frame.cargo().interact(event.getEntity(),event.getHand(),frame.cargoPose().local(event.getHitVec().getLocation()));
        if(result!=InteractionResult.PASS) { event.setCancellationResult(result);event.setCanceled(true); }
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void specific(PlayerInteractEvent.EntityInteractSpecific event) {
        var result=com.sange.tm_wagon.compat.MaidCompat.capture(event.getEntity(),event.getTarget(),event.getHand());
        if(result!=InteractionResult.PASS) { event.setCancellationResult(result);event.setCanceled(true); }
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void entity(PlayerInteractEvent.EntityInteract event) {
        var result=com.sange.tm_wagon.compat.MaidCompat.capture(event.getEntity(),event.getTarget(),event.getHand());
        if(result!=InteractionResult.PASS) { event.setCancellationResult(result);event.setCanceled(true); }
    }
    private CargoInteractions() {}
}
