package com.sange.tm_wagon.physics;

import com.sange.tm_wagon.DrivingConfig;
import com.sange.tm_wagon.core.DriveDynamics;

/** Connect the shared acceleration model to this loader's live server configuration. */
public final class WagonDrive extends DriveDynamics {
    public void load(double value) { super.load(value,DrivingConfig.get()); }
    public double tick(int input,double limit) { return tick(input,limit,0,1,false); }
    public double tick(int input,double limit,int occupied,int capacity,boolean doubleTeam) {
        return super.tick(input,limit,occupied,capacity,doubleTeam,DrivingConfig.get());
    }
}
