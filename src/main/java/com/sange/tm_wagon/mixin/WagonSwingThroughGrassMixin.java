package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.entity.WagonSpatialIndex;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SwingThroughGrass otherwise redirects server mining into a no-op attack on the wagon's broad AABB. */
@Pseudo
@Mixin(targets="dev.nikrecs.swingthroughgrass.SwingThroughGrassAttackHandler",remap=false)
public abstract class WagonSwingThroughGrassMixin {
    @Inject(method="isAttackable",at=@At("HEAD"),cancellable=true,remap=false)
    private static void tm_wagon$notAnAttackTarget(Entity entity,CallbackInfoReturnable<Boolean> callback) {
        if(entity instanceof WagonEntity)callback.setReturnValue(false);
    }

    @Inject(method="isObstructedBySolidBlock",at=@At("RETURN"),cancellable=true,remap=false)
    private static void tm_wagon$actualSurfaceOccludes(Player player,Vec3 start,Vec3 end,CallbackInfoReturnable<Boolean> callback) {
        if(callback.getReturnValue())return;
        double distance=start.distanceToSqr(end);
        for(var wagon:WagonSpatialIndex.candidates(player.level(),new AABB(start,end).inflate(1e-5))) {
            var hit=wagon.pick(start,end);
            if(hit.isPresent()&&start.distanceToSqr(hit.get())+1e-8<distance) {
                callback.setReturnValue(true);return;
            }
        }
    }
}
