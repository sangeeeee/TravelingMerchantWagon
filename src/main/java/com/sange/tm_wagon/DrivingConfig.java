package com.sange.tm_wagon;

import com.sange.tm_wagon.assembly.WagonPart;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative, immutable snapshots; convert user-facing seconds to ticks once on reload. */
public final class DrivingConfig {
    public record Speed(double forward,double reverse) {}
    public record Band(int percent,double multiplier) {}
    public record Settings(Speed small,Speed longBody,Speed wide,double boost,double slotPenalty,double boostFloor,
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
            int used=Math.clamp(occupied,0,Math.max(1,capacity))*100,total=Math.max(1,capacity);
            if(used>=full.percent()*total)return full.multiplier();
            if(used>=medium.percent()*total)return medium.multiplier();
            if(used>=light.percent()*total)return light.multiplier();
            return unloadedMultiplier;
        }
        public double maxForward() { return Math.max(small.forward(),Math.max(longBody.forward(),wide.forward()))*boost; }
        public double maxReverse() { return Math.max(small.reverse(),Math.max(longBody.reverse(),wide.reverse())); }
    }
    public static final Settings DEFAULTS=new Settings(new Speed(5.616/20,1.404/20),new Speed(4.68/20,1.17/20),new Speed(3.744/20,.936/20),
        1.8,.0117,1,.00975,.004875,.234/28,.234/7,1.3,new Band(30,1.1),new Band(60,.9),new Band(100,.6));
    private static volatile Settings settings=DEFAULTS;
    public static Settings get() { return settings; }
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue[] FORWARD=new ModConfigSpec.DoubleValue[3],REVERSE=new ModConfigSpec.DoubleValue[3];
    private static final ModConfigSpec.DoubleValue BOOST,PENALTY,FLOOR,ACCEL,REVERSE_ACCEL,COAST,BRAKE,UNLOADED;
    private static final ModConfigSpec.IntValue[] THRESHOLDS=new ModConfigSpec.IntValue[3];
    private static final ModConfigSpec.DoubleValue[] MULTIPLIERS=new ModConfigSpec.DoubleValue[3];
    static {
        var b=new ModConfigSpec.Builder();
        b.comment("Server-authoritative speed limits in blocks per second (20 game ticks per second).",
            "Ordinary forward and reverse limits are not reduced by cargo. Cargo reduces only the sprint bonus.").push("speed");
        String[] sizes={"small","long","wide"};Speed[] defaults={DEFAULTS.small(),DEFAULTS.longBody(),DEFAULTS.wide()};
        for(int i=0;i<3;i++) {
            b.push(sizes[i]);
            FORWARD[i]=b.comment("Ordinary maximum forward speed, in blocks per second.").defineInRange("forward",defaults[i].forward()*20,.2,10);
            REVERSE[i]=b.comment("Maximum reverse speed, in blocks per second. Sprint never increases reverse speed.").defineInRange("reverse",defaults[i].reverse()*20,.05,4);
            b.pop();
        }b.pop();
        b.comment("Sprint limit = max(base * minimumMultiplier, base * multiplier - occupiedSlots * penaltyPerOccupiedSlot).",
            "A straw mat occupies three slots; a stool or cargo block occupies one. Container contents and passengers add no slots.").push("boost");
        BOOST=b.comment("Empty-wagon sprint multiplier relative to this body's ordinary forward limit.").defineInRange("multiplier",1.8,1,2.5);
        PENALTY=b.comment("Absolute sprint speed penalty per occupied slot, in blocks per second; equal for every body size.",
            "Default 0.234 gives a full 12-slot long wagon a 1.2x sprint limit. Zero disables this penalty.").defineInRange("penaltyPerOccupiedSlot",.234,0,2.5);
        FLOOR=b.comment("Minimum sprint multiplier; cannot reduce speed below ordinary speed.",
            "Values above multiplier are effectively clamped to multiplier.").defineInRange("minimumMultiplier",1,1,2.5);b.pop();
        b.comment("Propulsion reference acceleration in blocks per second squared; shared by all body sizes.",
            "Each occupancy tier multiplies this reference directly, not the unloaded acceleration.",
            "Cargo does not weaken coasting deceleration or the reverse-key brake.").push("acceleration");
        ACCEL=b.comment("Forward reference acceleration before applying the occupancy multiplier.").defineInRange("forwardReference",3.9,.1,20);
        REVERSE_ACCEL=b.comment("Reverse reference acceleration before applying the same occupancy multiplier.").defineInRange("reverseReference",1.95,.1,10);
        UNLOADED=b.comment("Reference multiplier below the first occupancy threshold. Default is 1.3x the previous acceleration.").defineInRange("unloadedMultiplier",1.3,.05,3);
        COAST=b.comment("Deceleration when coasting or exceeding a reduced speed limit, in blocks per second squared.").defineInRange("coastingDeceleration",DEFAULTS.coast()*400,.2,40);
        BRAKE=b.comment("Deceleration when pressing the opposite direction, in blocks per second squared; stop before reversing.").defineInRange("brakingDeceleration",DEFAULTS.brake()*400,.5,80);
        String[] tiers={"lightLoad","mediumLoad","fullLoad"};Band[] bands={DEFAULTS.light(),DEFAULTS.medium(),DEFAULTS.full()};
        for(int i=0;i<3;i++) {
            b.comment("Tier starts at or above this occupied percentage, using this body's actual slot capacity.",
                "Thresholds are effectively clamped to nondecreasing order; the highest matching tier wins.",
                "Multipliers are effectively clamped to the previous tier so loading cannot increase acceleration.").push(tiers[i]);
            THRESHOLDS[i]=b.comment("Occupied percentage required for this tier, inclusive.").defineInRange("thresholdPercent",bands[i].percent(),1,100);
            MULTIPLIERS[i]=b.comment("Multiplier of forwardReference and reverseReference, not of unloaded acceleration.").defineInRange("multiplier",bands[i].multiplier(),.05,3);b.pop();
        }b.pop();SPEC=b.build();
    }
    private static Speed speed(int index) { return new Speed(FORWARD[index].get()/20,REVERSE[index].get()/20); }
    private static Band band(int index) { return new Band(THRESHOLDS[index].get(),MULTIPLIERS[index].get()); }
    public static void refresh(ModConfigEvent event) {
        if(event.getConfig().getSpec()!=SPEC)return;
        refresh();
    }
    static void refresh() {
        settings=new Settings(speed(0),speed(1),speed(2),BOOST.get(),PENALTY.get()/20,FLOOR.get(),
            ACCEL.get()/400,REVERSE_ACCEL.get()/400,COAST.get()/400,BRAKE.get()/400,UNLOADED.get(),band(0),band(1),band(2));
    }
    public static void unload(ModConfigEvent.Unloading event) { if(event.getConfig().getSpec()==SPEC)settings=DEFAULTS; }
    private DrivingConfig() {}
}
