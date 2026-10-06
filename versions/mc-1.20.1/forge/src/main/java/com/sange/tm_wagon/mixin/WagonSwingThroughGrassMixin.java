package com.sange.tm_wagon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.sange.tm_wagon.entity.WagonSpatialIndex;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import java.util.List;

/** Clean Swing may hit living entities through grass, but not through solid wagon parts. */
@Pseudo
@Mixin(targets="com.ldtteam.cleanswing.CleanSwing",remap=false)
public abstract class WagonSwingThroughGrassMixin {
    @WrapOperation(method="onBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",remap=true),remap=false)
    private static List<Entity> tm_wagon$visibleTargets(Level level,Entity excluded,AABB area,Operation<List<Entity>> original,PlayerInteractEvent.LeftClickBlock event) {
        var targets=original.call(level,excluded,area);var player=event.getEntity();var start=player.getEyePosition();
        var wagons=WagonSpatialIndex.candidates(level,area);
        if(wagons.isEmpty())return targets;
        return targets.stream().filter(entity->{
            var end=entity.getBoundingBox().getCenter();
            for(var wagon:wagons) {var hit=wagon.pick(start,end);if(hit.isPresent()&&start.distanceToSqr(hit.get())+1e-8<start.distanceToSqr(end))return false;}
            return true;
        }).toList();
    }
}
