package com.sange.tm_wagon.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sange.tm_wagon.client.WagonWorldBlockPicking;
import com.sange.tm_wagon.entity.WagonSpatialIndex;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=GameRenderer.class,priority=900)
public abstract class WagonWorldBlockPickingMixin {
    @WrapMethod(method="pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;")
    private HitResult tm_wagon$visibleOutline(Entity camera,double blockReach,double entityReach,float partial,Operation<HitResult> original){
        var result=original.call(camera,blockReach,entityReach,partial);
        if(result.getType()==HitResult.Type.MISS&&net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("swingthrough")){
            Vec3 start=camera.getEyePosition(partial),end=start.add(camera.getViewVector(partial).scale(blockReach));
            AABB bounds=new AABB(start,end).inflate(1);
            if(!WagonSpatialIndex.candidates(camera.level(),bounds).isEmpty()){
                var block=camera.level().clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,camera));
                if(block.getType()==HitResult.Type.BLOCK){
                    double distance=start.distanceToSqr(block.getLocation());
                    var obstruction=ProjectileUtil.getEntityHitResult(camera,start,end,bounds,e->!e.isSpectator()&&e.isPickable(),distance);
                    result=obstruction!=null&&start.distanceToSqr(obstruction.getLocation())+1e-8<distance?obstruction:block;
                }
            }
        }
        return WagonWorldBlockPicking.resolve(camera,blockReach,partial,result);
    }
}
