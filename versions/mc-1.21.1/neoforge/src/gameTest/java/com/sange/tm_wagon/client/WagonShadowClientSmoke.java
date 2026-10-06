package com.sange.tm_wagon.client;

import com.sange.tm_wagon.assembly.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Checks the real shader shadow pass, without modifying the shader pack. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class WagonShadowClientSmoke {
    private static boolean opened;
    private static int ticks,shadows,normal;
    private static volatile String failure;
    private static class Probe extends AssemblyRenderer {
        @Override public void render(AssemblyFrameBlockEntity frame,float tick,com.mojang.blaze3d.vertex.PoseStack poses,
                net.minecraft.client.renderer.MultiBufferSource buffers,int light,int overlay) {
            try {
                if(Class.forName("net.irisshaders.iris.shadows.ShadowRenderer").getField("ACTIVE").getBoolean(null))shadows++;
                else normal++;
            }catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
            super.render(frame,tick,poses,buffers,light,overlay);
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if(!Boolean.getBoolean("tm_wagon.shadowSmokeTest"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();
        if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
            opened=true;mc.createWorldOpenFlows().openWorld("repro",()->{});return;
        }
        if(failure!=null)throw new IllegalStateException(failure);
        if(mc.player==null||mc.level==null||mc.screen!=null)return;
        ticks++;
        if(ticks==1) {
            var dispatcher=mc.getBlockEntityRenderDispatcher();var field=dispatcher.getClass().getDeclaredField("renderers");field.setAccessible(true);
            var renderers=new java.util.HashMap<>((java.util.Map)field.get(dispatcher));renderers.put(WagonContent.FRAME_ENTITY.get(),new Probe());field.set(dispatcher,renderers);
            mc.getSingleplayerServer().execute(()->{
                try {
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var level=p.serverLevel();p.stopRiding();
                    level.setDayTime(4000);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());
                    for(var e:level.getEntities(p,new net.minecraft.world.phys.AABB(-15,78,-15,15,95,15)))e.discard();
                    p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(8,86,10);
                    for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++) {
                        level.setBlock(new BlockPos(x,80,z),Blocks.WHITE_CONCRETE.defaultBlockState(),3);
                        for(int y=81;y<91;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
                    }
                    var root=new BlockPos(0,81,0);level.setBlock(root,WagonContent.FRAME.get().defaultBlockState(),3);
                    var frame=(AssemblyFrameBlockEntity)level.getBlockEntity(root);frame.initializeFrame();
                    for(var entry:com.sange.tm_wagon.entity.WagonEntity.defaultParts().entrySet()) {
                        var error=frame.install(entry.getKey(),entry.getValue(),null,WagonContent.PART_ITEMS.get(entry.getValue()).get().getDefaultInstance());
                        if(error!=null)throw new IllegalStateException(error);
                    }
                }catch(Throwable e) { failure=e.toString(); }
            });
        }
        var d=new net.minecraft.world.phys.Vec3(.5,83,.5).subtract(mc.player.getEyePosition());
        mc.player.setYRot((float)-Math.toDegrees(Math.atan2(d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
        if(ticks==160) {
            if(shadows<10||normal<10)throw new IllegalStateException("Missing assembly render pass: shadow="+shadows+" normal="+normal);
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),message->{});
            com.mojang.logging.LogUtils.getLogger().info("WAGON_SHADOW_PASS: shadow={}, normal={}",shadows,normal);mc.stop();
        }
    }
}
