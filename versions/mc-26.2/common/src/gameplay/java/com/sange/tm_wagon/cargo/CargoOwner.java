package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.world.level.Level;

/** Conversion moves entries between authoritative holders instead of cloning their inventories. */
public interface CargoOwner {
    default com.sange.tm_wagon.assembly.WagonPart cargoSeat() { return null; }
    default com.sange.tm_wagon.assembly.WagonPart cargoBody() { return com.sange.tm_wagon.assembly.WagonPart.CARGO_BODY; }
    Level cargoLevel();
    WagonPose cargoPose();
    boolean cargoLive();
    boolean cargoBusy();
    String cargoGeometryChanged();
    void cargoChanged(boolean visible);
    CargoHold cargo();
}
