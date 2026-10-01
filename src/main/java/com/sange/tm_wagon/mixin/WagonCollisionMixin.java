package com.sange.tm_wagon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep vanilla's spatial query and filters; substitute only wagon geometry. */
@Mixin(EntityGetter.class)
public interface WagonCollisionMixin {
    @ModifyExpressionValue(method="getEntityCollisions",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/EntityGetter;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private java.util.List<Entity> tm_wagon$filterEmptyInterior(java.util.List<Entity> original,
            @Local(argsOnly=true) net.minecraft.world.phys.AABB area) {
        // noCollision uses list.isEmpty(), so a broad-phase hit in empty cargo
        // space must be removed rather than returned as an empty shape.
        java.util.List<Entity> filtered=null;
        for (int i=0;i<original.size();i++) {
            Entity entity=original.get(i);
            boolean keep=!(entity instanceof WagonEntity wagon) || net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(
                wagon.collisionShape(),net.minecraft.world.phys.shapes.Shapes.create(area.inflate(1e-7)),net.minecraft.world.phys.shapes.BooleanOp.AND);
            if (!keep && filtered==null) filtered=new java.util.ArrayList<>(original.subList(0,i));
            else if (keep && filtered!=null) filtered.add(entity);
        }
        return filtered==null ? original : filtered;
    }
    @ModifyExpressionValue(method={"getEntityCollisions","isUnobstructed"},
        at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/shapes/Shapes;create(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape tm_wagon$compoundShape(VoxelShape original,@Local(ordinal=1) Entity entity) {
        return entity instanceof WagonEntity wagon ? wagon.collisionShape() : original;
    }
}
