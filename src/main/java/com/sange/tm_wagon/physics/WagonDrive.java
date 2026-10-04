package com.sange.tm_wagon.physics;

/** Signed longitudinal speed, in blocks/tick. No extra collision queries or packets. */
public final class WagonDrive {
    public static final double ACCELERATION=WagonPhysics.FORWARD_SPEED/30;
    public static final double REVERSE_ACCELERATION=WagonPhysics.REVERSE_SPEED/15;
    public static final double COAST_DECELERATION=WagonPhysics.FORWARD_SPEED/28;
    public static final double BRAKE_DECELERATION=WagonPhysics.FORWARD_SPEED/7;
    private double speed;

    public double speed() { return speed; }
    public void reset() { speed=0; }
    public void load(double value) {
        speed=Double.isFinite(value)?Math.clamp(value,-WagonPhysics.REVERSE_SPEED,WagonPhysics.FORWARD_SPEED*1.5):0;
    }
    public double tick(int input,double limit) {
        if(input==0||limit==0) speed=approach(speed,0,COAST_DECELERATION);
        // Always spend one tick stopped before applying power in the opposite direction.
        else if(speed*input<0) speed=approach(speed,0,BRAKE_DECELERATION);
        else {
            double target=input>0?limit:-limit;
            double rate=Math.abs(speed)>limit?COAST_DECELERATION:input>0?ACCELERATION:REVERSE_ACCELERATION;
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
