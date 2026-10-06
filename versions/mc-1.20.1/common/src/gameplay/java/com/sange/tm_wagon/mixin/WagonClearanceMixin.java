package com.sange.tm_wagon.mixin;
import com.sange.tm_wagon.physics.WagonCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
/** Override the inherited interface default on the concrete world for Mixin 0.8.5. */
@Mixin(Level.class)
public abstract class WagonClearanceMixin implements CollisionGetter {
    @Override public boolean noCollision(Entity excluded,AABB area) {
        for(var shape:getBlockCollisions(excluded,area))if(!shape.isEmpty())return false;
        if(!getEntityCollisions(excluded,area).isEmpty())return false;
        var border=getWorldBorder();
        if(excluded!=null&&border.isInsideCloseToBorder(excluded,area)&&net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(border.getCollisionShape(),net.minecraft.world.phys.shapes.Shapes.create(area),net.minecraft.world.phys.shapes.BooleanOp.AND))return false;
        return WagonCollision.unobstructed((Level)(Object)this,excluded,area);
    }
}
