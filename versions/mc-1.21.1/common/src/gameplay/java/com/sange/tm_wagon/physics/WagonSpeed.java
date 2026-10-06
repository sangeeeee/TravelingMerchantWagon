package com.sange.tm_wagon.physics;

import com.sange.tm_wagon.assembly.WagonPart;
import com.sange.tm_wagon.DrivingConfig;

/** Absolute per-slot penalty, calibrated against a full twelve-slot long wagon. */
public final class WagonSpeed {
    public static double maxForward() { return DrivingConfig.get().maxForward(); }
    public static double maxReverse() { return DrivingConfig.get().maxReverse(); }
    public static double forward(WagonPart body,int occupied,boolean boosted,boolean doubleTeam) {
        return DrivingConfig.get().forward(body,occupied,boosted,doubleTeam);
    }
    public static double reverse(WagonPart body) { return DrivingConfig.get().speed(body).reverse(); }
    private WagonSpeed() {}
}
