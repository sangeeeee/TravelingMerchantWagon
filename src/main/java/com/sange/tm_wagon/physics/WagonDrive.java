package com.sange.tm_wagon.physics;

import com.sange.tm_wagon.DrivingConfig;

/** Signed longitudinal speed, in blocks/tick. No extra collision queries or packets. */
public final class WagonDrive {
    private double speed;

    public double speed() { return speed; }
    public void reset() { speed=0; }
    public void load(double value) {
        speed=Double.isFinite(value)?Math.clamp(value,-WagonSpeed.maxReverse(),WagonSpeed.maxForward()):0;
    }
    public double tick(int input,double limit) { return tick(input,limit,0,1,false); }
    public double tick(int input,double limit,int occupied,int capacity,boolean doubleTeam) {
        var config=DrivingConfig.get();
        if(input==0||limit==0) speed=approach(speed,0,config.coast());
        // Always spend one tick stopped before applying power in the opposite direction.
        else if(speed*input<0) speed=approach(speed,0,config.brake());
        else {
            double target=input>0?limit:-limit;
            double rate=Math.abs(speed)>limit?config.coast():(input>0?config.forwardAcceleration():config.reverseAcceleration())
                *config.accelerationMultiplier(occupied,capacity)*(doubleTeam?config.doubleTeam().accelerationMultiplier():1);
            speed=approach(speed,target,rate);
        }
        return speed;
    }
    /** A blocked wagon must not accumulate invisible speed against the obstacle. */
    public void acceptMovement(double actual) {
        speed=Math.copySign(Math.min(Math.abs(speed),Math.max(0,actual*Math.signum(speed))),speed);
    }
    private static double approach(double current,double target,double amount) {
        return Math.abs(target-current)<=amount?target:current+Math.copySign(amount,target-current);
    }
}
