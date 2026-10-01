package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.entity.WagonPlatform;
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

/** Individual volumes avoid constructing a huge voxel union at every moving pose. */
@Mixin(EntityGetter.class)
public interface WagonCollisionMixin {
    @Inject(method="getEntityCollisions",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$movingVolumes(Entity excluded,AABB area,CallbackInfoReturnable<List<VoxelShape>> callback) {
        EntityGetter world=(EntityGetter)this;
        if(area.getSize()<1e-7) { callback.setReturnValue(List.of());return; }
        var predicate=excluded==null?EntitySelector.CAN_BE_COLLIDED_WITH:EntitySelector.NO_SPECTATORS.and(excluded::canCollideWith);
        var result=new ArrayList<VoxelShape>();
        WagonEntity carrier=WagonPlatform.excludedWagon();
        for(Entity entity:world.getEntities(excluded,area.inflate(1e-7),predicate)) {
            if(entity==carrier)continue;
            if(entity instanceof WagonEntity wagon) {
                for(AABB box:wagon.collisionBoxes())if(box.intersects(area.inflate(1e-7)))result.add(Shapes.create(box));
            }else result.add(Shapes.create(entity.getBoundingBox()));
        }
        callback.setReturnValue(result);
    }
    @Inject(method="isUnobstructed",at=@At("HEAD"),cancellable=true)
    private void tm_wagon$unobstructed(Entity excluded,VoxelShape shape,CallbackInfoReturnable<Boolean> callback) {
        if(shape.isEmpty()) { callback.setReturnValue(true);return; }
        for(Entity entity:((EntityGetter)this).getEntities(excluded,shape.bounds())) {
            if(entity.isRemoved()||!entity.blocksBuilding||(excluded!=null&&entity.isPassengerOfSameVehicle(excluded)))continue;
            if(entity instanceof WagonEntity wagon) {
                for(AABB box:shape.toAabbs())if(wagon.intersects(box)) { callback.setReturnValue(false);return; }
            }else if(Shapes.joinIsNotEmpty(shape,Shapes.create(entity.getBoundingBox()),net.minecraft.world.phys.shapes.BooleanOp.AND)) {
                callback.setReturnValue(false);return;
            }
        }
        callback.setReturnValue(true);
    }
}
