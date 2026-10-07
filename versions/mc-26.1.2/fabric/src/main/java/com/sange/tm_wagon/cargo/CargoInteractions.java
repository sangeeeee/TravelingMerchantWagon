package com.sange.tm_wagon.cargo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

import com.sange.tm_wagon.TravelingMerchantWagon;
import com.sange.tm_wagon.assembly.AssemblyFrameBlockEntity;
import net.minecraft.world.InteractionResult;

/** Before BlockItem placement, including sneaking: cargo must never become a world block. */

public final class CargoInteractions {
    // Carry On handles world placement at HIGH; cargo must consume the selected slot first.
    
    private CargoInteractions() {}
public static InteractionResult block(Player player,Level level,InteractionHand hand,BlockHitResult hit) {
        var frame=AssemblyFrameBlockEntity.find(level,hit.getBlockPos());
        if(frame==null||hit.getBlockPos().equals(frame.getBlockPos()))return InteractionResult.PASS;
        return frame.cargo().interact(player,hand,frame.cargoPose().local(hit.getLocation()));
    }
}
