package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.entity.WagonSpatialIndex;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class WagonQueryMixin {
    @Inject(method="getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",at=@At("RETURN"))
    private void tm_wagon$includeLongBounds(Entity excluded,AABB bounds,Predicate<? super Entity> predicate,CallbackInfoReturnable<List<Entity>> callback) {
        var result=callback.getReturnValue();
        for (Entity wagon : WagonSpatialIndex.candidates((Level)(Object)this,bounds))
            if (wagon!=excluded && predicate.test(wagon) && !result.contains(wagon)) result.add(wagon);
    }
    @Inject(method="getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V",at=@At("RETURN"))
    private <T extends Entity> void tm_wagon$includeTypedBounds(EntityTypeTest<Entity,T> test,AABB bounds,Predicate<? super T> predicate,List<? super T> output,int limit,CallbackInfo callback) {
        if (output.size()>=limit) return;
        for (Entity wagon : WagonSpatialIndex.candidates((Level)(Object)this,bounds)) {
            T value=test.tryCast(wagon);
            if (value!=null && predicate.test(value) && !output.contains(value)) { output.add(value);if (output.size()>=limit) break; }
        }
    }
}
