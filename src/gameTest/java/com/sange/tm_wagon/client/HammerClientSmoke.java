package com.sange.tm_wagon.client;

import com.mojang.logging.LogUtils;
import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Native mouse attack and hold-to-mine paths, in the disposable hammer-client fixture world. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class HammerClientSmoke {
    private static boolean opened;
    private static int ticks,loading,id;
    private static volatile String failure;
    private static final BlockPos TARGET=new BlockPos(-1,81,0);
    private static final BlockPos BLOCKED=new BlockPos(0,83,0);
    private static final BlockPos INTERIOR=new BlockPos(0,82,0);
    private static final BlockPos CORNER=new BlockPos(1,82,0);
    private static final BlockPos GROUND=new BlockPos(0,80,1);
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        if(!Boolean.getBoolean("tm_wagon.hammerSmokeTest"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();
        if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
            opened=true;mc.createWorldOpenFlows().openWorld("repro",()->{throw new IllegalStateException("Cannot open hammer fixture");});return;
        }
        if(failure!=null)throw new IllegalStateException(failure);
        if(mc.player==null||mc.level==null||mc.screen!=null) { if(++loading>1600)throw new IllegalStateException("Hammer fixture loading timed out");return; }
        ticks++;
        if(ticks==1)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var level=p.serverLevel();p.stopRiding();
            if(p.isSleeping())p.stopSleepInBed(true,true);
            for(var e:level.getEntities(p,new AABB(-12,78,-12,12,92,12)))e.discard();
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++) {
                level.setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),3);
                for(int y=81;y<=89;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
            }
            p.setGameMode(GameType.SURVIVAL);p.getAbilities().flying=false;p.onUpdateAbilities();p.teleportTo(-2.5,81,.5);
            p.setItemInHand(InteractionHand.MAIN_HAND,Items.IRON_AXE.getDefaultInstance());
            level.setBlock(new BlockPos(0,81,0),WagonContent.FRAME.get().defaultBlockState(),3);
            var w=WagonContent.WAGON.get().create(level);w.setPos(.5,81,.5);level.addFreshEntity(w);id=w.getId();
            level.setBlock(TARGET.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(TARGET,Blocks.SHORT_GRASS.defaultBlockState(),3);
            level.setBlock(BLOCKED.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(BLOCKED,Blocks.SHORT_GRASS.defaultBlockState(),3);
        });
        if(ticks>=30&&ticks<=500) {
            BlockPos aim=ticks<180?TARGET:ticks<240?BLOCKED:ticks<280?GROUND:BLOCKED;
            Vec3 target=ticks>=280&&ticks<335?Vec3.atLowerCornerOf(INTERIOR).add(.5,.8,.5):
                ticks>=335&&ticks<420?Vec3.atLowerCornerOf(CORNER).add(.08,.8,.5):Vec3.atLowerCornerOf(aim).add(.5,.3,.5);
            Vec3 d=target.subtract(mc.player.getEyePosition());
            mc.player.setYRot((float)-Math.toDegrees(Math.atan2(d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
            mc.gameRenderer.pick(1);
        }
        if(ticks==40) {
            require(mc.hitResult instanceof BlockHitResult h&&h.getBlockPos().equals(TARGET),"Gap did not select visible grass inside broad bounds");
            invoke(mc,"startAttack");
        }
        if(ticks==55) {
            require(mc.level.getBlockState(TARGET).isAir(),"Client grass restored after acknowledgement");
            server(mc,()->{
                var level=mc.getSingleplayerServer().overworld();require(level.getBlockState(TARGET).isAir(),"Server rejected gap grass break");require(level.getEntity(id)!=null,"Axe broke wagon");
                level.setBlock(TARGET,Blocks.OAK_PLANKS.defaultBlockState(),3);
            });
        }
        if(ticks==75)invoke(mc,"startAttack");
        if(ticks>=76&&ticks<=120)invoke(mc,"continueAttack",true);
        if(ticks==135) {
            require(mc.level.getBlockState(TARGET).isAir(),"Client planks restored after acknowledgement");
            server(mc,()->{
                var level=mc.getSingleplayerServer().overworld();require(level.getBlockState(TARGET).isAir(),"Server rejected held mining in gap");
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.CREATIVE);
                level.setBlock(TARGET,Blocks.SHORT_GRASS.defaultBlockState(),3);
            });
        }
        if(ticks==155)invoke(mc,"startAttack");
        if(ticks==170) {
            require(mc.level.getBlockState(TARGET).isAir(),"Creative client grass restored");
            server(mc,()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();require(p.serverLevel().getBlockState(TARGET).isAir(),"Creative server grass restored");p.setGameMode(GameType.SURVIVAL);});
        }
        if(ticks==190) {
            require(mc.hitResult instanceof EntityHitResult h&&h.getEntity().getId()==id,"Solid wall did not occlude rear block");
            mc.player.resetAttackStrengthTicker();
            invoke(mc,"startAttack");
            var input=new net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered(0,mc.options.keyAttack,InteractionHand.MAIN_HAND);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(input);
            require(input.isCanceled()&&!input.shouldSwingHand(),"Wagon still accepts an ordinary attack input");
            require(mc.level.getBlockState(BLOCKED).is(Blocks.SHORT_GRASS),"Wall click temporarily broke hidden grass");
        }
        if(ticks>=191&&ticks<=220)invoke(mc,"continueAttack",true);
        if(ticks==230)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();require(p.serverLevel().getBlockState(BLOCKED).is(Blocks.SHORT_GRASS),"Mined through cargo wall");
            p.teleportTo(.5,82.5,1.5);
        });
        if(ticks==255) {
            require(mc.hitResult instanceof EntityHitResult h&&h.getEntity().getId()==id,"Standing on wagon could target ground through floor");invoke(mc,"startAttack");
        }
        if(ticks==270)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();require(p.serverLevel().getBlockState(GROUND).is(Blocks.STONE),"Mined ground through floor");
            p.teleportTo(.5,82.5,1.5);
            var level=p.serverLevel();level.setBlock(new BlockPos(0,80,0),Blocks.DIRT.defaultBlockState(),3);
            level.setBlock(INTERIOR.below(),Blocks.TALL_GRASS.defaultBlockState(),3);
            level.setBlock(INTERIOR,Blocks.TALL_GRASS.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),3);
        });
        if(ticks==300) {
            require(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(INTERIOR),"Visible tall grass above the cargo floor was not selectable: "+mc.hitResult);
            invoke(mc,"startAttack");
        }
        if(ticks==320)server(mc,()->{
            var level=mc.getSingleplayerServer().overworld();require(level.getBlockState(INTERIOR).isAir()&&level.getBlockState(INTERIOR.below()).isAir(),"Interior tall grass break was rejected");
            var w=(WagonEntity)level.getEntity(id);w.applyPose(new com.sange.tm_wagon.physics.WagonPose(w.position(),225,0,0));
            level.setBlock(CORNER,Blocks.POWDER_SNOW.defaultBlockState(),3);
        });
        if(ticks==355) {
            require(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(CORNER),"Visible powder snow corner in rotated wagon was not selectable: "+mc.hitResult);
            // Also exercise correction of a coarse/stale wagon target supplied by another picker.
            var w=(WagonEntity)mc.level.getEntity(id);
            var resolved=WagonWorldBlockPicking.resolve(mc.player,mc.player.blockInteractionRange(),1,new EntityHitResult(w,w.position()));
            require(resolved instanceof BlockHitResult hit&&hit.getBlockPos().equals(CORNER),"Wagon target hid a nearer world outline");
            mc.hitResult=new EntityHitResult(w,w.position());
            invoke(mc,"startAttack");
            require(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(CORNER),"Attack input did not recover the visible world target");
        }
        if(ticks>=356&&ticks<400)invoke(mc,"continueAttack",true);
        if(ticks==410) {
            require(mc.level.getBlockState(CORNER).isAir(),"Client restored mined interior powder snow");
            server(mc,()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();require(p.serverLevel().getBlockState(CORNER).isAir(),"Server rejected interior powder snow mining");
                var w=(WagonEntity)p.serverLevel().getEntity(id);w.applyPose(new com.sange.tm_wagon.physics.WagonPose(w.position(),180,0,0));
                p.teleportTo(-2.5,81,.5);p.setItemInHand(InteractionHand.MAIN_HAND,WagonContent.DISMANTLING_HAMMER.get().getDefaultInstance());
            });
        }
        if(ticks==450) { require(mc.player.getMainHandItem().is(WagonContent.DISMANTLING_HAMMER.get()),"Hammer did not synchronize");invoke(mc,"startAttack"); }
        if(ticks==470)server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();require(p.serverLevel().getEntity(id)==null,"Hammer attack did not dismantle server wagon");
            require(p.getMainHandItem().getDamageValue()==1,"Hammer durability was not synchronized");
        });
        if(ticks==490) {
            require(mc.level.getEntity(id)==null&&mc.player.getMainHandItem().getDamageValue()==1,"Client retained wagon or wrong durability");
            LogUtils.getLogger().info("HAMMER_CLIENT_PASS: no ordinary attack; visible tall grass and powder snow corners mine inside rotated wagon; walls/floor occlude; one-hit dismantle");mc.stop();
        }
    }
    private static void invoke(Minecraft mc,String name,Object... args) {
        try { var method=Minecraft.class.getDeclaredMethod(name,args.length==0?new Class<?>[0]:new Class<?>[]{boolean.class});method.setAccessible(true);method.invoke(mc,args); }
        catch(ReflectiveOperationException e) { throw new IllegalStateException("Native input failed: "+name,e); }
    }
    private static void server(Minecraft mc,Runnable task) { mc.getSingleplayerServer().execute(()->{try { task.run(); }catch(Throwable error) { failure=error.toString(); }}); }
    private static void require(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }
}
