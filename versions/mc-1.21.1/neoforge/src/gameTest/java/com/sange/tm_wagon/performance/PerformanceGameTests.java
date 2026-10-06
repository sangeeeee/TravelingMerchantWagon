package com.sange.tm_wagon.performance;

import com.mojang.logging.LogUtils;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.cargo.*;
import com.sange.tm_wagon.compat.StructureCollision;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.WagonPose;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.gametest.*;

/** Real server ticks, one sequential scenario at a time. No performance thresholds in CI.
 * Fixture resets, warmup and result serialization are outside measured entity ticks.
 * This entire source set is excluded from the distributable mod JAR. */
@GameTestHolder("tm_wagon_perf")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="tm_wagon")
public final class PerformanceGameTests {
    private record Case(String name,int wagons,boolean moving,boolean wide,String cargo,int mobs,float yaw,boolean structure,boolean movingStructure) {}
    private static final List<Case> CASES=List.of(
        new Case("baseline",0,false,false,"",0,0,false,false),
        new Case("idle_empty",1,false,false,"",0,0,false,false),
        new Case("moving_empty",1,true,false,"",0,0,false,false),
        new Case("moving_empty_45",1,true,false,"",0,45,false,false),
        new Case("wide_empty_45",1,true,true,"",0,45,false,false),
        new Case("wide_stone32_45",1,true,true,"stone",0,45,false,false),
        new Case("wide_chest32_45",1,true,true,"chest",0,45,false,false),
        new Case("wide_canopy_stone32_45",1,true,true,"canopy",0,45,false,false),
        new Case("wide_furnaces32_45",1,true,true,"furnace",0,45,false,false),
        new Case("wide_riders16_45",1,true,true,"riders",0,45,false,false),
        new Case("wide_sleepers8_45",1,true,true,"sleepers",0,45,false,false),
        new Case("wide_maid_sleepers8_45",1,true,true,"maids",0,45,false,false),
        new Case("crowd40_control",0,false,false,"",40,0,false,false),
        new Case("crowd120_control",0,false,false,"",120,0,false,false),
        new Case("moving_crowd40",1,true,false,"",40,0,false,false),
        new Case("moving_crowd120",1,true,false,"",120,0,false,false),
        new Case("moving_crowd120_45",1,true,false,"",120,45,false,false),
        new Case("moving_crowd120_turn",1,true,false,"",120,0,false,false),
        new Case("wide_canopy_crowd120_turn",1,true,true,"canopy",120,0,false,false),
        new Case("wide_stone32_crowd120_45",1,true,true,"stone",120,45,false,false),
        new Case("wide_canopy_crowd120_45",1,true,true,"canopy",120,45,false,false),
        new Case("moving_crowd120_corridor",1,true,false,"",120,0,false,false),
        new Case("crowd120_ai_control",0,false,false,"",120,0,false,false),
        new Case("moving_crowd120_ai",1,true,false,"",120,0,false,false),
        new Case("crowd320_control",0,false,false,"",320,0,false,false),
        new Case("moving_crowd320",1,true,false,"",320,0,false,false),
        new Case("moving_empty8",8,true,false,"",0,45,false,false),
        new Case("idle_empty8",8,false,false,"",0,45,false,false),
        new Case("falling_wide_stone32",1,false,true,"fall",0,45,false,false),
        new Case("moving_empty_repeat",1,true,false,"",0,0,false,false),
        new Case("sable_floor_45",1,true,true,"stone",0,45,true,false),
        new Case("sable_moving_floor_45",1,true,true,"stone",0,45,true,true),
        new Case("sable_rotated_floor_45",1,true,true,"rotated",0,45,true,false),
        new Case("sable_rotated_moving_floor_45",1,true,true,"rotated",0,45,true,true),
        new Case("sable_floor_crowd120_45",1,true,true,"stone",120,45,true,false),
        new Case("sable_moving_floor_crowd120_45",1,true,true,"stone",120,45,true,true),
        new Case("sable_floor_crowd120_control",0,false,false,"",120,45,true,false),
        new Case("sable_moving_floor_crowd120_control",0,false,false,"",120,45,true,true));
    private static final int WARMUP=200,SAMPLES=800;
    private static final com.sun.management.ThreadMXBean MEMORY=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static PerformanceGameTests active;
    private GameTestHelper helper;
    private int scenario=-1,age;
    private final List<WagonEntity> wagons=new ArrayList<>();
    private final List<Player> drivers=new ArrayList<>();
    private final List<Entity> fixtures=new ArrayList<>();
    private final List<Mob> crowd=new ArrayList<>();
    private final List<LivingEntity> sleepers=new ArrayList<>();
    private final List<Vec3> origins=new ArrayList<>();
    private final List<String> rows=new ArrayList<>();
    private final List<Double> tickTimes=new ArrayList<>(),wagonTimes=new ArrayList<>(),tickBytes=new ArrayList<>(),wagonBytes=new ArrayList<>();
    private long tickStart,allocationStart,entityStart,entityAllocation;
    private double travelled;
    private int clippedTicks,horseBlockedTicks;
    private final List<String> traces=new ArrayList<>();
    private record Trace(int tick,double requested,double actual,boolean horseBlocked,double ms,double kib) {}
    private final List<Trace> scenarioTraces=new ArrayList<>();
    /** Observes the speed request before contact resolution; no extra collision queries. */
    private static final class ProbeWagon extends WagonEntity {
        double requested; boolean horseBlocked;
        ProbeWagon(Level level) { super(WagonContent.WAGON.get(),level); }
        @Override public boolean horsesCanAdvance(Vec3 delta) {
            requested=delta.horizontalDistance();boolean result=super.horsesCanAdvance(delta);
            horseBlocked=!result;return result;
        }
    }
    private Runnable structureStep=()->{},structureClose=()->{};
    @jdk.jfr.Name("tm_wagon.PerformanceScenario")
    @jdk.jfr.Label("Measured wagon workload (after warmup)")
    public static final class Phase extends jdk.jfr.Event { public String scenario; public boolean sable; }
    private Phase phase;
    private static long allocated() { return MEMORY.getThreadAllocatedBytes(Thread.currentThread().threadId()); }
    @GameTest(template="assembly_test",timeoutTicks=24000)
    public static void sequential_live_tick_profile(GameTestHelper helper) {
        if(!Boolean.getBoolean("tm_wagon.performance")) { helper.succeed();return; }
        active=new PerformanceGameTests();active.helper=helper;
        StructureUtils.removeBarriers(helper.getBounds(),helper.getLevel());
        helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,helper.getLevel().getServer());
        helper.getLevel().getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(false,helper.getLevel().getServer());
        helper.getLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,helper.getLevel().getServer());
        helper.getLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,helper.getLevel().getServer());
        helper.getLevel().setDayTime(18000);
        var corner=helper.absolutePos(new BlockPos(-16,7,-48));var far=helper.absolutePos(new BlockPos(112,7,32));
        for(int x=corner.getX()>>4;x<=far.getX()>>4;x++)for(int z=corner.getZ()>>4;z<=far.getZ()>>4;z++) {
            helper.getLevel().getChunk(x,z);helper.getLevel().setChunkForced(x,z,true);
        }
        for(int x=-14;x<=110;x++)for(int z=-45;z<=32;z++) {
            helper.setBlock(new BlockPos(x,7,z),Blocks.STONE);
            for(int y=8;y<=18;y++)helper.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        active.rows.add("scenario,sable,wagons,passengers,sleepers,crowd,colliders,motion_colliders,ticks,wagon_ticks,tick_mean_ms,tick_p95_ms,tick_max_ms,wagon_mean_ms,wagon_p95_ms,tick_KiB,wagon_KiB,distance,clipped_ticks,horse_blocked_ticks");
        active.traces.add("scenario,tick,requested,actual,horse_blocked,wagon_ms,wagon_KiB");
        active.next();
    }
    private void next() {
        if(scenario>=0&&CASES.get(scenario).name.endsWith("_corridor"))corridor(false);
        for(var w:wagons) {
            StrawMatSleep.wake(w.cargo(),null);
            for(int i=0;i<w.horseCapacity();i++)if(w.horse(i)!=null)w.detachHorse(w.horse(i).getUUID(),false);
        }
        for(var e:fixtures)e.discard();for(var p:drivers)p.stopRiding();
        structureClose.run();structureStep=()->{};structureClose=()->{};
        wagons.clear();fixtures.clear();drivers.clear();origins.clear();crowd.clear();sleepers.clear();
        scenario++;
        var selected=System.getProperty("tm_wagon.performanceCases","");
        while(scenario<CASES.size()) {
            var c=CASES.get(scenario);
            boolean excluded=!selected.isEmpty()&&!List.of(selected.split(",")).contains(c.name);
            boolean missingMaid=c.cargo.equals("maids")&&!net.neoforged.fml.ModList.get().isLoaded("touhou_little_maid");
            if(!excluded&&!missingMaid)break;
            scenario++;
        }
        if(scenario>=CASES.size()||CASES.get(scenario).structure&&!StructureCollision.available()) {
            try { Files.write(Path.of("performance-server.csv"),rows);Files.write(Path.of("performance-trace.csv"),traces); }
            catch(java.io.IOException e) { throw new IllegalStateException(e); }
            LogUtils.getLogger().info("PERFORMANCE_SERVER_COMPLETE {} scenarios",rows.size()-1);
            active=null;helper.succeed();return;
        }
        var c=CASES.get(scenario);age=0;travelled=0;clippedTicks=horseBlockedTicks=0;
        tickTimes.clear();wagonTimes.clear();tickBytes.clear();wagonBytes.clear();scenarioTraces.clear();
        Vec3 base=Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(10,8,15)));
        if(c.structure) {
            var fixture=new SablePerformanceFixture(helper.getLevel(),BlockPos.containing(base),c.cargo.equals("rotated"));
            structureStep=c.movingStructure?fixture::step:()->{};structureClose=fixture::close;
        }
        for(int i=0;i<c.wagons;i++) {
            Vec3 origin=base.add(i*12,c.cargo.equals("fall")?8:0,0);
            var w=new ProbeWagon(helper.getLevel());var parts=WagonEntity.defaultParts();
            if(c.wide)parts.put(WagonSlot.BODY,WagonPart.WIDE_CARGO_BODY);
            w.configure(parts,Direction.NORTH);w.applyPose(new WagonPose(origin,180+c.yaw,0,0));
            var p=helper.makeMockPlayer(GameType.CREATIVE);p.setPos(origin.add(0,0,5));
            if(List.of("stone","chest","canopy","rotated","furnace","fall").contains(c.cargo))for(int slot=0;slot<w.cargo().capacity();slot++) {
                require(w.cargo().place(slot,new ItemStack(c.cargo.equals("chest")?Items.CHEST:c.cargo.equals("furnace")?Items.FURNACE:Items.STONE),p)==null,"cargo fixture");
                if(c.cargo.equals("furnace")) { var e=w.cargo().entry(slot);e.inventory.setItem(0,new ItemStack(Items.IRON_ORE,64));e.inventory.setItem(1,new ItemStack(Items.COAL,64)); }
            }
            if(c.cargo.equals("canopy")) { var tag=new net.minecraft.nbt.CompoundTag();tag.putBoolean("Installed",true);w.cargo().canopy().load(tag);w.cargoGeometryChanged();w.cargoChanged(true); }
            if(c.cargo.equals("riders"))for(int slot=0;slot<32;slot+=2)
                require(w.cargo().place(slot,new ItemStack(WagonContent.STOOL.get()),p)==null,"stool fixture");
            if(c.cargo.equals("sleepers")||c.cargo.equals("maids"))for(int slot:new int[]{8,9,10,11,20,21,22,23})
                require(w.cargo().place(slot,new ItemStack(WagonContent.STRAW_MAT.get()),p)==null,"mat fixture");
            helper.getLevel().addFreshEntity(w);wagons.add(w);fixtures.add(w);drivers.add(p);origins.add(origin);
            if(!p.startRiding(w)) {
                var box=SeatClearance.body(p,w,w.companionSeatPosition(0));
                StringBuilder diagnostic=new StringBuilder("driver fixture: box="+box+" border="+helper.getLevel().getWorldBorder().isWithinBounds(box));
                for(var pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ)))
                    diagnostic.append(" ").append(pos).append(":").append(helper.getLevel().hasChunkAt(pos)).append(":").append(helper.getLevel().getBlockState(pos));
                throw new IllegalStateException(diagnostic.toString());
            }w.positionRider(p);
            if(c.cargo.equals("riders"))for(int slot=0;slot<32;slot+=2) {
                var mob=EntityType.VILLAGER.create(helper.getLevel());mob.setNoAi(true);mob.setPos(origin);helper.getLevel().addFreshEntity(mob);fixtures.add(mob);
                require(w.boardCargoSeat(mob,slot),"rider fixture "+slot);w.positionRider(mob);
            }
            if(c.cargo.equals("sleepers")||c.cargo.equals("maids"))for(int slot:new int[]{8,9,10,11,20,21,22,23}) {
                var type=c.cargo.equals("maids")?net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("touhou_little_maid","maid")):EntityType.VILLAGER;
                var mob=(Mob)type.create(helper.getLevel());mob.setNoAi(true);mob.setPos(w.pose().point(w.cargo().centreAt(slot)));helper.getLevel().addFreshEntity(mob);fixtures.add(mob);
                require(StrawMatSleep.sleepMob(w.cargo(),slot,mob),"sleep fixture "+slot);sleepers.add(mob);
            }
            if(c.moving) {
                var horse=EntityType.HORSE.create(helper.getLevel());horse.setNoAi(true);horse.setPos(w.horsePosition(0));helper.getLevel().addFreshEntity(horse);fixtures.add(horse);horse.setLeashedTo(p,true);
                String error=w.attachHorse(p,horse,0);require(error==null,"horse fixture "+error);
            }
        }
        for(int i=0;i<c.mobs;i++) {
            var mob=EntityType.PIG.create(helper.getLevel());mob.setNoAi(!c.name.contains("_ai"));mob.setInvulnerable(true);helper.getLevel().addFreshEntity(mob);fixtures.add(mob);crowd.add(mob);
        }
        if(c.name.endsWith("_corridor"))corridor(true);
        reset();LogUtils.getLogger().info("PERFORMANCE_BEGIN {}",c.name);
    }
    private void corridor(boolean wall) {
        for(int x:new int[]{7,13})for(int z=-20;z<=25;z++)for(int y=8;y<=11;y++)
            helper.setBlock(new BlockPos(x,y,z),wall?Blocks.STONE:Blocks.AIR);
    }
    private void reset() {
        for(int i=0;i<wagons.size();i++) {
            var w=wagons.get(i);w.applyPose(new WagonPose(origins.get(i),180+CASES.get(scenario).yaw,0,0));
            for(int j=0;j<w.horseCapacity();j++)if(w.horse(j)!=null)w.horse(j).setPos(w.horsePosition(j));
        }
        Vec3 base=Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(10,8,15)));
        for(int i=0;i<crowd.size();i++) {
            var m=crowd.get(i);double x=(i%8-3.5)*.55,z=-(i/8)*.55;
            // Keep the same density relative to travel when testing diagonal wagons.
            Vec3 local=new WagonPose(base,180+CASES.get(scenario).yaw,0,0).point(new Vec3(x,0,z));
            m.setPos(local);m.setDeltaMovement(Vec3.ZERO);
        }
    }
    @SubscribeEvent public static void before(ServerTickEvent.Pre event) {
        if(active==null)return;var a=active;
        if(a.age==WARMUP+SAMPLES) { a.finish();a.next();if(active==null)return; }
        if(a.age%40==0)a.reset();a.structureStep.run();
        if(a.age==WARMUP) { a.phase=new Phase();a.phase.scenario=CASES.get(a.scenario).name;a.phase.sable=StructureCollision.available();a.phase.begin(); }
        var c=CASES.get(a.scenario);
        for(int i=0;i<a.wagons.size();i++)a.wagons.get(i).acceptInput(a.drivers.get(i),c.moving?1:0,c.name.endsWith("_turn")?1:0,false);
        a.allocationStart=allocated();a.tickStart=System.nanoTime();
    }
    @SubscribeEvent public static void after(ServerTickEvent.Post event) {
        if(active==null||active.tickStart==0)return;var a=active;
        long time=System.nanoTime()-a.tickStart,bytes=allocated()-a.allocationStart;
        if(a.age>=WARMUP) { a.tickTimes.add(time/1e6);a.tickBytes.add(bytes/1024.0); }
        for(var sleeper:a.sleepers)require(sleeper.isSleeping(),"sleeper woke during profile");
        a.age++;
    }
    @SubscribeEvent public static void entityBefore(EntityTickEvent.Pre event) {
        if(active==null||event.getEntity().level().isClientSide||!(event.getEntity() instanceof WagonEntity))return;
        active.entityAllocation=allocated();active.entityStart=System.nanoTime();
    }
    @SubscribeEvent public static void entityAfter(EntityTickEvent.Post event) {
        if(active==null||event.getEntity().level().isClientSide||!(event.getEntity() instanceof WagonEntity))return;
        long time=System.nanoTime()-active.entityStart,bytes=allocated()-active.entityAllocation;
        if(active.age>=WARMUP) {
            var w=(ProbeWagon)event.getEntity();double actual=w.getDeltaMovement().horizontalDistance();
            active.wagonTimes.add(time/1e6);active.wagonBytes.add(bytes/1024.0);active.travelled+=actual;
            if(w.requested>1e-5&&actual<w.requested*.99)active.clippedTicks++;
            if(w.horseBlocked)active.horseBlockedTicks++;
            active.scenarioTraces.add(new Trace(active.age-WARMUP,w.requested,actual,w.horseBlocked,time/1e6,bytes/1024.0));
        }
    }
    private void finish() {
        phase.end();phase.commit();
        var c=CASES.get(scenario);
        for(var t:scenarioTraces)traces.add(String.format(Locale.ROOT,"%s,%d,%.8f,%.8f,%s,%.6f,%.3f",c.name,t.tick,t.requested,t.actual,t.horseBlocked,t.ms,t.kib));
        require(tickTimes.size()==SAMPLES,"missing tick samples");
        require(wagonTimes.size()==SAMPLES*c.wagons,"wagons not being naturally ticked");
        if(c.moving)require(travelled>1,"moving workload did not move");
        String row=String.format(Locale.ROOT,"%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%.5f,%.5f,%.5f,%.5f,%.5f,%.2f,%.2f,%.3f",c.name,StructureCollision.available(),c.wagons,
            wagons.stream().mapToInt(w->w.getPassengers().size()).sum(),sleepers.size(),crowd.size(),wagons.isEmpty()?0:wagons.getFirst().colliders().size(),
            wagons.isEmpty()?0:wagons.getFirst().motionCollidersAt(wagons.getFirst().pose()).size(),tickTimes.size(),wagonTimes.size(),mean(tickTimes),percentile(tickTimes,.95),percentile(tickTimes,1),mean(wagonTimes),percentile(wagonTimes,.95),mean(tickBytes),mean(wagonBytes),travelled);
        row+=","+clippedTicks+","+horseBlockedTicks;
        rows.add(row);LogUtils.getLogger().info("PERFORMANCE_RESULT {}",row);
    }
    public static double mean(List<Double> values) { return values.stream().mapToDouble(Double::doubleValue).average().orElse(0); }
    public static double percentile(List<Double> values,double fraction) { if(values.isEmpty())return 0;var sorted=new ArrayList<>(values);sorted.sort(null);return sorted.get(Math.min(sorted.size()-1,(int)Math.ceil(sorted.size()*fraction)-1)); }
    private static void require(boolean value,String message) { if(!value)throw new IllegalStateException(message); }
}
