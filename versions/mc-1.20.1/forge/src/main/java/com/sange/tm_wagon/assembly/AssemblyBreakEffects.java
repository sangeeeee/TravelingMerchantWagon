package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.material.WoodMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** One successful player action produces one vanilla sound/particle event. */
final class AssemblyBreakEffects {
    static void play(Level level,BlockPos pos,Player player,BlockState original,WoodMaterial wood) {
        if(level.isClientSide)return;
        // The model is rendered by a BE and its cells may already be gone. Use
        // a vanilla plank state so particles have a real sprite and valid shape.
        var surface=Block.byItem(wood.planks()).defaultBlockState();
        // Client-side removal is deliberately not predicted for the multiblock.
        // Include the breaker, who therefore does not play a local break effect.
        level.levelEvent(2001,pos,Block.getId(surface));
        level.gameEvent(GameEvent.BLOCK_DESTROY,pos,GameEvent.Context.of(player,original));
    }
    private AssemblyBreakEffects() {}
}
