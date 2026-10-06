package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.entity.WagonCrowd;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wagons have a separate oriented narrow phase, never enlarged vanilla voxel colliders. */
@Mixin(net.minecraft.world.level.Level.class)
public abstract class WagonCollisionMixin implements EntityGetter {
    @Override public List<VoxelShape> getEntityCollisions(Entity excluded,AABB area) {
        EntityGetter world=(EntityGetter)this;
        if(area.getSize()<1e-7) { return List.of(); }
        var predicate=excluded==null?EntitySelector.CAN_BE_COLLIDED_WITH:EntitySelector.NO_SPECTATORS.and(excluded::canCollideWith);
        var result=new ArrayList<VoxelShape>();
        WagonEntity clearedWagon=WagonCrowd.excludedWagon();
        for(Entity entity:world.getEntities(excluded,area.inflate(1e-7),predicate)) {
            if(entity==clearedWagon)continue;
            if(!(entity instanceof WagonEntity))result.add(Shapes.create(entity.getBoundingBox()));
        }
        return result;
    }
    @Override public boolean isUnobstructed(Entity excluded,VoxelShape shape) {
        if(shape.isEmpty()) { return true; }
        for(Entity entity:((EntityGetter)this).getEntities(excluded,shape.bounds())) {
            if(entity.isRemoved()||!entity.blocksBuilding||(excluded!=null&&entity.isPassengerOfSameVehicle(excluded)))continue;
            if(entity instanceof WagonEntity wagon) {
                for(AABB box:shape.toAabbs())if(wagon.intersects(box)) { return false; }
            }else if(Shapes.joinIsNotEmpty(shape,Shapes.create(entity.getBoundingBox()),net.minecraft.world.phys.shapes.BooleanOp.AND)) {
                return false;
            }
        }
        return true;
    }
}
