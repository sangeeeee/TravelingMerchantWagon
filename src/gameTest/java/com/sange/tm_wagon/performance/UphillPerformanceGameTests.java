package com.sange.tm_wagon.performance;

import com.mojang.logging.LogUtils;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.gametest.*;

/** Continuous ascents in real server ticks. Fresh vehicles per traversal avoid contact-history
 * contamination from teleports. Geometry setup and CSV formatting are outside timed regions. */
@GameTestHolder("tm_wagon_uphill")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="tm_wagon")
public final class UphillPerformanceGameTests {
    private record Scenario(String name, boolean wide, int rises, int spacing, int angle) {}
    private static final List<Scenario> CASES=new ArrayList<>();
    static {
        for(boolean wide:new boolean[]{false,true})for(int angle:new int[]{0,15,45}) {
            String prefix=(wide?"wide_loaded":"small_empty")+"_"+angle;
            CASES.add(new Scenario(prefix+"_flat",wide,0,1,angle));
            CASES.add(new Scenario(prefix+"_step",wide,1,1,angle));
            CASES.add(new Scenario(prefix+"_stairs2",wide,3,2,angle));
            CASES.add(new Scenario(prefix+"_stairs1",wide,3,1,angle));
            CASES.add(new Scenario(prefix+"_hill8",wide,8,1,angle));
        }
        CASES.add(new Scenario("small_empty_0_stairs1_boost",false,3,1,0));
        CASES.add(new Scenario("small_empty_15_stairs1_boost",false,3,1,15));
        CASES.add(new Scenario("small_empty_45_stairs1_boost",false,3,1,45));
        CASES.add(new Scenario("small_empty_0_stairs1_boost_pair",false,3,1,0));
        CASES.add(new Scenario("small_empty_15_stairs1_boost_pair",false,3,1,15));
        CASES.add(new Scenario("long_empty_15_hill8_pair",false,8,1,15));
        CASES.add(new Scenario("long_empty_45_stairs1_boost_pair",false,3,1,45));
        CASES.add(new Scenario("wide_loaded_15_hill8_pair",true,8,1,15));
        CASES.add(new Scenario("wide_loaded_45_stairs1_pair",true,3,1,45));
        CASES.add(new Scenario("wide_loaded_45_stairs1_offset",true,3,1,45));
    }
    private static final int TRAVERSAL=180,REPEATS=4;
    private static final boolean DIAGNOSTICS=Boolean.getBoolean("tm_wagon.uphillDiagnostics");
    private static final com.sun.management.ThreadMXBean MEMORY=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static UphillPerformanceGameTests active;
    private GameTestHelper helper;
    private ProbeWagon wagon;
    private Player driver;
    private final List<AbstractHorse> horses=new ArrayList<>();
    private int scenario=-1,age;
    private long start,bytes,tickStart,tickBytes;
    private Vec3 origin,before;
    private double entityMs,entityKiB;
    private PerformanceGameTests.Phase phase;
    private final List<String> rows=new ArrayList<>();
    private static long allocated() { return MEMORY.getThreadAllocatedBytes(Thread.currentThread().threadId()); }
    private static final class ProbeWagon extends WagonEntity {
        int poses,colliderBuilds; boolean horseBlocked; double requestedSpeed;
        private final List<List<OrientedBox>> acceptedBoxes=new ArrayList<>();
        ProbeWagon(Level level) { super(WagonContent.WAGON.get(),level); }
        @Override public void applyPose(WagonPose pose) {
            poses++;
            if(DIAGNOSTICS&&acceptedBoxes!=null)acceptedBoxes.add(super.motionCollidersAt(pose));
            super.applyPose(pose);
        }
        @Override public List<OrientedBox> motionCollidersAt(WagonPose pose) {
            colliderBuilds++;return super.motionCollidersAt(pose);
        }
        @Override public boolean horsesCanAdvance(Vec3 delta) {
            requestedSpeed=delta.horizontalDistance();boolean allowed=super.horsesCanAdvance(delta);horseBlocked=!allowed;return allowed;
        }
        void resetProbe() {
            poses=colliderBuilds=0;horseBlocked=false;requestedSpeed=Double.NaN;
            acceptedBoxes.clear();
        }
        /** Expensive diagnostic queries run only in a separate non-benchmark launch. */
        void diagnose(String scenario,int tick) {
            try {
                var field=WagonEntity.class.getDeclaredField("components");field.setAccessible(true);
                var labels=new ArrayList<String>();
                for(Object component:(List<?>)field.get(this)) {
                    var method=component.getClass().getDeclaredMethod("slot");method.setAccessible(true);
                    var slot=(WagonSlot)method.invoke(component);
                    if(slot==WagonSlot.FRONT_LEFT||slot==WagonSlot.FRONT_RIGHT||slot==WagonSlot.REAR_LEFT||slot==WagonSlot.REAR_RIGHT)continue;
                    labels.add(component.toString());
                }
                var hits=new LinkedHashSet<String>();Vec3 ahead=new WagonPose(position(),getYRot(),0,0).forward().scale(.02);
                for(var boxes:acceptedBoxes)for(int i=0;i<boxes.size();i++) {
                    var box=boxes.get(i);
                    for(var shape:level().getBlockCollisions(this,box.bounds().expandTowards(ahead).inflate(.001)))for(var block:shape.toAabbs()) {
                        if(box.intersects(block))continue;
                        var hit=OrientedBox.of(block).sweep(box,ahead);
                        if(hit!=null&&hit.time()<.05)hits.add((i<labels.size()?labels.get(i):"passenger")+"; terrain relative="+block.move(active.origin.scale(-1)));
                    }
                }
                LogUtils.getLogger().info("UPHILL_CONTACT {} tick={} hits={}",scenario,tick,hits);
            }catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
    }
    @GameTest(template="assembly_test",timeoutTicks=32000)
    public static void continuous_uphill_profile(GameTestHelper h) {
        if(!Boolean.getBoolean("tm_wagon.uphill")) { h.succeed();return; }
        active=new UphillPerformanceGameTests();active.helper=h;
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,h.getLevel().getServer());
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(false,h.getLevel().getServer());
        h.getLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,h.getLevel().getServer());
        var low=h.absolutePos(new BlockPos(-16,7,-96));var high=h.absolutePos(new BlockPos(96,7,24));
        for(int x=low.getX()>>4;x<=high.getX()>>4;x++)for(int z=low.getZ()>>4;z<=high.getZ()>>4;z++) {
            h.getLevel().getChunk(x,z);h.getLevel().setChunkForced(x,z,true);
        }
        for(int x=-16;x<=96;x++)for(int z=-96;z<=24;z++)for(int y=7;y<=20;y++)
            h.setBlock(new BlockPos(x,y,z),y==7?Blocks.STONE:Blocks.AIR);
        active.rows.add("scenario,run,tick,x,y,z,speed,requested_speed,pitch_deg,roll_deg,horse_blocked,falling,substeps,collider_builds,wagon_ms,wagon_KiB,server_ms,server_KiB");
        active.next();
    }
    private void cleanup() {
        if(wagon!=null) { for(var horse:horses)wagon.detachHorse(horse.getUUID(),false);driver.stopRiding();wagon.discard(); }
        for(var horse:horses)horse.discard();horses.clear();wagon=null;
    }
    private void next() {
        cleanup();scenario++;age=0;
        String filter=System.getProperty("tm_wagon.uphillCases","");
        while(scenario<CASES.size()&&!filter.isEmpty()&&!List.of(filter.split(",")).contains(CASES.get(scenario).name))scenario++;
        if(scenario==CASES.size()) {
            try { Files.write(Path.of("uphill-trace.csv"),rows); }catch(java.io.IOException e) { throw new IllegalStateException(e); }
            LogUtils.getLogger().info("UPHILL_COMPLETE {} samples",rows.size()-1);active=null;helper.succeed();return;
        }
        var c=CASES.get(scenario);
        for(int x=-16;x<=96;x++)for(int z=-96;z<=0;z++)for(int y=8;y<=15;y++) {
            int height=c.rises==0?0:Math.min(c.rises,1+(-z)/c.spacing);
            helper.setBlock(new BlockPos(x,y,z),y<8+height?Blocks.STONE:Blocks.AIR);
        }
        LogUtils.getLogger().info("UPHILL_BEGIN {}",c.name);
        spawn();
    }
    private void spawn() {
        cleanup();var c=CASES.get(scenario);
        origin=Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0,8,c.name.endsWith("_offset")?11:12)));
        wagon=new ProbeWagon(helper.getLevel());var parts=WagonEntity.defaultParts();
        if(c.wide)parts.put(WagonSlot.BODY,WagonPart.WIDE_CARGO_BODY);
        if(c.name.startsWith("long_"))parts.put(WagonSlot.BODY,WagonPart.LONG_CARGO_BODY);
        if(c.name.endsWith("_pair"))parts.put(WagonSlot.SHAFTS,WagonPart.DOUBLE_HORSE_SHAFTS);
        wagon.configure(parts,Direction.NORTH);wagon.applyPose(new WagonPose(origin,180+c.angle,0,0));
        driver=helper.makeMockPlayer(GameType.CREATIVE);driver.setPos(origin.add(0,0,5));
        if(c.wide)for(int i=0;i<wagon.cargo().capacity();i++) {
            String error=wagon.cargo().place(i,new ItemStack(Items.STONE),driver);
            if(error!=null)throw new IllegalStateException(error);
        }
        helper.getLevel().addFreshEntity(wagon);
        if(!driver.startRiding(wagon))throw new IllegalStateException("driver fixture failed");wagon.positionRider(driver);
        for(int i=0;i<wagon.horseCapacity();i++) {
            var horse=EntityType.HORSE.create(helper.getLevel());horse.setNoAi(true);horse.setPos(wagon.horsePosition(i));
            helper.getLevel().addFreshEntity(horse);horse.setLeashedTo(driver,true);horses.add(horse);
            String error=wagon.attachHorse(driver,horse,i);if(error!=null)throw new IllegalStateException(error);
        }
    }
    @SubscribeEvent public static void pre(ServerTickEvent.Pre event) {
        if(active==null)return;var a=active;
        if(a.age==TRAVERSAL*REPEATS) {
            a.phase.end();a.phase.commit();a.next();if(active==null)return;
        } else if(a.age>0&&a.age%TRAVERSAL==0)a.spawn();
        if(a.age==TRAVERSAL) {
            a.phase=new PerformanceGameTests.Phase();a.phase.scenario=CASES.get(a.scenario).name;
            a.phase.sable=com.sange.tm_wagon.compat.StructureCollision.available();a.phase.begin();
        }
        a.wagon.resetProbe();a.before=a.wagon.position();a.entityMs=Double.NaN;
        a.wagon.acceptInput(a.driver,1,0,CASES.get(a.scenario).name.contains("_boost"));
        a.tickBytes=allocated();a.tickStart=System.nanoTime();
    }
    @SubscribeEvent public static void beforeWagon(EntityTickEvent.Pre event) {
        if(active==null||event.getEntity()!=active.wagon)return;
        active.bytes=allocated();active.start=System.nanoTime();
    }
    @SubscribeEvent public static void afterWagon(EntityTickEvent.Post event) {
        if(active==null||event.getEntity()!=active.wagon)return;
        active.entityMs=(System.nanoTime()-active.start)/1e6;active.entityKiB=(allocated()-active.bytes)/1024.0;
    }
    @SubscribeEvent public static void post(ServerTickEvent.Post event) {
        if(active==null||active.tickStart==0)return;var a=active;
        double ms=(System.nanoTime()-a.tickStart)/1e6,kib=(allocated()-a.tickBytes)/1024.0;
        if(a.age>=TRAVERSAL) {
            if(!Double.isFinite(a.entityMs))throw new IllegalStateException("wagon did not tick");
            Vec3 p=a.wagon.position().subtract(a.origin),d=a.wagon.position().subtract(a.before);
            a.rows.add(String.format(Locale.ROOT,"%s,%d,%d,%.7f,%.7f,%.7f,%.7f,%.7f,%.5f,%.5f,%s,%s,%d,%d,%.6f,%.3f,%.6f,%.3f",
                CASES.get(a.scenario).name,a.age/TRAVERSAL,a.age%TRAVERSAL,p.x,p.y,p.z,d.horizontalDistance(),a.wagon.requestedSpeed,
                Math.toDegrees(a.wagon.pitch()),Math.toDegrees(a.wagon.roll()),a.wagon.horseBlocked,a.wagon.falling(),a.wagon.poses,a.wagon.colliderBuilds,a.entityMs,a.entityKiB,ms,kib));
            if(DIAGNOSTICS&&a.age<TRAVERSAL*2&&d.horizontalDistance()<a.wagon.requestedSpeed*.9)
                a.wagon.diagnose(CASES.get(a.scenario).name,a.age%TRAVERSAL);
        }
        a.age++;
    }
}
