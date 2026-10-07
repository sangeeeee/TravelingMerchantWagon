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
    private void tm_wagon$save(net.minecraft.world.level.storage.ValueOutput tag,CallbackInfo cir) {
        if(tm_wagon$data!=null&&!tm_wagon$data.isEmpty())tag.store("tm_wagon:entity_data",CompoundTag.CODEC,tm_wagon$data.copy());
    }
    @Inject(method="load",at=@At("TAIL"))
    private void tm_wagon$load(net.minecraft.world.level.storage.ValueInput tag,CallbackInfo ci) {
        tm_wagon$data=tag.read("tm_wagon:entity_data",CompoundTag.CODEC).map(CompoundTag::copy).orElse(null);
    }
}
