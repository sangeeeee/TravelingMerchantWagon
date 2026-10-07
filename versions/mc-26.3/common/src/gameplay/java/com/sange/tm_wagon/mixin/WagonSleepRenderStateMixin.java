package com.sange.tm_wagon.mixin;
import com.sange.tm_wagon.client.SleepRenderAttachment;
import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
@Mixin(EntityRenderState.class)
public abstract class WagonSleepRenderStateMixin implements SleepRenderAttachment {
    @Unique private WagonPose tm_wagon$pose;
    @Unique private Vec3 tm_wagon$offset;
    @Unique private float tm_wagon$rotation;
    public WagonPose tm_wagon$sleepPose() { return tm_wagon$pose; }
    public Vec3 tm_wagon$sleepOffset() { return tm_wagon$offset; }
    public float tm_wagon$bedRotation() { return tm_wagon$rotation; }
    public void tm_wagon$setSleepPose(WagonPose pose,Vec3 offset,float rotation) { tm_wagon$pose=pose;tm_wagon$offset=offset;tm_wagon$rotation=rotation; }
}
