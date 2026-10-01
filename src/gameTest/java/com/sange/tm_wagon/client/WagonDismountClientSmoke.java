package com.sange.tm_wagon.client;

import com.mojang.logging.LogUtils;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in end-to-end test; opens only the separately copied build/dismount-client/saves/repro world. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class WagonDismountClientSmoke {
    private static boolean opened;
    private static int ticks,loading;
    private static net.minecraft.world.phys.Vec3 dismountPosition;
    private static double jumpBase;
    private static boolean jumped;
    private static volatile boolean verified;
    private static volatile String failure;
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        if(!Boolean.getBoolean("tm_wagon.dismountSmokeTest"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();
        if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
            opened=true;mc.createWorldOpenFlows().openWorld("repro",()->{throw new IllegalStateException("Cannot open copied repro world");});return;
        }
        if(mc.player==null||mc.level==null||mc.screen!=null) {
            if(++loading>1200)throw new IllegalStateException("Repro world did not finish loading");return;
        }
        ticks++;
        if(failure!=null)throw new IllegalStateException(failure);
        if(ticks==30) {
            if(!(mc.player.getVehicle() instanceof WagonEntity wagon))throw new IllegalStateException("Test did not mount wagon");
            int wagonId=wagon.getId();float clientYaw=wagon.getYRot();
            mc.getSingleplayerServer().execute(()->{
                var w=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().serverLevel().getEntity(wagonId);
                if(w==null||Math.abs(net.minecraft.util.Mth.wrapDegrees(w.getYRot()-clientYaw))>.01)
                    failure="Client retained quantized wagon yaw instead of synchronized full yaw";
            });
        }
        if(ticks==45) {
            if(mc.player.isPassenger())throw new IllegalStateException("Shift failed to dismount");
            dismountPosition=mc.player.position();
        }
        if(ticks==1)mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            var w=p.serverLevel().getEntitiesOfClass(WagonEntity.class,p.getBoundingBox().inflate(12)).getFirst();
            p.getAbilities().flying=false;p.onUpdateAbilities();p.startRiding(w);
        });
        if(ticks==35)mc.options.keyShift.setDown(true);
        if(ticks==40)mc.options.keyShift.setDown(false);
        if(ticks>=45) { mc.player.setYRot(-172+(Math.min(ticks,119)-45)*2);mc.player.setXRot(15); }
        if(ticks==55)mc.options.keyUp.setDown(true);
        if(ticks==85) { jumpBase=mc.player.getY();mc.options.keyJump.setDown(true); }
        if(ticks>85&&ticks<100)jumped|=mc.player.getY()>jumpBase+.3;
        if(ticks==88)mc.options.keyJump.setDown(false);
        if(ticks==105)mc.options.keyUp.setDown(false);
        if(ticks%5==0) {
            LogUtils.getLogger().info("DISMOUNT_CLIENT tick={} pos={} yaw={} ground={} rider={} velocity={}",ticks,mc.player.position(),mc.player.getYRot(),mc.player.onGround(),mc.player.isPassenger(),mc.player.getDeltaMovement());
            int step=ticks;mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                LogUtils.getLogger().info("DISMOUNT_SERVER tick={} pos={} yaw={} ground={} rider={} velocity={}",step,p.position(),p.getYRot(),p.onGround(),p.isPassenger(),p.getDeltaMovement());
            });
        }
        if(ticks==130) {
            if(mc.player.position().distanceTo(dismountPosition)<2||!jumped)
                throw new IllegalStateException("Dismounted player could not walk or jump");
            var clientPosition=mc.player.position();float yaw=mc.player.getYRot(),pitch=mc.player.getXRot();
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                if(p.isPassenger()||p.position().distanceTo(clientPosition)>.15||Math.abs(p.getYRot()-yaw)>.1||Math.abs(p.getXRot()-pitch)>.1)
                    failure="Server/client did not agree on dismounted position and view";
                else verified=true;
            });
        }
        if(ticks>=140&&verified) { LogUtils.getLogger().info("DISMOUNT_SMOKE_PASSED: Shift, view, walking, jump, server agreement");mc.stop(); }
        if(ticks>200)throw new IllegalStateException("Dismount verification timed out");
    }
}
