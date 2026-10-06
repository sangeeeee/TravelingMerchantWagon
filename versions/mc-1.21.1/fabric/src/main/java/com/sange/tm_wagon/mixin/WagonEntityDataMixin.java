package com.sange.tm_wagon.mixin;

import com.sange.tm_wagon.platform.EntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class WagonEntityDataMixin implements EntityData {
    @Unique private CompoundTag tm_wagon$data;
    @Override public CompoundTag tm_wagon$data() {
        if(tm_wagon$data==null)tm_wagon$data=new CompoundTag();
        return tm_wagon$data;
    }
    @Inject(method="saveWithoutId",at=@At("RETURN"))
    private void tm_wagon$save(CompoundTag tag,CallbackInfoReturnable<CompoundTag> cir) {
        if(tm_wagon$data!=null&&!tm_wagon$data.isEmpty())tag.put("tm_wagon:entity_data",tm_wagon$data.copy());
    }
    @Inject(method="load",at=@At("TAIL"))
    private void tm_wagon$load(CompoundTag tag,CallbackInfo ci) {
        tm_wagon$data=tag.contains("tm_wagon:entity_data",10)?tag.getCompound("tm_wagon:entity_data").copy():null;
    }
}
