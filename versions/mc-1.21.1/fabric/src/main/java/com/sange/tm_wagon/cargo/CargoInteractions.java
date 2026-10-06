package com.sange.tm_wagon.cargo;
import com.sange.tm_wagon.assembly.AssemblyFrameBlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
/** Runs before BlockItem placement, also while sneaking. */
public final class CargoInteractions {
    public static InteractionResult block(Player player,Level level,InteractionHand hand,BlockHitResult hit) {
        var frame=AssemblyFrameBlockEntity.find(level,hit.getBlockPos());
        if(frame==null||hit.getBlockPos().equals(frame.getBlockPos()))return InteractionResult.PASS;
        return frame.cargo().interact(player,hand,frame.cargoPose().local(hit.getLocation()));
    }
    private CargoInteractions() {}
}
