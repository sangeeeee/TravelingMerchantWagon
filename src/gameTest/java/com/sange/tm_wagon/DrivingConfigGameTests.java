package com.sange.tm_wagon;

import com.sange.tm_wagon.assembly.WagonPart;
import com.sange.tm_wagon.physics.*;
import java.util.LinkedHashMap;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class DrivingConfigGameTests {
    private static void close(GameTestHelper h,double actual,double expected,String message) {
        h.assertTrue(Math.abs(actual-expected)<1e-9,message+": "+actual+" != "+expected);
    }
    @GameTest(template="assembly_test")
    public static void cargo_acceleration_boundaries_use_each_bodys_capacity(GameTestHelper h) {
        h.assertTrue(DrivingConfig.SPEC.isLoaded(),"Server driving config was not loaded");
        var bodies=new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY};
        int[][] boundaries={{3,6,10},{4,8,12},{10,20,32}};
        for(int i=0;i<bodies.length;i++)for(int used=0;used<=bodies[i].cargoCapacity();used++) {
            double multiplier=used>=boundaries[i][2]?.6:used>=boundaries[i][1]?.9:used>=boundaries[i][0]?1.1:1.3;
            var drive=new WagonDrive();int capacity=bodies[i].cargoCapacity();
            close(h,drive.tick(1,WagonSpeed.forward(bodies[i],used,false),used,capacity),.00975*multiplier,"Forward tier for "+bodies[i]+" / "+used);
            drive.reset();
            close(h,drive.tick(-1,WagonSpeed.reverse(bodies[i]),used,capacity),-.004875*multiplier,"Reverse tier for "+bodies[i]+" / "+used);
        }
        h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void loading_changes_acceleration_without_weakening_brakes(GameTestHelper h) {
        var drive=new WagonDrive();drive.tick(1,.234,0,12);double previous=drive.speed();
        drive.tick(1,.234,12,12);close(h,drive.speed()-previous,.00585,"Full load did not apply immediately");
        previous=drive.speed();drive.tick(1,.234,0,12);close(h,drive.speed()-previous,.012675,"Unloading did not restore acceleration immediately");
        drive.load(.234);double emptyBrake=drive.tick(-1,.0585,0,12);
        drive.load(.234);close(h,drive.tick(-1,.0585,12,12),emptyBrake,"Cargo weakened braking");
        drive.load(.234);double emptyCoast=drive.tick(0,.234,0,12);
        drive.load(.234);close(h,drive.tick(0,.234,12,12),emptyCoast,"Cargo weakened coasting deceleration");h.succeed();
    }
    @SuppressWarnings("unchecked")
    private static ModConfigSpec.ConfigValue<Object> value(String path) { return DrivingConfig.SPEC.getValues().get(path); }
    @GameTest(template="assembly_test")
    public static void server_config_reload_drives_limits_acceleration_and_range_validation(GameTestHelper h) {
        var original=new LinkedHashMap<String,Object>();
        Object[][] changes={
            {"speed.small.forward",6.0},{"speed.small.reverse",2.0},
            {"speed.long.forward",5.0},{"speed.long.reverse",1.5},
            {"speed.wide.forward",4.0},{"speed.wide.reverse",1.0},
            {"boost.multiplier",2.0},{"boost.penaltyPerOccupiedSlot",.1},{"boost.minimumMultiplier",1.1},
            {"acceleration.forwardReference",4.0},{"acceleration.reverseReference",2.0},
            {"acceleration.unloadedMultiplier",1.5},{"acceleration.coastingDeceleration",4.0},{"acceleration.brakingDeceleration",12.0},
            {"acceleration.lightLoad.thresholdPercent",25},{"acceleration.lightLoad.multiplier",1.2},
            {"acceleration.mediumLoad.thresholdPercent",50},{"acceleration.mediumLoad.multiplier",.8},
            {"acceleration.fullLoad.thresholdPercent",90},{"acceleration.fullLoad.multiplier",.4}
        };
        try {
            for(var change:changes) {
                String path=(String)change[0];var entry=value(path);original.put(path,entry.get());
                h.assertTrue(entry.getSpec().test(change[1])&&!entry.getSpec().test(-1)&&!entry.getSpec().test(1e9),"Config range missing: "+path);
                entry.set(change[1]);
            }
            DrivingConfig.refresh();
            var bodies=new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY};
            for(int i=0;i<3;i++) {
                close(h,WagonSpeed.forward(bodies[i],0,false),(6-i)/20.0,"Configured forward speed");
                close(h,WagonSpeed.reverse(bodies[i]),(2-i*.5)/20,"Configured reverse speed");
                close(h,WagonSpeed.forward(bodies[i],3,true),(6-i)/10.0-.015,"Configured absolute slot penalty");
                close(h,WagonSpeed.forward(bodies[i],100,true),(6-i)/20.0*1.1,"Configured boost floor");
            }
            var drive=new WagonDrive();close(h,drive.tick(1,.3,0,12),.015,"Configured empty acceleration");
            drive.reset();close(h,drive.tick(1,.3,3,12),.012,"Configured first tier");
            drive.reset();close(h,drive.tick(-1,.1,6,12),-.004,"Configured reverse tier");
            drive.reset();close(h,drive.tick(1,.3,11,12),.004,"Configured final threshold");
            drive.load(.2);close(h,drive.tick(0,.3),.19,"Configured coasting");
            drive.load(.2);close(h,drive.tick(-1,.1),.17,"Configured braking");
            drive.load(100);close(h,drive.speed(),.6,"Configured saved forward cap");
            drive.load(-100);close(h,drive.speed(),-.1,"Configured saved reverse cap");
        } finally {
            // Synchronous server-thread test: restore in-memory values before other tests or ticks run; never save to disk.
            original.forEach((path,old)->value(path).set(old));DrivingConfig.refresh();
        }
        h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void conflicting_tiers_are_ordered_and_cannot_accelerate_under_load(GameTestHelper h) {
        var d=DrivingConfig.DEFAULTS;
        var s=new DrivingConfig.Settings(d.small(),d.longBody(),d.wide(),1.2,d.slotPenalty(),2,
            d.forwardAcceleration(),d.reverseAcceleration(),d.coast(),d.brake(),1.3,
            new DrivingConfig.Band(50,2),new DrivingConfig.Band(20,1.1),new DrivingConfig.Band(10,1.2));
        close(h,s.boostFloor(),1.2,"Boost floor exceeded empty cap");
        close(h,s.accelerationMultiplier(4,10),1.3,"Unloaded tier changed");
        close(h,s.accelerationMultiplier(5,10),1.1,"Highest matching tier did not win");
        h.assertTrue(s.medium().percent()==50&&s.full().percent()==50,"Tier thresholds not ordered");h.succeed();
    }
}
