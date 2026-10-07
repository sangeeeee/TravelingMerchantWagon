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
    /** Weapon attack ranges use a separate multi-target ray in 26.2. Apply the
     * same actual-surface test there, rather than the entity's broad bounds. */
    @WrapMethod(method="getHitEntitiesAlong(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ljava/util/function/Predicate;Lnet/minecraft/world/phys/Vec3;FLnet/minecraft/world/level/ClipContext$Block;)Lcom/mojang/datafixers/util/Either;")
    private static com.mojang.datafixers.util.Either<net.minecraft.world.phys.BlockHitResult,java.util.Collection<EntityHitResult>> tm_wagon$attackRange(
            Entity source,Vec3 origin,Vec3 from,Predicate<Entity> predicate,Vec3 to,float margin,
            net.minecraft.world.level.ClipContext.Block clip,
            Operation<com.mojang.datafixers.util.Either<net.minecraft.world.phys.BlockHitResult,java.util.Collection<EntityHitResult>>> original) {
        var result=original.call(source,origin,from,predicate.and(e->!(e instanceof WagonEntity)),to,margin,clip);
        var block=source.level().clipIncludingBorder(new net.minecraft.world.level.ClipContext(origin,to,clip,net.minecraft.world.level.ClipContext.Fluid.NONE,source));
        Vec3 end=block.getType()==net.minecraft.world.phys.HitResult.Type.MISS?to:block.getLocation();
        if(origin.distanceToSqr(end)<origin.distanceToSqr(from))return result;
        EntityHitResult nearest=null;double limit=Double.POSITIVE_INFINITY;
        for(var wagon:WagonSpatialIndex.candidates(source.level(),new AABB(from,end).inflate(1))) {
            if(wagon==source||!predicate.test(wagon)||wagon.getRootVehicle()==source.getRootVehicle()&&!wagon.canRiderInteract())continue;
            var hit=wagon.pick(from,end);if(hit.isEmpty())continue;
            double distance=origin.distanceToSqr(hit.get());
            if(distance<limit) {limit=distance;nearest=new EntityHitResult(wagon,hit.get());}
        }
        if(nearest==null)return result;
        var hits=new java.util.ArrayList<EntityHitResult>();
        for(var hit:result.right().orElse(java.util.List.of()))if(origin.distanceToSqr(hit.getLocation())<=limit)hits.add(hit);
        hits.add(nearest);return com.mojang.datafixers.util.Either.right(hits);
    }
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
