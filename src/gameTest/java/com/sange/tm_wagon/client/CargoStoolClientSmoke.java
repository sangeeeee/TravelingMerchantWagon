package com.sange.tm_wagon.client;

import com.mojang.logging.LogUtils;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.CargoSeatEntity;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.client.Minecraft;

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

/** Real integrated-server seat/dismount and input checks, in a disposable build-directory world only. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class CargoStoolClientSmoke {
    private static boolean opened;
    private static int ticks,loading,wagonId;
    private static volatile String failure;
    private static volatile boolean verified;
    private static Vec3 awake;
    private static double jumpBase;
    private static boolean jumped;
    private static final BlockPos FRAME=new BlockPos(0,81,0);
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        if(!Boolean.getBoolean("tm_wagon.stoolSmokeTest"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();
        if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
            opened=true;mc.createWorldOpenFlows().openWorld("repro",()->{throw new IllegalStateException("Cannot open isolated stool world");});return;
        }
        if(mc.player==null||mc.level==null||mc.screen!=null) {
            if(++loading>1600)throw new IllegalStateException("Stool world loading timed out");return;
        }
        if(failure!=null)throw new IllegalStateException(failure);
        ticks++;
        if(ticks==1)server(mc,()->prepare(mc));
        if(ticks==25)require(mc.player.getVehicle() instanceof CargoSeatEntity,"Block seat was not tracked by client");
        if(ticks==35)mc.options.keyShift.setDown(true);
        if(ticks==38)mc.options.keyShift.setDown(false);
        if(ticks==55)require(!mc.player.isPassenger()&&mc.player.getPose()==Pose.STANDING,"Block stool Shift dismount failed");
        if(ticks==75)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var f=(AssemblyFrameBlockEntity)p.serverLevel().getBlockEntity(FRAME);
            require(f.toggleFrame(null)==null,"Could not assemble stool wagon");
            wagonId=p.serverLevel().getEntitiesOfClass(WagonEntity.class,new net.minecraft.world.phys.AABB(FRAME).inflate(6)).getFirst().getId();
        });
        if(ticks==110)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var w=(WagonEntity)p.serverLevel().getEntity(wagonId);
            w.applyPose(new WagonPose(w.position(),213,.18F,.12F));w.syncMotion();
            require(w.cargo().seats.sit(4,p)==null,"Could not sit on tilted entity stool");
        });
        if(ticks==135) {
            require(mc.player.getVehicle() instanceof WagonEntity,"Moving stool did not reuse wagon passenger tracking");
            var w=(WagonEntity)mc.player.getVehicle();
            require(w.passengerSeat(mc.player)==6&&w.driver()==null,"Cargo stool stole driving permission");
        }
        if(ticks==140)mc.options.keyShift.setDown(true);
        if(ticks==143)mc.options.keyShift.setDown(false);
        if(ticks==160) {
            require(!mc.player.isPassenger()&&mc.player.getPose()==Pose.STANDING,"Tilted stool Shift dismount failed");awake=mc.player.position();
        }
        if(ticks>=160&&ticks<240) { mc.player.setYRot(33);mc.player.setXRot(15); }
        if(ticks==175) { jumpBase=mc.player.getY();mc.options.keyJump.setDown(true); }
        if(ticks>175&&ticks<200)jumped|=mc.player.getY()>jumpBase+.2;
        if(ticks==195) { mc.options.keyJump.setDown(false);mc.options.keyUp.setDown(true); }
        if(ticks==225)mc.options.keyUp.setDown(false);
        if(ticks==245) {
            require(jumped&&mc.player.position().distanceTo(awake)>.5,"Dismounted stool rider could not walk and jump");
            require(!mc.player.isPassenger()&&mc.player.getPose()==Pose.STANDING,"Stool camera state survived dismount");
            var point=mc.player.position();float yaw=mc.player.getYRot(),pitch=mc.player.getXRot();
            server(mc,()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                require(!p.isPassenger()&&p.position().distanceTo(point)<.4,"Stool server/client dismount positions disagree");
                require(Math.abs(p.getYRot()-yaw)<.2&&Math.abs(p.getXRot()-pitch)<.2,"Server rejected view rotation after stool dismount");verified=true;
            });
        }
        if(ticks>=260&&verified) { LogUtils.getLogger().info("CARGO_STOOL_CLIENT_PASS: block and tilted entity stools, native Shift dismount, view, walking, jump, driving permission and server agreement");mc.stop(); }
        if(ticks>300)throw new IllegalStateException("Stool verification timed out");
    }
    private static void server(Minecraft mc,Runnable task) {
        mc.getSingleplayerServer().execute(()->{try { task.run(); }catch(Throwable error) { failure=error.toString();LogUtils.getLogger().error("Stool client test failed",error); }});
    }
    private static void prepare(Minecraft mc) {
        var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var level=p.serverLevel();
        if(p.isSleeping())p.stopSleepInBed(true,true);
        if(p.isPassenger())p.stopRiding();
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
        require(f.cargo().place(4,new ItemStack(WagonContent.STOOL.get()),p)==null,"Mat placement failed");
        require(f.cargo().seats.sit(4,p)==null,"Block stool seat failed");
    }
    private static void require(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }
}
