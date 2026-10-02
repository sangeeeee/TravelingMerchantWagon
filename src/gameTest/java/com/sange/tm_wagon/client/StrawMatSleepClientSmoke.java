package com.sange.tm_wagon.client;

import com.mojang.logging.LogUtils;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.cargo.StrawMatSleep;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real integrated-server sleep/wake and input checks, in a disposable build-directory world only. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class StrawMatSleepClientSmoke {
    private static boolean opened;
    private static int ticks,loading,wagonId;
    private static volatile String failure;
    private static volatile boolean verified;
    private static Vec3 awake;
    private static double jumpBase;
    private static boolean jumped;
    private static final BlockPos FRAME=new BlockPos(0,81,0);
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        if(!Boolean.getBoolean("tm_wagon.strawMatSleepSmokeTest"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();
        if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
            opened=true;mc.createWorldOpenFlows().openWorld("repro",()->{throw new IllegalStateException("Cannot open isolated straw mat world");});return;
        }
        if(mc.player==null||mc.level==null||mc.screen!=null&&!(mc.screen instanceof InBedChatScreen)) {
            if(++loading>1600)throw new IllegalStateException("Straw mat world loading timed out");return;
        }
        if(failure!=null)throw new IllegalStateException(failure);
        ticks++;
        if(ticks==1)server(mc,()->prepare(mc));
        if(ticks==35)checkSleeping(mc,false);
        if(ticks==45)leaveBed(mc);
        if(ticks==60) {
            checkAwake(mc);server(mc,()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var f=(AssemblyFrameBlockEntity)p.serverLevel().getBlockEntity(FRAME);
                require(f.toggleFrame(null)==null,"Could not assemble sleeping test wagon");
                wagonId=p.serverLevel().getEntitiesOfClass(WagonEntity.class,new net.minecraft.world.phys.AABB(FRAME).inflate(6)).getFirst().getId();
            });
        }
        if(ticks==90)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var w=(WagonEntity)p.serverLevel().getEntity(wagonId);
            w.applyPose(new WagonPose(w.position(),213,.18F,.12F));w.syncMotion();
            require(StrawMatSleep.sleep(w.cargo(),w.cargo().entry(8),p)==null,"Could not sleep on tilted entity wagon");
        });
        if(ticks==120) { checkSleeping(mc,true);leaveBed(mc); }
        if(ticks==140) {
            checkAwake(mc);awake=mc.player.position();server(mc,()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var w=(WagonEntity)p.serverLevel().getEntity(wagonId);
                require(w.cargo().toggleGate()==null,"Could not open tailgate");
                require(p.getRespawnPosition().equals(new BlockPos(8,81,0)),"Mat changed the existing respawn point");
            });
        }
        if(ticks>=145&&ticks<240) { mc.player.setYRot(33);mc.player.setXRot(15); }
        if(ticks==160) { jumpBase=mc.player.getY();mc.options.keyJump.setDown(true); }
        if(ticks>160&&ticks<185)jumped|=mc.player.getY()>jumpBase+.2;
        if(ticks==180) { mc.options.keyJump.setDown(false);mc.options.keyUp.setDown(true); }
        if(ticks==225)mc.options.keyUp.setDown(false);
        if(ticks>=140&&ticks%5==0)LogUtils.getLogger().info("MAT_WAKE_CLIENT tick={} pos={} ground={} velocity={} screen={} jumped={}",ticks,mc.player.position(),mc.player.onGround(),mc.player.getDeltaMovement(),mc.screen==null?"none":mc.screen.getClass().getSimpleName(),jumped);
        if(ticks==245) {
            require(jumped&&mc.player.position().distanceTo(awake)>.8,"Waking player could not walk and jump: jumped="+jumped+", distance="+mc.player.position().distanceTo(awake)+", ground="+mc.player.onGround());
            require(!mc.player.isSleeping()&&!mc.player.isPassenger()&&StrawMatSleep.sleepingPoint(mc.player)==null,"Sleep camera state survived wake");
            var point=mc.player.position();float yaw=mc.player.getYRot(),pitch=mc.player.getXRot();
            server(mc,()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                require(!p.isSleeping()&&!p.isPassenger()&&p.position().distanceTo(point)<.35,"Server/client wake positions disagree");
                require(Math.abs(p.getYRot()-yaw)<.2&&Math.abs(p.getXRot()-pitch)<.2,"Server rejected view rotation after wake");verified=true;
            });
        }
        if(ticks>=260&&verified) { LogUtils.getLogger().info("STRAW_MAT_SLEEP_CLIENT_PASS: block sleep, tilted entity sleep, leave-bed packet, view, walking, jump, unchanged respawn and server agreement");mc.stop(); }
        if(ticks>300)throw new IllegalStateException("Straw mat sleep verification timed out");
    }
    private static void server(Minecraft mc,Runnable task) {
        mc.getSingleplayerServer().execute(()->{try { task.run(); }catch(Throwable error) { failure=error.toString();LogUtils.getLogger().error("Straw mat client test failed",error); }});
    }
    private static void prepare(Minecraft mc) {
        var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var level=p.serverLevel();
        if(p.isSleeping())p.stopSleepInBed(true,true);
        for(var entity:level.getEntities(p,new net.minecraft.world.phys.AABB(-12,78,-12,12,90,12)))entity.discard();
        for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++) {
            level.getChunk(new BlockPos(x,80,z));level.setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=81;y<=88;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        p.setGameMode(GameType.SURVIVAL);p.getAbilities().flying=false;p.onUpdateAbilities();p.teleportTo(-3,82.5,0);
        level.setBlock(FRAME,WagonContent.FRAME.get().defaultBlockState(),3);var f=(AssemblyFrameBlockEntity)level.getBlockEntity(FRAME);
        require(f.initializeFrame()==null,"Frame init failed");
        require(f.install(WagonSlot.BODY,WagonPart.CARGO_BODY,null,new ItemStack(WagonContent.PART_ITEMS.get(WagonPart.CARGO_BODY).get()))==null,"Body init failed");
        for(var entry:WagonEntity.defaultParts().entrySet())if(entry.getKey()!=WagonSlot.BODY)
            require(f.install(entry.getKey(),entry.getValue(),null,new ItemStack(WagonContent.PART_ITEMS.get(entry.getValue()).get()))==null,"Module init failed");
        require(f.cargo().place(8,new ItemStack(WagonContent.STRAW_MAT.get()),p)==null,"Mat placement failed");
        p.setRespawnPosition(level.dimension(),new BlockPos(8,81,0),47,false,false);level.setDayTime(13000);level.updateSkyBrightness();
        require(StrawMatSleep.sleep(f.cargo(),f.cargo().entry(8),p)==null,"Block-form native sleep failed");
    }
    private static void leaveBed(Minecraft mc) {
        mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.STOP_SLEEPING));
    }
    private static void checkSleeping(Minecraft mc,boolean tilted) {
        require(mc.player.isSleeping()&&mc.player.getPose()==Pose.SLEEPING&&StrawMatSleep.sleepingPoint(mc.player)!=null,"Client did not enter the mat sleeping pose/session");
        if(tilted)require(Math.abs(StrawMatSleep.sleepingPose(mc.player).roll()-.12F)<.001,"Client did not receive the tilted sleeping pose");
    }
    private static void checkAwake(Minecraft mc) {
        require(!mc.player.isSleeping()&&mc.player.getPose()!=Pose.SLEEPING&&StrawMatSleep.sleepingPoint(mc.player)==null,"Leave-bed left a stuck sleeping camera");
    }
    private static void require(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }
}
