package com.sange.tm_wagon.core;

import com.sange.tm_wagon.assembly.WagonPart;

/** Immutable driving rules in blocks/tick, independent of Minecraft and mod loaders. */
public final class DrivingParameters {
    public record Speed(double forward,double reverse) {}
    public record Band(int percent,double multiplier) {}
    public record DraftTeam(double accelerationMultiplier,double cargoSpeedPenaltyMultiplier) {}
    public record Settings(Speed small,Speed longBody,Speed wide,double boost,double slotPenalty,double boostFloor,
                           DraftTeam doubleTeam,
                           double forwardAcceleration,double reverseAcceleration,double coast,double brake,
                           double unloadedMultiplier,Band light,Band medium,Band full) {
        public Settings {
            boostFloor=Math.min(boost,boostFloor);
            light=new Band(light.percent(),Math.min(unloadedMultiplier,light.multiplier()));
            medium=new Band(Math.max(light.percent(),medium.percent()),Math.min(light.multiplier(),medium.multiplier()));
            full=new Band(Math.max(medium.percent(),full.percent()),Math.min(medium.multiplier(),full.multiplier()));
        }
        public Speed speed(WagonPart body) { return body==WagonPart.WIDE_CARGO_BODY?wide:body==WagonPart.LONG_CARGO_BODY?longBody:small; }
        public double accelerationMultiplier(int occupied,int capacity) {
            int used=Math.max(0,Math.min(occupied,Math.max(1,capacity)))*100,total=Math.max(1,capacity);
            if(used>=full.percent()*total)return full.multiplier();
            if(used>=medium.percent()*total)return medium.multiplier();
            if(used>=light.percent()*total)return light.multiplier();
            return unloadedMultiplier;
        }
        public double forward(WagonPart body,int occupied,boolean boosted,boolean doubleTeam) {
            double base=speed(body).forward();
            double penalty=slotPenalty*(doubleTeam?this.doubleTeam.cargoSpeedPenaltyMultiplier():1);
            return boosted?Math.max(base*boostFloor,base*boost-Math.max(0,occupied)*penalty):base;
        }
        public double maxForward() { return Math.max(small.forward(),Math.max(longBody.forward(),wide.forward()))*boost; }
        public double maxReverse() { return Math.max(small.reverse(),Math.max(longBody.reverse(),wide.reverse())); }
    }
    public static final Settings DEFAULTS=new Settings(new Speed(5.616/20,1.404/20),new Speed(4.68/20,1.17/20),new Speed(3.744/20,.936/20),
        1.8,.0117,1,new DraftTeam(1.2,.85),.00975,.004875,.234/28,.234/7,1.3,new Band(30,1.1),new Band(60,.9),new Band(100,.6));
    private DrivingParameters() {}
}
