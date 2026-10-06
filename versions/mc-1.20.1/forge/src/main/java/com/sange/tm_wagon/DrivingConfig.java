package com.sange.tm_wagon;

import com.sange.tm_wagon.core.DrivingParameters;
import com.sange.tm_wagon.core.DrivingParameters.*;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.common.ForgeConfigSpec;

/** Server-authoritative, immutable snapshots; convert user-facing seconds to ticks once on reload. */
public final class DrivingConfig {
    public static final Settings DEFAULTS=DrivingParameters.DEFAULTS;
    private static volatile Settings settings=DEFAULTS;
    public static Settings get() { return settings; }
    private static final ForgeConfigSpec.DoubleValue[] FORWARD=new ForgeConfigSpec.DoubleValue[3],REVERSE=new ForgeConfigSpec.DoubleValue[3];
    private static ForgeConfigSpec.DoubleValue BOOST,PENALTY,FLOOR,ACCEL,REVERSE_ACCEL,COAST,BRAKE,UNLOADED;
    private static ForgeConfigSpec.DoubleValue TEAM_ACCEL,TEAM_PENALTY;
    private static final ForgeConfigSpec.IntValue[] THRESHOLDS=new ForgeConfigSpec.IntValue[3];
    private static final ForgeConfigSpec.DoubleValue[] MULTIPLIERS=new ForgeConfigSpec.DoubleValue[3];
    static void define(ForgeConfigSpec.Builder b) {
        b.comment("Server-authoritative speed limits in blocks per second (20 game ticks per second).",
            "Ordinary forward and reverse limits are not reduced by cargo. Cargo reduces only the sprint bonus.").push("speed");
        String[] sizes={"small","long","wide"};Speed[] defaults={DEFAULTS.small(),DEFAULTS.longBody(),DEFAULTS.wide()};
        for(int i=0;i<3;i++) {
            b.push(sizes[i]);
            FORWARD[i]=b.comment("Ordinary maximum forward speed, in blocks per second.").defineInRange("forward",defaults[i].forward()*20,.2,10);
            REVERSE[i]=b.comment("Maximum reverse speed, in blocks per second. Sprint never increases reverse speed.").defineInRange("reverse",defaults[i].reverse()*20,.05,4);
            b.pop();
        }b.pop();
        b.comment("Sprint limit = max(base * minimumMultiplier, base * multiplier - occupiedSlots * penaltyPerOccupiedSlot * teamPenalty).",
            "teamPenalty is draftTeam.cargoSpeedPenaltyMultiplier with two attached draft animals, otherwise 1.",
            "A straw mat occupies three slots; a sleeping bag occupies two; a stool or cargo block occupies one.",
            "Container contents and passengers add no slots.").push("boost");
        BOOST=b.comment("Empty-wagon sprint multiplier relative to this body's ordinary forward limit.").defineInRange("multiplier",1.8,1,2.5);
        PENALTY=b.comment("Absolute sprint speed penalty per occupied slot, in blocks per second; equal for every body size.",
            "With one draft animal, default 0.234 gives a full 12-slot long wagon a 1.2x sprint limit.",
            "Two attached draft animals scale this deduction by draftTeam.cargoSpeedPenaltyMultiplier. Zero disables the penalty.").defineInRange("penaltyPerOccupiedSlot",.234,0,2.5);
        FLOOR=b.comment("Minimum sprint multiplier; cannot reduce speed below ordinary speed.",
            "Values above multiplier are effectively clamped to multiplier.").defineInRange("minimumMultiplier",1,1,2.5);b.pop();
        b.comment("Benefits for a double-horse hitch with both living draft animals actually attached.",
            "The shafts alone grant no bonus. Cargo changes and horse detachment take effect on the next driving tick.",
            "Ordinary speeds, empty-wagon sprint limits, braking, coasting and manual pushing remain unchanged.").push("draftTeam");
        TEAM_ACCEL=b.comment("Multiplier of forward and reverse propulsion acceleration after applying the cargo occupancy tier.",
            "Default 1.2 gives a two-animal team 20 percent more acceleration. Set to 1 to disable.")
            .defineInRange("accelerationMultiplier",DEFAULTS.doubleTeam().accelerationMultiplier(),1,2);
        TEAM_PENALTY=b.comment("Multiplier of the per-occupied-slot sprint speed deduction for a two-animal team.",
            "Default 0.85 reduces the deduction by 15 percent, rather than increasing the total speed by 15 percent.",
            "The ordinary-speed floor still applies. Set to 1 to disable the reduction.")
            .defineInRange("cargoSpeedPenaltyMultiplier",DEFAULTS.doubleTeam().cargoSpeedPenaltyMultiplier(),.5,1);b.pop();
        b.comment("Propulsion reference acceleration in blocks per second squared; shared by all body sizes.",
            "Each occupancy tier multiplies this reference directly, not the unloaded acceleration.",
            "Cargo does not weaken coasting deceleration or the reverse-key brake.").push("acceleration");
        ACCEL=b.comment("Forward reference acceleration before applying the occupancy multiplier.").defineInRange("forwardReference",3.9,.1,20);
        REVERSE_ACCEL=b.comment("Reverse reference acceleration before applying the same occupancy multiplier.").defineInRange("reverseReference",1.95,.1,10);
        UNLOADED=b.comment("Reference multiplier below the first cargo occupancy threshold.").defineInRange("unloadedMultiplier",1.3,.05,3);
        COAST=b.comment("Deceleration when coasting or exceeding a reduced speed limit, in blocks per second squared.").defineInRange("coastingDeceleration",DEFAULTS.coast()*400,.2,40);
        BRAKE=b.comment("Deceleration when pressing the opposite direction, in blocks per second squared; stop before reversing.").defineInRange("brakingDeceleration",DEFAULTS.brake()*400,.5,80);
        String[] tiers={"lightLoad","mediumLoad","fullLoad"};Band[] bands={DEFAULTS.light(),DEFAULTS.medium(),DEFAULTS.full()};
        for(int i=0;i<3;i++) {
            b.comment("Tier starts at or above this occupied percentage, using this body's actual slot capacity.",
                "Thresholds are effectively clamped to nondecreasing order; the highest matching tier wins.",
                "Multipliers are effectively clamped to the previous tier so loading cannot increase acceleration.").push(tiers[i]);
            THRESHOLDS[i]=b.comment("Occupied percentage required for this tier, inclusive.").defineInRange("thresholdPercent",bands[i].percent(),1,100);
            MULTIPLIERS[i]=b.comment("Multiplier of forwardReference and reverseReference, not of unloaded acceleration.").defineInRange("multiplier",bands[i].multiplier(),.05,3);b.pop();
        }b.pop();
    }
    private static Speed speed(int index) { return new Speed(FORWARD[index].get()/20,REVERSE[index].get()/20); }
    private static Band band(int index) { return new Band(THRESHOLDS[index].get(),MULTIPLIERS[index].get()); }
    public static void refresh(ModConfigEvent event) {
        if(event.getConfig().getSpec()!=ServerConfig.SPEC)return;
        refresh();
    }
    static void refresh() {
        settings=new Settings(speed(0),speed(1),speed(2),BOOST.get(),PENALTY.get()/20,FLOOR.get(),
            new DraftTeam(TEAM_ACCEL.get(),TEAM_PENALTY.get()),
            ACCEL.get()/400,REVERSE_ACCEL.get()/400,COAST.get()/400,BRAKE.get()/400,UNLOADED.get(),band(0),band(1),band(2));
    }
    public static void unload(ModConfigEvent.Unloading event) { if(event.getConfig().getSpec()==ServerConfig.SPEC)settings=DEFAULTS; }
    private DrivingConfig() {}
}
