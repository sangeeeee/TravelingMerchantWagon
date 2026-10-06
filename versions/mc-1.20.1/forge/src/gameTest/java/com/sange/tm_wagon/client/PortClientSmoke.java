package com.sange.tm_wagon.client;

import com.mojang.math.Axis;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public class PortClientSmoke {
    private static boolean started,opened,gameplayStarted;
    private static volatile boolean gameplayFinished;
    private static int breakSounds;
    @SubscribeEvent public static void sound(net.minecraftforge.client.event.sound.PlaySoundEvent event) {
        if(Boolean.getBoolean("tm_wagon.portSmoke")&&event.getName().equals("block.wood.break"))breakSounds++;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END||!Boolean.getBoolean("tm_wagon.portSmoke")||started)return;
        var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        if(mc.screen instanceof AccessibilityOnboardingScreen){mc.setScreen(new TitleScreen());return;}
        if(!opened&&mc.screen instanceof TitleScreen) {
            opened=true;mc.options.pauseOnLostFocus=false;
            var settings=new net.minecraft.world.level.LevelSettings("Port smoke",net.minecraft.world.level.GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.level.GameRules(),net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("port-smoke-"+System.currentTimeMillis(),settings,new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),
                access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());return;
        }
        if(opened&&mc.level!=null&&mc.player!=null) {
            if(!gameplayStarted) {
                gameplayStarted=true;
                var server=mc.getSingleplayerServer();var playerId=mc.player.getUUID();
                server.execute(()->{
                    try {
                        var p=server.getPlayerList().getPlayer(playerId);var level=p.serverLevel();level.setDayTime(18000);level.updateSkyBrightness();
                        var w=WagonContent.WAGON.get().create(level);w.setPos(p.position());w.setNoGravity(true);level.addFreshEntity(w);
                        p.setPos(w.position().add(-3,1.5,0));
                        var placement=w.cargo().place(4,new ItemStack(WagonContent.STRAW_MAT.get()),p);
                        if(placement!=null)throw new IllegalStateException("Mat placement failed: "+placement);
                        p.setPos(w.cargo().position(w.cargo().entry(4)).add(0,.1,0));
                        var respawn=p.getRespawnPosition();var dimension=p.getRespawnDimension();
                        var error=com.sange.tm_wagon.cargo.StrawMatSleep.sleep(w.cargo(),w.cargo().entry(4),p);
                        if(error!=null)throw new IllegalStateException("Player sleep rejected: "+error);
                        w.setPos(w.position().add(1,0,0));w.setYRot(45);com.sange.tm_wagon.cargo.StrawMatSleep.follow(p);
                        if(!p.isSleeping()||!com.sange.tm_wagon.cargo.StrawMatSleep.continueSleep(p))throw new IllegalStateException("Moving player woke up");
                        if(!java.util.Objects.equals(respawn,p.getRespawnPosition())||!dimension.equals(p.getRespawnDimension()))throw new IllegalStateException("Respawn point changed");
                        p.stopSleepInBed(true,true);
                        if(p.isSleeping()||com.sange.tm_wagon.cargo.StrawMatSleep.matSleeper(p))throw new IllegalStateException("Wake session leaked");
                        com.mojang.logging.LogUtils.getLogger().info("FORGE_PORT_PLAYER_SLEEP_PASSED: real player connection, movement, wake and respawn");
                        if(net.minecraftforge.fml.ModList.get().isLoaded("touhou_little_maid")) {
                            var maid=com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.TYPE.create(level);
                            maid.setOwnerUUID(p.getUUID());maid.setNoAi(true);maid.setPos(w.cargo().position(w.cargo().entry(4)));level.addFreshEntity(maid);
                            p.setPos(w.position().add(-2,1.5,0));
                            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,com.github.tartaricacid.touhoulittlemaid.init.InitItems.SMART_SLAB_EMPTY.get().getDefaultInstance());
                            if(!com.sange.tm_wagon.cargo.StrawMatSleep.sleepMob(w.cargo(),4,maid))throw new IllegalStateException("Maid capture sleep fixture failed");
                            var capture=new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific(p,net.minecraft.world.InteractionHand.MAIN_HAND,maid,net.minecraft.world.phys.Vec3.ZERO);
                            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(capture);
                            if(!capture.isCanceled()||!capture.getCancellationResult().consumesAction()||!maid.isRemoved()
                                ||!p.getMainHandItem().is(com.github.tartaricacid.touhoulittlemaid.init.InitItems.SMART_SLAB_HAS_MAID.get())
                                ||com.sange.tm_wagon.cargo.StrawMatSleep.matSleeper(maid)||w.cargo().entry(4).sleeper!=null)
                                throw new IllegalStateException("Native event did not capture sleeping maid cleanly");
                            com.mojang.logging.LogUtils.getLogger().info("FORGE_PORT_MAID_CAPTURE_PASSED: real owner, sleeping maid, native event priority and sleep cleanup");
                        }
                        w.discard();
                        var framePos=p.blockPosition().offset(3,0,0);
                        level.setBlock(framePos,WagonContent.FRAME.get().defaultBlockState(),3);
                        var frame=(AssemblyFrameBlockEntity)level.getBlockEntity(framePos);frame.initializeFrame();
                        if(frame.install(WagonSlot.BODY,WagonPart.CARGO_BODY,null,new ItemStack(WagonContent.PART_ITEMS.get(WagonPart.CARGO_BODY).get()))!=null)
                            throw new IllegalStateException("Break effect fixture failed");
                        var hit=frame.cargoPose().point(new net.minecraft.world.phys.Vec3(0,1.5,0));
                        p.setPos(hit.add(-3,1,0));var delta=hit.subtract(p.getEyePosition());
                        p.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));
                        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_AXE));
                        if(!p.gameMode.destroyBlock(framePos.above())||frame.has(WagonSlot.BODY)||!p.gameMode.destroyBlock(framePos))
                            throw new IllegalStateException("Native break did not remove body/frame");
                        gameplayFinished=true;
                    } catch(Throwable error){com.mojang.logging.LogUtils.getLogger().error("FORGE_PORT_PLAYER_SLEEP_FAILED",error);mc.execute(mc::stop);}
                });return;
            }
            if(gameplayFinished&&mc.screen==null){started=true;mc.setScreen(new Preview());}
        }
    }
    static class Preview extends Screen {
        int frames;
        final java.util.List<ItemStack> items=new java.util.ArrayList<>();
        final java.util.List<WagonEntity> wagons=new java.util.ArrayList<>();
        final java.util.List<AssemblyFrameBlockEntity> blocks=new java.util.ArrayList<>();
        final AssemblyRenderer renderer=new AssemblyRenderer();
        Preview() {
            super(Component.literal("Forge 1.20.1 renderer verification"));
            items.add(new ItemStack(WagonContent.FRAME_ITEM.get()));for(var part:WagonPart.values())items.add(new ItemStack(WagonContent.PART_ITEMS.get(part).get()));
            items.addAll(java.util.List.of(new ItemStack(WagonContent.CARGO_COVER.get()),new ItemStack(WagonContent.CANOPY.get()),new ItemStack(WagonContent.CABINET.get()),new ItemStack(WagonContent.STOOL.get()),new ItemStack(WagonContent.STRAW_MAT.get())));
            for(var body:new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY}) {
                var w=new WagonEntity(WagonContent.WAGON.get(),Minecraft.getInstance().level){@Override public net.minecraft.world.scores.PlayerTeam getTeam(){return null;}};
                var parts=new java.util.EnumMap<WagonSlot,WagonPart>(WagonSlot.class);parts.putAll(WagonEntity.defaultParts());parts.put(WagonSlot.BODY,body);w.configure(parts,Direction.NORTH);
                var cargo=new net.minecraft.nbt.CompoundTag();var accessory=new net.minecraft.nbt.CompoundTag();accessory.putBoolean("Installed",true);
                if(body==WagonPart.CARGO_BODY){accessory.putInt("OpenRows",2);cargo.put("Cover",accessory);}else {accessory.putBoolean("FrontClosed",body==WagonPart.WIDE_CARGO_BODY);cargo.put("Canopy",accessory);}
                var cabinet=new net.minecraft.nbt.CompoundTag();cabinet.putBoolean("Installed",true);cabinet.putInt("Rows",3);cargo.put("Cabinet",cabinet);
                w.cargo().load(cargo,Minecraft.getInstance().level.registryAccess());wagons.add(w);
                var f=new AssemblyFrameBlockEntity(BlockPos.ZERO,WagonContent.FRAME.get().defaultBlockState());var tag=new net.minecraft.nbt.CompoundTag();tag.put("Modules",WagonEntity.encode(parts));tag.put("Cargo",cargo);f.load(tag);f.setLevel(Minecraft.getInstance().level);blocks.add(f);
            }
        }
        @Override public void render(GuiGraphics g,int x,int y,float partial) {
            if(frames==1) {
                try {
                    var field=net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");field.setAccessible(true);
                    var particles=(java.util.Map<?,?>)field.get(Minecraft.getInstance().particleEngine);
                    long terrain=particles.values().stream().flatMap(value->((java.util.Collection<?>)value).stream()).filter(p->p instanceof net.minecraft.client.particle.TerrainParticle).count();
                    if(breakSounds!=2||terrain==0)throw new IllegalStateException("Break sounds="+breakSounds+", terrain particles="+terrain);
                    com.mojang.logging.LogUtils.getLogger().info("FORGE_PORT_BREAK_EFFECTS_PASSED: two native break sounds, {} terrain particles on the breaking player's client",terrain);
                }catch(Exception error){com.mojang.logging.LogUtils.getLogger().error("FORGE_PORT_BREAK_EFFECTS_FAILED",error);Minecraft.getInstance().stop();return;}
            }
            g.fill(0,0,width,height,0xffdad4c5);g.drawCenteredString(font,title,width/2,8,0xff332a20);
            for(int i=0;i<items.size();i++)g.renderItem(items.get(i),10+(i%12)*24,25+(i/12)*24);
            var mc=Minecraft.getInstance();var buffer=mc.renderBuffers().bufferSource();
            for(int i=0;i<3;i++)for(int form=0;form<2;form++) {
                var pose=g.pose();pose.pushPose();pose.translate(width*(i+.5)/3,height*(form==0?.47:.83),150);pose.scale(21,-21,21);pose.mulPose(Axis.XP.rotationDegrees(23));pose.mulPose(Axis.YP.rotationDegrees(-35));
                if(form==0)renderer.render(blocks.get(i),0,pose,buffer,0xf000f0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                else mc.getEntityRenderDispatcher().getRenderer(wagons.get(i)).render(wagons.get(i),0,0,pose,buffer,0xf000f0);
                buffer.endBatch();pose.popPose();
            }
            g.flush();frames++;
            if(frames==60){Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),message->{});com.mojang.logging.LogUtils.getLogger().info("FORGE_PORT_CLIENT_RENDER_PASSED: items, three sizes, block/entity models, canopy, cover and cabinet");mc.stop();}
        }
    }
}
