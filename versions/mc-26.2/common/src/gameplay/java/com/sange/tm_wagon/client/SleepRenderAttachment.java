package com.sange.tm_wagon.client;
import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.world.phys.Vec3;
public interface SleepRenderAttachment {
    WagonPose tm_wagon$sleepPose();
    Vec3 tm_wagon$sleepOffset();
    float tm_wagon$bedRotation();
    void tm_wagon$setSleepPose(WagonPose pose,Vec3 offset,float bedRotation);
}
