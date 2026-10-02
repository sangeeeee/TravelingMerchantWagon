package com.sange.tm_wagon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.sange.tm_wagon.entity.WagonEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The large query bounds must not prevent clicking the lift through empty space. */
@Mixin(ProjectileUtil.class)
public abstract class WagonPickingMixin {
    @ModifyExpressionValue(method="getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"))
    private static Optional<Vec3> tm_wagon$pickParts(Optional<Vec3> original,@Local(ordinal=2) Entity candidate,
            @Local(argsOnly=true,ordinal=0) Vec3 start,@Local(argsOnly=true,ordinal=1) Vec3 end) {
        if (!(candidate instanceof WagonEntity wagon)) return original;
        return wagon.pick(start,end);
    }
    @ModifyExpressionValue(method="getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/AABB;contains(Lnet/minecraft/world/phys/Vec3;)Z"))
    private static boolean tm_wagon$insideSolid(boolean original,@Local(ordinal=2) Entity candidate,
            @Local(argsOnly=true,ordinal=0) Vec3 start) {
        if (!(candidate instanceof WagonEntity wagon)) return original;
        return wagon.containsPickPoint(start);
    }
}
