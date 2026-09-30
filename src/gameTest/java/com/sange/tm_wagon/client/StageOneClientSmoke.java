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

    private static class PreviewScreen extends Screen {
        private int frames;
        private final List<ItemStack> items=new ArrayList<>();
        private final AssemblyRenderer renderer=new AssemblyRenderer();
        private final List<AssemblyFrameBlockEntity> assemblies=new ArrayList<>();
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
        }
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
            graphics.fill(0,0,width,height,0xffede6d7);
            graphics.drawCenteredString(font,"TravelingMerchantWagon / native renderer check",width/2,12,0xff463729);
            if(frames<25) {
                for(int i=0;i<items.size();i++) {
                    int x=width/6+(i%3)*width/3,y=60+(i/3)*100;
                    graphics.pose().pushPose();graphics.pose().translate(x-24,y,0);graphics.pose().scale(3,3,3);
                    graphics.renderItem(items.get(i),0,0);graphics.pose().popPose();
                    graphics.drawCenteredString(font,items.get(i).getHoverName(),x,y+55,0xff463729);
                }
            } else {
                for(int i=0;i<4;i++) drawAssembly(graphics,assemblies.get(i),width/4+(i%2)*width/2,110+(i/2)*170,23);
                // Render a body-only instance after full wagons, verifying shared
                // model visibility is reset rather than inherited from a neighbour.
                drawAssembly(graphics,assemblies.get(4),width/2,height-35,9);
            }
            graphics.flush();frames++;
            if(frames==15)save("stage-one-items.png");
            if(frames==40)save("stage-one-assemblies.png");
            if(frames==55) {LogUtils.getLogger().info("TM_WAGON_CLIENT_SMOKE_PASS: 9 items, 4 combinations and body-only rendered");Minecraft.getInstance().stop();}
        }
        private void drawAssembly(GuiGraphics graphics,AssemblyFrameBlockEntity frame,int x,int y,float scale) {
            graphics.pose().pushPose();graphics.pose().translate(x,y,500);
            graphics.pose().scale(scale,-scale,scale);graphics.pose().mulPose(Axis.XP.rotationDegrees(25));
            graphics.pose().mulPose(Axis.YP.rotationDegrees(-35));graphics.pose().translate(-.5,-1.5,.7);
            Lighting.setupForEntityInInventory();
            renderer.render(frame,0,graphics.pose(),graphics.bufferSource(),15728880,0);
            graphics.flush();graphics.pose().popPose();Lighting.setupFor3DItems();
        }
        private void save(String name) {
            try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                var dir=Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);image.writeToFile(dir.resolve(name));
            } catch(java.io.IOException e) {throw new IllegalStateException(e);}
        }
    }
}
