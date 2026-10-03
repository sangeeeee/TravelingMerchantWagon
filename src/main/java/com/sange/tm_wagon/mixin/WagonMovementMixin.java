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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Both client movement and server packet validation enter these same methods. */
@Mixin(Entity.class)
public abstract class WagonMovementMixin {
    @Shadow private Vec3 collide(Vec3 movement) { throw new AssertionError(); }
    @Unique private WagonCollision.Contacts tm_wagon$contacts;
    @Unique private Vec3 tm_wagon$requested;

    @Inject(method="move",at=@At("HEAD"))
    private void tm_wagon$reset(MoverType type,Vec3 movement,CallbackInfo callback) { tm_wagon$contacts=null; }

    @Redirect(method="move",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 tm_wagon$recordAcceptedContacts(Entity entity,Vec3 movement) {
        var recording=WagonCollision.record(entity,movement);tm_wagon$requested=movement;
        Vec3 result=null;
        try { result=collide(movement);return result; }
        finally { if(recording!=null)tm_wagon$contacts=recording.finish(result); }
    }
    @Inject(method="move",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;setOnGroundWithMovement(ZLnet/minecraft/world/phys/Vec3;)V"))
    private void tm_wagon$surfaceMomentum(MoverType type,Vec3 movement,CallbackInfo callback) {
        if(tm_wagon$contacts==null||!tm_wagon$contacts.wagon())return;
        Entity entity=(Entity)(Object)this;
        entity.setDeltaMovement(tm_wagon$contacts.velocity(entity.getDeltaMovement()));
        if(!tm_wagon$contacts.worldVertical()) {
            entity.verticalCollision=tm_wagon$contacts.vertical();
            entity.verticalCollisionBelow=tm_wagon$requested.y<0&&tm_wagon$contacts.floor();
        }
    }
    @Redirect(method="move",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;setDeltaMovement(DDD)V"))
    private void tm_wagon$keepTangentVelocity(Entity entity,double x,double y,double z) {
        if(tm_wagon$contacts==null||!tm_wagon$contacts.wagon())entity.setDeltaMovement(x,y,z);
        // The accepted surface planes already removed the blocked velocity components.
    }
    @Redirect(method="move",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;updateEntityAfterFallOn(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;)V"))
    private void tm_wagon$actualVerticalContact(Block block,BlockGetter level,Entity entity) {
        if(tm_wagon$contacts==null||!tm_wagon$contacts.wagon()||tm_wagon$contacts.worldVertical())block.updateEntityAfterFallOn(level,entity);
    }
    @Inject(method="move",at=@At("RETURN"))
    private void tm_wagon$release(MoverType type,Vec3 movement,CallbackInfo callback) { tm_wagon$contacts=null;tm_wagon$requested=null; }
    @Inject(method="collideBoundingBox",at=@At("RETURN"),cancellable=true)
    private static void tm_wagon$orientedContact(Entity entity,Vec3 movement,AABB bounds,Level level,List<VoxelShape> shapes,CallbackInfoReturnable<Vec3> callback) {
        if(!WagonCollision.vanillaOnly())callback.setReturnValue(WagonCollision.resolve(entity,movement,bounds,level,shapes,callback.getReturnValue()));
    }
    @Inject(method="collide",at=@At("RETURN"),cancellable=true)
    private void tm_wagon$step(Vec3 movement,CallbackInfoReturnable<Vec3> callback) {
        callback.setReturnValue(WagonCollision.step((Entity)(Object)this,movement,callback.getReturnValue()));
    }
}
