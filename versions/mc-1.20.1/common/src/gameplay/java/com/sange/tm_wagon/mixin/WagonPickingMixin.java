package com.sange.tm_wagon.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.entity.WagonSpatialIndex;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/** One stable target for rendering, mining and interaction. The outer wagon AABB only discovers candidates. */
@Mixin(ProjectileUtil.class)
public abstract class WagonPickingMixin {
    @WrapMethod(method="getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;")
    private static EntityHitResult tm_wagon$pickParts(Entity source,Vec3 start,Vec3 end,AABB bounds,Predicate<Entity> predicate,
            double limit,Operation<EntityHitResult> original) {
        // Keep vanilla/modded picking for other entities. Do not allow the broad wagon bounds
        // to win before comparing actual surfaces, including when the eye is inside them.
        EntityHitResult best=original.call(source,start,end,bounds,predicate.and(e->!(e instanceof WagonEntity)),limit);
        double distance=best==null?limit:best.getEntity().getBoundingBox().inflate(best.getEntity().getPickRadius()).contains(start)
            ?0:start.distanceToSqr(best.getLocation());
        for(WagonEntity wagon:WagonSpatialIndex.candidates(source.level(),bounds)) {
            if(wagon==source||!predicate.test(wagon)
                ||wagon.getRootVehicle()==source.getRootVehicle()&&!wagon.canRiderInteract())continue;
            var hit=wagon.pick(start,end);
            if(hit.isEmpty())continue;
            double candidate=start.distanceToSqr(hit.get());
            if(candidate<distance||best==null&&candidate==0&&limit>=0) {
                distance=candidate;best=new EntityHitResult(wagon,hit.get());
            }
        }
        return best;
    }
}
