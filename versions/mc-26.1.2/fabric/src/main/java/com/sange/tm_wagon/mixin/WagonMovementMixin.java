package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.physics.WagonCollision;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sange.tm_wagon.compat.StructureCollision;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Both client movement and server packet validation enter these same methods. */
@Mixin(Entity.class)
public abstract class WagonMovementMixin {
    @Unique private WagonCollision.Contacts tm_wagon$contacts;
    @Unique private Vec3 tm_wagon$requested;

    @Inject(method="move",at=@At("HEAD"))
    private void tm_wagon$reset(MoverType type,Vec3 movement,CallbackInfo callback) { tm_wagon$contacts=null; }

    @WrapMethod(method="collide")
    private Vec3 tm_wagon$recordAcceptedContacts(Vec3 movement,Operation<Vec3> original) {
        Entity entity=(Entity)(Object)this;
        var recording=WagonCollision.record(entity,movement);tm_wagon$requested=movement;
        Vec3 result=null;
        try { result=original.call(movement);return result; }
        finally { if(recording!=null)tm_wagon$contacts=recording.finish(result); }
    }
    @WrapOperation(method="move",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;setOnGroundWithMovement(ZZLnet/minecraft/world/phys/Vec3;)V"))
    private void tm_wagon$surfaceMomentum(Entity entity,boolean grounded,boolean horizontal,Vec3 movement,Operation<Void> original) {
        original.call(entity,grounded,horizontal,movement);
        if(tm_wagon$contacts==null||!tm_wagon$contacts.wagon())return;
        entity.setDeltaMovement(tm_wagon$contacts.velocity(entity.getDeltaMovement()));
        if(!tm_wagon$contacts.worldVertical()) {
            boolean structureGround=StructureCollision.grounded(entity);
            entity.verticalCollision=tm_wagon$contacts.vertical()||structureGround;
            entity.verticalCollisionBelow=(tm_wagon$requested.y<0&&tm_wagon$contacts.floor())||structureGround;
            entity.setOnGroundWithMovement(entity.verticalCollisionBelow,movement);
            StructureCollision.updateGroundFlags(entity);
        }
    }
    @WrapOperation(method="move",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;setDeltaMovement(DDD)V"))
    private void tm_wagon$keepTangentVelocity(Entity entity,double x,double y,double z,Operation<Void> original) {
        if(tm_wagon$contacts==null||!tm_wagon$contacts.wagon())original.call(entity,x,y,z);
    }
    @WrapOperation(method="move",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;updateEntityMovementAfterFallOn(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;)V"))
    private void tm_wagon$actualVerticalContact(Block block,BlockGetter level,Entity entity,Operation<Void> original) {
        if(tm_wagon$contacts==null||!tm_wagon$contacts.wagon()||tm_wagon$contacts.worldVertical()||StructureCollision.grounded(entity))original.call(block,level,entity);
    }
    @Inject(method="move",at=@At("RETURN"))
    private void tm_wagon$release(MoverType type,Vec3 movement,CallbackInfo callback) { tm_wagon$contacts=null;tm_wagon$requested=null; }
    @Inject(method="collideBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;",at=@At("RETURN"),cancellable=true)
    private static void tm_wagon$orientedContact(Entity entity,Vec3 movement,AABB bounds,Level level,List<VoxelShape> shapes,CallbackInfoReturnable<Vec3> callback) {
        if(!WagonCollision.vanillaOnly())callback.setReturnValue(WagonCollision.resolve(entity,movement,bounds,level,shapes,callback.getReturnValue()));
    }
    @Inject(method="collide",at=@At("RETURN"),cancellable=true)
    private void tm_wagon$step(Vec3 movement,CallbackInfoReturnable<Vec3> callback) {
        callback.setReturnValue(WagonCollision.step((Entity)(Object)this,movement,callback.getReturnValue()));
    }
}
