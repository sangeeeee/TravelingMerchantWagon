package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.entity.WagonSpatialIndex;
import com.sange.tm_wagon.physics.WagonCollision;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class WagonPlayerCollisionMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="isEntityCollidingWithAnythingNew",at=@At("RETURN"),cancellable=true)
    private void tm_wagon$newContact(LevelReader level,net.minecraft.world.entity.Entity entity,AABB previous,double x,double y,double z,CallbackInfoReturnable<Boolean> callback) {
        if(callback.getReturnValue())return;
        AABB next=entity.getBoundingBox().move(x-entity.getX(),y-entity.getY(),z-entity.getZ()).deflate(1e-5);
        AABB before=previous.deflate(1e-5);
        for(var wagon:WagonSpatialIndex.candidates(entity.level(),next))if(WagonCollision.eligible(entity,wagon))
            for(var box:wagon.colliders())if(box.intersects(next)&&!box.intersects(before)) { callback.setReturnValue(true);return; }
    }
}
