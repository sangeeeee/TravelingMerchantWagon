package com.sange.tm_wagon.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import com.mojang.logging.LogUtils;
import com.sange.tm_wagon.assembly.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in native renderer check, excluded from the production JAR. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public class StageOneClientSmoke {
    private static boolean started;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("tm_wagon.clientSmokeTest") || started) return;
        Minecraft mc=Minecraft.getInstance();
        if (mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
            started=true;mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new PreviewScreen());
        }
    }

    private static class PoseProbe extends AssemblyFrameBlockEntity {
        private double progress;
        PoseProbe() { super(new BlockPos(20,0,0),WagonContent.FRAME.get().defaultBlockState()); }
        @Override public double getTick(Object object) { return 100; }
        @Override public double frameProgress(double partialTick) { return progress; }
    }

    private static class PreviewScreen extends Screen {
        private int frames;
        private final List<ItemStack> items=new ArrayList<>();
        private final AssemblyRenderer renderer=new AssemblyRenderer();
        private final List<AssemblyFrameBlockEntity> assemblies=new ArrayList<>();
        private final List<AssemblyFrameBlockEntity> framesOnly=new ArrayList<>();
        private final PoseProbe probe=new PoseProbe();
        private final List<com.sange.tm_wagon.entity.WagonEntity> wagons=new ArrayList<>();
        PreviewScreen() {
            super(Component.literal("Wagon stage-one renderer verification"));
            items.add(WagonContent.FRAME_ITEM.get().getDefaultInstance());
            for(WagonPart part:WagonPart.values())items.add(WagonContent.PART_ITEMS.get(part).get().getDefaultInstance());
            items.add(WagonContent.ICON.get().getDefaultInstance());
            for(int i=0;i<5;i++) {
                var frame=new AssemblyFrameBlockEntity(new BlockPos(i,0,0),WagonContent.FRAME.get().defaultBlockState().setValue(AssemblyFrameBlock.FACING,Direction.NORTH));
                CompoundTag modules=new CompoundTag();modules.putString("BODY","CARGO_BODY");
                if(i<4) {
                    modules.putString("SEAT",i<2?"SINGLE_SEAT":"DOUBLE_SEAT");
                    modules.putString("SHAFTS",i%2==0?"SINGLE_HORSE_SHAFTS":"DOUBLE_HORSE_SHAFTS");
                    modules.putString("FRONT_LEFT","SMALL_WHEEL");modules.putString("FRONT_RIGHT","SMALL_WHEEL");
                    modules.putString("REAR_LEFT","LARGE_WHEEL");modules.putString("REAR_RIGHT","LARGE_WHEEL");
                }
                CompoundTag tag=new CompoundTag();tag.put("Modules",modules);
                frame.loadWithComponents(tag,Minecraft.getInstance().getConnection()==null?net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY):Minecraft.getInstance().level.registryAccess());
                assemblies.add(frame);
            }
            for(int i=0;i<2;i++)framesOnly.add(new AssemblyFrameBlockEntity(new BlockPos(10+i,0,0),WagonContent.FRAME.get().defaultBlockState().setValue(AssemblyFrameBlock.EXTENDED,i==0)));
            for(int i=0;i<4;i++) {
                // This preview runs on the title screen, without a world scoreboard.
                var wagon=new com.sange.tm_wagon.entity.WagonEntity(WagonContent.WAGON.get(),null) {
                    @Override public net.minecraft.world.scores.PlayerTeam getTeam() { return null; }
                };
                var parts=new java.util.EnumMap<WagonSlot,WagonPart>(WagonSlot.class);parts.putAll(com.sange.tm_wagon.entity.WagonEntity.defaultParts());
                if(i>=2)parts.put(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);
                if(i%2==1)parts.put(WagonSlot.SHAFTS,WagonPart.DOUBLE_HORSE_SHAFTS);
                wagon.configure(parts,new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}[i]);wagons.add(wagon);
            }
        }
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
            graphics.fill(0,0,width,height,0xffede6d7);
            graphics.drawCenteredString(font,"TravelingMerchantWagon / native renderer check",width/2,12,0xff463729);
            if(frames<25) {
                for(int i=0;i<items.size();i++) {
                    int x=width/6+(i%3)*width/3,y=60+(i/3)*100;
                    graphics.fill(x-25,y-1,x+25,y+49,0xff5c4c38);
                    graphics.fill(x-24,y,x+24,y+48,0xffd0c5af);
                    graphics.pose().pushPose();graphics.pose().translate(x-24,y,0);graphics.pose().scale(3,3,3);
                    graphics.renderItem(items.get(i),0,0);graphics.pose().popPose();
                    graphics.fill(x-9,y+51,x+9,y+69,0xff5c4c38);
                    graphics.fill(x-8,y+52,x+8,y+68,0xffd0c5af);
                    graphics.renderItem(items.get(i),x-8,y+52);
                    graphics.drawCenteredString(font,items.get(i).getHoverName(),x,y+73,0xff463729);
                }
            } else if(frames<50) {
                for(int i=0;i<4;i++) drawAssembly(graphics,assemblies.get(i),width/4+(i%2)*width/2,110+(i/2)*170,23);
                // Render a body-only instance after full wagons, verifying shared
                // model visibility is reset rather than inherited from a neighbour.
                drawAssembly(graphics,assemblies.get(4),width/2,height-35,9);
            } else if(frames<75) {
                for(int i=0;i<2;i++) {
                    drawAssembly(graphics,framesOnly.get(i),width/4+i*width/2,height/2,80);
                    graphics.drawCenteredString(font,i==0?"Extended / default":"Folded",width/4+i*width/2,height-40,0xff463729);
                }
            } else {
                for(int i=0;i<4;i++) {
                    drawWagon(graphics,wagons.get(i),width/4+(i%2)*width/2,105+(i/2)*175,25);
                    graphics.drawCenteredString(font,wagons.get(i).facing().name(),width/4+(i%2)*width/2,172+(i/2)*175,0xff463729);
                }
            }
            if(frames==64)verifyCachedFramePose(graphics);
            if(frames==80)verifyAudio();
            if(frames==90)verifyEntityAndFrameVisibility(graphics);
            if(frames==93)verifyDrivingBones(graphics);
            graphics.flush();frames++;
            if(frames==15)save("stage-one-items.png");
            if(frames==40)save("stage-one-assemblies.png");
            if(frames==65)save("folding-frame-states.png");
            if(frames==90)save("wagon-entity-variants.png");
            if(frames==100) {LogUtils.getLogger().info("TM_WAGON_CLIENT_SMOKE_PASS: 9 items, 4 block and entity combinations, 4 entity directions, body-only and both frame states rendered");Minecraft.getInstance().stop();}
        }
        private void verifyAudio() {
            try(var stream=new net.minecraft.client.sounds.JOrbisAudioStream(StageOneClientSmoke.class.getResourceAsStream("/assets/tm_wagon/sounds/wagon_roll.ogg"))) {
                if(stream.getFormat().getChannels()!=1 || stream.read(8192).remaining()==0)throw new IllegalStateException("Invalid wagon OGG");
                LogUtils.getLogger().info("TM_WAGON_AUDIO_PASS: mono OGG decoded, {} Hz",stream.getFormat().getSampleRate());
            } catch(java.io.IOException error) { throw new IllegalStateException(error); }
        }
        private void drawAssembly(GuiGraphics graphics,AssemblyFrameBlockEntity frame,int x,int y,float scale) {
            graphics.pose().pushPose();graphics.pose().translate(x,y,500);
            graphics.pose().scale(scale,-scale,scale);graphics.pose().mulPose(Axis.XP.rotationDegrees(25));
            graphics.pose().mulPose(Axis.YP.rotationDegrees(-35));
            if(frame.has(WagonSlot.BODY))graphics.pose().translate(-.5,-1.5,.7);
            else graphics.pose().translate(-.5,-.68,-.5);
            Lighting.setupForEntityInInventory();
            renderer.render(frame,0,graphics.pose(),graphics.bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            graphics.flush();graphics.pose().popPose();Lighting.setupFor3DItems();
        }
        private void verifyCachedFramePose(GuiGraphics graphics) {
            for(double progress:new double[]{0,.5,1,.5,0}) {
                probe.progress=progress;
                drawAssembly(graphics,probe,-1000,-1000,1);
                var platform=renderer.getGeoModel().getBone("frame_grid_platform").orElseThrow();
                if(Math.abs(platform.getPosY()-FrameMotion.platformOffset(progress))>.0001)
                    throw new IllegalStateException("Cached renderer displayed a stale or endpoint frame pose");
            }
            LogUtils.getLogger().info("TM_WAGON_CACHED_POSE_PASS: both directions evaluated at the same animation tick");
        }
        private void drawWagon(GuiGraphics graphics,com.sange.tm_wagon.entity.WagonEntity wagon,int x,int y,float scale) {
            drawWagon(graphics,wagon,x,y,scale,0);
        }
        private void drawWagon(GuiGraphics graphics,com.sange.tm_wagon.entity.WagonEntity wagon,int x,int y,float scale,float partial) {
            var entityRenderer=(WagonRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(wagon);
            graphics.pose().pushPose();graphics.pose().translate(x,y,500);graphics.pose().scale(scale,-scale,scale);
            graphics.pose().mulPose(Axis.XP.rotationDegrees(25));graphics.pose().mulPose(Axis.YP.rotationDegrees(-35));
            graphics.pose().translate(0,-1.5,.7);Lighting.setupForEntityInInventory();
            entityRenderer.render(wagon,0,partial,graphics.pose(),graphics.bufferSource(),15728880);
            graphics.flush();graphics.pose().popPose();Lighting.setupFor3DItems();
        }
        private void verifyDrivingBones(GuiGraphics graphics) {
            var wagon=wagons.getFirst();var initial=wagon.pose();wagon.setSteering(.25F);wagon.addWheelAngle(0,-1.2F);wagon.addWheelAngle(1,-1.4F);
            wagon.applyPose(new com.sange.tm_wagon.physics.WagonPose(wagon.position(),213,.15F,.1F));
            var entityRenderer=(WagonRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(wagon);
            for(int i=0;i<2;i++) {
                drawWagon(graphics,wagon,-1000,-1000,1,1);
                if(Math.abs(entityRenderer.getGeoModel().getBone("front_axle").orElseThrow().getRotY()+.25F)>.001
                    ||Math.abs(entityRenderer.getGeoModel().getBone("front_left_wheel").orElseThrow().getRotX()+1.2F)>.001)
                    throw new IllegalStateException("Driving bones were not applied at draw time");
                drawAssembly(graphics,assemblies.getFirst(),-1000,-1000,1);
                if(Math.abs(renderer.getGeoModel().getBone("front_axle").orElseThrow().getRotY())>.001
                    ||Math.abs(renderer.getGeoModel().getBone("front_left_wheel").orElseThrow().getRotX())>.001)
                    throw new IllegalStateException("Driving rotations leaked into assembled block model");
            }
            wagon.setSteering(0);wagon.addWheelAngle(0,1.2F);wagon.addWheelAngle(1,1.4F);wagon.applyPose(initial);
            LogUtils.getLogger().info("TM_WAGON_DRIVING_RENDER_PASS: steering, wheel phase and same-tick block/entity cache isolation");
        }
        private void verifyEntityAndFrameVisibility(GuiGraphics graphics) {
            for(int i=0;i<4;i++) {
                var wagon=wagons.get(i);
                var entityRenderer=(WagonRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(wagon);
                drawWagon(graphics,wagon,-1000,-1000,1);
                if(!entityRenderer.getGeoModel().getBone("frame_root").orElseThrow().isHidden())throw new IllegalStateException("Entity renderer displayed lift");
                drawAssembly(graphics,assemblies.get(i),-1000,-1000,1);
                if(renderer.getGeoModel().getBone("frame_root").orElseThrow().isHidden())throw new IllegalStateException("Entity visibility leaked into block renderer");
                drawWagon(graphics,wagon,-1000,-1000,1);
                if(!entityRenderer.getGeoModel().getBone("frame_root").orElseThrow().isHidden())throw new IllegalStateException("Cached entity renderer inherited block lift");
            }
            LogUtils.getLogger().info("TM_WAGON_ENTITY_RENDER_PASS: all variants and shared-bone visibility verified");
        }
        private void save(String name) {
            try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                var dir=Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);image.writeToFile(dir.resolve(name));
            } catch(java.io.IOException e) {throw new IllegalStateException(e);}
        }
    }
}
