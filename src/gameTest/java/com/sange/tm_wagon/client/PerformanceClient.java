package com.sange.tm_wagon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.performance.PerformanceGameTests;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Native rendered frames in a disposable world, fixed camera, VSync off, 120 FPS cap.
 * Renderer timings measure CPU submission, not isolated GPU time. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class PerformanceClient {
    private record Case(String name,int count,boolean wide,boolean chests,boolean canopy,boolean block) {}
    private static final List<Case> CASES=List.of(new Case("baseline",0,false,false,false,false),
        new Case("small1",1,false,false,false,false),new Case("wide_chests32",1,true,true,false,false),
        new Case("wide_chests32_canopy",1,true,true,true,false),new Case("small8",8,false,false,false,false),
        new Case("wide_chests256",8,true,true,false,false),new Case("wide_chests256_canopy",8,true,true,true,false),new Case("block_wide_chests256",8,true,true,false,true),
        new Case("baseline_repeat",0,false,false,false,false));
    private static boolean opened,measuring;
    private static volatile boolean ready;
    private static volatile String failure;
    private static int scenario=-1,age,loading;
    private static long frameStart,frameAllocation;
    private static java.lang.reflect.Field shadowActive;
    private static final com.sun.management.ThreadMXBean MEMORY=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static final List<Double> frames=new ArrayList<>(),renders=new ArrayList<>(),allocations=new ArrayList<>();
    private static final List<Double> shadowRenders=new ArrayList<>();
    private static final List<String> rows=new ArrayList<>(List.of("scenario,frames,render_calls,frame_mean_ms,frame_p95_ms,render_mean_ms,render_p95_ms,frame_KiB,shadow_calls,shadow_render_mean_ms"));
    private static final List<AssemblyFrameBlockEntity> placed=new ArrayList<>();
    private static long allocated() { return MEMORY.getThreadAllocatedBytes(Thread.currentThread().threadId()); }
    @EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent(priority=EventPriority.LOWEST) public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            if(!Boolean.getBoolean("tm_wagon.performanceClient"))return;
            event.registerEntityRenderer(WagonContent.WAGON.get(),Probe::new);
            event.registerBlockEntityRenderer(WagonContent.FRAME_ENTITY.get(),context->new FrameProbe());
        }
    }
    private static class Probe extends WagonRenderer {
        Probe(EntityRendererProvider.Context context) { super(context); }
        @Override public void render(WagonEntity w,float yaw,float tick,PoseStack poses,MultiBufferSource buffers,int light) {
            long start=System.nanoTime();super.render(w,yaw,tick,poses,buffers,light);record(start);
        }
    }
    private static class FrameProbe extends AssemblyRenderer {
        @Override public void render(AssemblyFrameBlockEntity f,float tick,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
            long start=System.nanoTime();super.render(f,tick,poses,buffers,light,overlay);record(start);
        }
    }
    private static void record(long start) {
        if(!measuring)return;double elapsed=(System.nanoTime()-start)/1e6;
        try { if(shadowActive!=null&&shadowActive.getBoolean(null))shadowRenders.add(elapsed);else renders.add(elapsed); }
        catch(IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    @SubscribeEvent public static void framePre(RenderFrameEvent.Pre event) { if(measuring) { frameAllocation=allocated();frameStart=System.nanoTime(); } }
    @SubscribeEvent public static void framePost(RenderFrameEvent.Post event) { if(measuring&&frameStart!=0) { frames.add((System.nanoTime()-frameStart)/1e6);allocations.add((allocated()-frameAllocation)/1024.0); } }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("tm_wagon.performanceClient"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();mc.setWindowActive(true);
        if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
            try { shadowActive=Class.forName("net.irisshaders.iris.shadows.ShadowRenderer").getField("ACTIVE"); }
            catch(ClassNotFoundException ignored) {}catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
            LogUtils.getLogger().info("PERFORMANCE_GPU {} / {}",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER),org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));
            opened=true;mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.renderDistance().set(6);mc.options.fov().set(90);
            mc.createWorldOpenFlows().openWorld("repro",()->{throw new IllegalStateException("No disposable performance world");});return;
        }
        if(failure!=null)throw new IllegalStateException(failure);
        if(mc.player==null||mc.level==null||mc.screen!=null) { if(++loading>2000)throw new IllegalStateException("Profile loading timed out");return; }
        if(scenario<0) { next(mc);return; }
        if(!ready)return;
        Vec3 d=new Vec3(0,82.5,-5).subtract(mc.player.getEyePosition());
        mc.player.setYRot((float)-Math.toDegrees(Math.atan2(d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
        age++;
        if(age==80) { frames.clear();renders.clear();allocations.clear();shadowRenders.clear();measuring=true; }
        if(age==240) {
            measuring=false;var c=CASES.get(scenario);
            if(frames.size()<20||c.count>0&&renders.size()<frames.size()*c.count*.8)throw new IllegalStateException("Insufficient visible rendered workload: "+c.name+" frames="+frames.size()+" renders="+renders.size());
            String row=String.format(Locale.ROOT,"%s,%d,%d,%.5f,%.5f,%.5f,%.5f,%.2f,%d,%.5f",c.name,frames.size(),renders.size(),PerformanceGameTests.mean(frames),PerformanceGameTests.percentile(frames,.95),PerformanceGameTests.mean(renders),PerformanceGameTests.percentile(renders,.95),PerformanceGameTests.mean(allocations),shadowRenders.size(),PerformanceGameTests.mean(shadowRenders));
            rows.add(row);LogUtils.getLogger().info("PERFORMANCE_CLIENT_RESULT {}",row);
            if(c.block)net.minecraft.client.Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),message->{});
            next(mc);
        }
    }
    private static void next(Minecraft mc) {
        ready=false;age=0;scenario++;
        var selected=System.getProperty("tm_wagon.performanceClientCases","");
        while(scenario<CASES.size()&&!selected.isEmpty()&&!List.of(selected.split(",")).contains(CASES.get(scenario).name))scenario++;
        if(scenario==CASES.size()) {
            try { Files.write(Path.of(mc.gameDirectory.toString(),"performance-client.csv"),rows); }
            catch(java.io.IOException e) { throw new IllegalStateException(e); }
            LogUtils.getLogger().info("PERFORMANCE_CLIENT_COMPLETE");mc.stop();return;
        }
        var c=CASES.get(scenario);
        mc.getSingleplayerServer().execute(()->{
            try {
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var level=p.serverLevel();p.stopRiding();if(p.isSleeping())p.stopSleepInBed(true,true);
                for(var f:placed)f.dismantle(false,true);placed.clear();
                for(var e:level.getEntities(p,new AABB(-60,70,-60,60,110,60)))e.discard();
                if(scenario==0) {
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
                    level.getGameRules().getRule(GameRules.RULE_DOENTITYDROPS).set(false,level.getServer());
                    level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(false,level.getServer());
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.setDayTime(4000);
                    for(int x=-28;x<=28;x++)for(int z=-26;z<=32;z++) {
                        level.setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),3);
                        for(int y=81;y<=94;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
                    }
                }
                p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(.5,93,28.5);
                for(int i=0;i<c.count;i++) {
                    var pos=new BlockPos(c.count==1?0:(i%4)*8-12,81,(i/4)*12-12);
                    var parts=WagonEntity.defaultParts();if(c.wide)parts.put(WagonSlot.BODY,WagonPart.WIDE_CARGO_BODY);
                    com.sange.tm_wagon.cargo.CargoHold hold;
                    if(c.block) {
                        level.setBlock(pos,WagonContent.FRAME.get().defaultBlockState(),3);var frame=(AssemblyFrameBlockEntity)level.getBlockEntity(pos);frame.initializeFrame();
                        for(var e:parts.entrySet())require(frame.install(e.getKey(),e.getValue(),null,new ItemStack(WagonContent.PART_ITEMS.get(e.getValue()).get())));
                        placed.add(frame);hold=frame.cargo();
                    } else {
                        var w=WagonContent.WAGON.get().create(level);w.configure(parts,Direction.NORTH);w.setPos(Vec3.atBottomCenterOf(pos));level.addFreshEntity(w);hold=w.cargo();
                    }
                    p.teleportTo(pos.getX()+5,pos.getY()+1,pos.getZ());
                    if(c.chests)for(int slot=0;slot<hold.capacity();slot++)require(hold.place(slot,new ItemStack(Items.CHEST),p));
                    if(c.canopy) { var tag=new net.minecraft.nbt.CompoundTag();tag.putBoolean("Installed",true);hold.canopy().load(tag);hold.owner().cargoGeometryChanged();hold.changed(true); }
                }
                p.teleportTo(.5,93,28.5);ready=true;LogUtils.getLogger().info("PERFORMANCE_CLIENT_BEGIN {}",c.name);
            }catch(Throwable e) { failure=e.toString();LogUtils.getLogger().error("Performance fixture failed",e); }
        });
    }
    private static void require(String error) { if(error!=null)throw new IllegalStateException(error); }
}
