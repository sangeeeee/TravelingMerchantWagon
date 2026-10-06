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
    @GameTest(template="assembly_test")
    public static void unified_startup_config_preserves_settings_and_migrates_legacy(GameTestHelper h) throws java.io.IOException {
        var root=java.nio.file.Files.createTempDirectory("tm-wagon-config-test-");
        var directory=root.resolve("config");var templates=root.resolve("defaultconfigs");
        StartupConfigFiles.ensure(directory,templates);
        var target=directory.resolve(ServerConfig.FILE_NAME);
        var parser=new com.electronwill.nightconfig.toml.TomlParser();
        var fresh=parser.parse(java.nio.file.Files.readString(target));
        h.assertTrue(ServerConfig.SPEC.isCorrect(fresh)&&fresh.contains("cargo.blacklist")&&fresh.contains("speed.small.forward"),"Fresh file does not contain both sections");
        close(h,fresh.<Number>get("draftTeam.accelerationMultiplier").doubleValue(),1.2,"Fresh config omitted team acceleration");
        close(h,fresh.<Number>get("draftTeam.cargoSpeedPenaltyMultiplier").doubleValue(),.85,"Fresh config omitted team load relief");
        try(var files=java.nio.file.Files.list(directory)) { h.assertTrue(files.count()==1,"Startup generated multiple files"); }
        java.nio.file.Files.writeString(target,"[cargo]\nlistMode = \"WHITELIST\"\nwhitelist = [\"minecraft:stone\"]\n[speed.small]\nforward = 7.0\n");
        var legacy=directory.resolve("tm_wagon-driving-server.toml");
        java.nio.file.Files.writeString(legacy,"[speed.small]\nforward = 6.0\nreverse = 2.0\n[acceleration]\nunloadedMultiplier = 1.6\n");
        StartupConfigFiles.ensure(directory,templates);
        var merged=parser.parse(java.nio.file.Files.readString(target));
        h.assertTrue("WHITELIST".equals(merged.get("cargo.listMode")),"Migration overwrote cargo rules");
        close(h,merged.<Number>get("speed.small.forward").doubleValue(),7,"Migration overwrote unified value");
        close(h,merged.<Number>get("speed.small.reverse").doubleValue(),2,"Legacy speed was lost");
        close(h,merged.<Number>get("acceleration.unloadedMultiplier").doubleValue(),1.6,"Legacy acceleration was lost");
        h.assertTrue(!java.nio.file.Files.exists(legacy),"Legacy file remained active");
        String before=java.nio.file.Files.readString(target);StartupConfigFiles.ensure(directory,templates);
        h.assertTrue(before.equals(java.nio.file.Files.readString(target)),"Repeated startup changed user settings");
        java.nio.file.Files.createDirectories(templates);
        java.nio.file.Files.writeString(templates.resolve(ServerConfig.FILE_NAME),"[speed.long]\nforward = 6.5\n");
        var templated=root.resolve("templated");StartupConfigFiles.ensure(templated,templates);
        var copied=parser.parse(java.nio.file.Files.readString(templated.resolve(ServerConfig.FILE_NAME)));
        close(h,copied.<Number>get("speed.long.forward").doubleValue(),6.5,"Default template ignored");h.succeed();
    }
    private static void close(GameTestHelper h,double actual,double expected,String message) {
        h.assertTrue(Math.abs(actual-expected)<1e-9,message+": "+actual+" != "+expected);
    }
    @GameTest(template="assembly_test")
    public static void cargo_acceleration_boundaries_use_each_bodys_capacity(GameTestHelper h) {
        h.assertTrue(ServerConfig.SPEC.isLoaded(),"Server driving config was not loaded");
        var bodies=new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY};
        int[][] boundaries={{3,6,10},{4,8,12},{10,20,32}};
        for(int i=0;i<bodies.length;i++)for(int used=0;used<=bodies[i].cargoCapacity();used++) {
            double multiplier=used>=boundaries[i][2]?.6:used>=boundaries[i][1]?.9:used>=boundaries[i][0]?1.1:1.3;
            var drive=new WagonDrive();int capacity=bodies[i].cargoCapacity();
            close(h,drive.tick(1,WagonSpeed.forward(bodies[i],used,false,false),used,capacity,false),.00975*multiplier,"Forward tier for "+bodies[i]+" / "+used);
            drive.reset();
            close(h,drive.tick(-1,WagonSpeed.reverse(bodies[i]),used,capacity,false),-.004875*multiplier,"Reverse tier for "+bodies[i]+" / "+used);
        }
        h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void loading_changes_acceleration_without_weakening_brakes(GameTestHelper h) {
        var drive=new WagonDrive();drive.tick(1,.234,0,12,false);double previous=drive.speed();
        drive.tick(1,.234,12,12,false);close(h,drive.speed()-previous,.00585,"Full load did not apply immediately");
        previous=drive.speed();drive.tick(1,.234,0,12,false);close(h,drive.speed()-previous,.012675,"Unloading did not restore acceleration immediately");
        drive.load(.234);double emptyBrake=drive.tick(-1,.0585,0,12,false);
        drive.load(.234);close(h,drive.tick(-1,.0585,12,12,false),emptyBrake,"Cargo weakened braking");
        drive.load(.234);double emptyCoast=drive.tick(0,.234,0,12,false);
        drive.load(.234);close(h,drive.tick(0,.234,12,12,false),emptyCoast,"Cargo weakened coasting deceleration");h.succeed();
    }
    @SuppressWarnings("unchecked")
    private static ModConfigSpec.ConfigValue<Object> value(String path) { return ServerConfig.SPEC.getValues().get(path); }
    @GameTest(template="assembly_test")
    public static void server_config_reload_drives_limits_acceleration_and_range_validation(GameTestHelper h) {
        var original=new LinkedHashMap<String,Object>();
        Object[][] changes={
            {"speed.small.forward",6.0},{"speed.small.reverse",2.0},
            {"speed.long.forward",5.0},{"speed.long.reverse",1.5},
            {"speed.wide.forward",4.0},{"speed.wide.reverse",1.0},
            {"boost.multiplier",2.0},{"boost.penaltyPerOccupiedSlot",.1},{"boost.minimumMultiplier",1.1},
            {"draftTeam.accelerationMultiplier",1.35},{"draftTeam.cargoSpeedPenaltyMultiplier",.7},
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
                close(h,WagonSpeed.forward(bodies[i],0,false,false),(6-i)/20.0,"Configured forward speed");
                close(h,WagonSpeed.reverse(bodies[i]),(2-i*.5)/20,"Configured reverse speed");
                close(h,WagonSpeed.forward(bodies[i],3,true,false),(6-i)/10.0-.015,"Configured absolute slot penalty");
                close(h,WagonSpeed.forward(bodies[i],3,true,true),(6-i)/10.0-.0105,"Configured team slot penalty");
                close(h,WagonSpeed.forward(bodies[i],100,true,false),(6-i)/20.0*1.1,"Configured boost floor");
                close(h,WagonSpeed.forward(bodies[i],100,true,true),(6-i)/20.0*1.1,"Configured team boost floor");
            }
            var drive=new WagonDrive();close(h,drive.tick(1,.3,0,12,false),.015,"Configured empty acceleration");
            drive.reset();close(h,drive.tick(1,.3,3,12,false),.012,"Configured first tier");
            drive.reset();close(h,drive.tick(-1,.1,6,12,false),-.004,"Configured reverse tier");
            drive.reset();close(h,drive.tick(1,.3,11,12,false),.004,"Configured final threshold");
            drive.reset();close(h,drive.tick(1,.3,0,12,true),.015*1.35,"Configured team forward acceleration");
            drive.reset();close(h,drive.tick(-1,.1,6,12,true),-.004*1.35,"Configured team reverse acceleration");
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
    public static void double_team_acceleration_scales_every_load_tier_but_not_brakes(GameTestHelper h) {
        for(var body:new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY}) {
            for(int occupied=0;occupied<=body.cargoCapacity();occupied++) {
                for(int input:new int[]{1,-1}) {
                    var single=new WagonDrive();var pair=new WagonDrive();
                    double a=single.tick(input,.3,occupied,body.cargoCapacity(),false);
                    double b=pair.tick(input,.3,occupied,body.cargoCapacity(),true);
                    close(h,b,a*1.2,"Team acceleration at "+body+" / "+occupied+" / "+input);
                }
            }
        }
        var drive=new WagonDrive();
        drive.load(.2);double brake=drive.tick(-1,.1,12,12,false);
        drive.load(.2);close(h,drive.tick(-1,.1,12,12,true),brake,"Team altered braking");
        drive.load(.2);double coast=drive.tick(0,.3,12,12,false);
        drive.load(.2);close(h,drive.tick(0,.3,12,12,true),coast,"Team altered coasting");
        drive.load(.3);double overspeed=drive.tick(1,.2,12,12,false);
        drive.load(.3);close(h,drive.tick(1,.2,12,12,true),overspeed,"Team altered reduced-limit deceleration");
        drive.reset();drive.tick(1,.3,0,12,true);double before=drive.speed();
        drive.tick(1,.3,0,12,false);close(h,drive.speed()-before,.012675,"Team state change did not affect the next propulsion tick");
        h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void conflicting_tiers_are_ordered_and_cannot_accelerate_under_load(GameTestHelper h) {
        var d=DrivingConfig.DEFAULTS;
        var s=new com.sange.tm_wagon.core.DrivingParameters.Settings(d.small(),d.longBody(),d.wide(),1.2,d.slotPenalty(),2,d.doubleTeam(),
            d.forwardAcceleration(),d.reverseAcceleration(),d.coast(),d.brake(),1.3,
            new com.sange.tm_wagon.core.DrivingParameters.Band(50,2),new com.sange.tm_wagon.core.DrivingParameters.Band(20,1.1),new com.sange.tm_wagon.core.DrivingParameters.Band(10,1.2));
        close(h,s.boostFloor(),1.2,"Boost floor exceeded empty cap");
        close(h,s.accelerationMultiplier(4,10),1.3,"Unloaded tier changed");
        close(h,s.accelerationMultiplier(5,10),1.1,"Highest matching tier did not win");
        h.assertTrue(s.medium().percent()==50&&s.full().percent()==50,"Tier thresholds not ordered");h.succeed();
    }
}
