package com.sange.tm_wagon.assembly;

/** Shared rigid-link motion, in model units (16 units per block). */
public final class FrameMotion {
    public static final int TICKS = 20;
    public static final double EXTENDED_ANGLE = Math.toDegrees(Math.asin(.84));
    public static final double FOLDED_ANGLE = 12;
    public static double platformOffset(double progress) {
        return 20 * (Math.sin(Math.toRadians(EXTENDED_ANGLE + (FOLDED_ANGLE-EXTENDED_ANGLE)*progress))-.84);
    }
    public static double collisionTop(double progress) {
        return Math.ceil((22+platformOffset(progress))*2-1e-7)/32;
    }
    private FrameMotion() {}
}
