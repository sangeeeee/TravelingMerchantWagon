package com.sange.tm_wagon.client;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.*;
import com.sange.tm_wagon.cargo.*;
import com.sange.tm_wagon.material.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.Vec3;

/** Opt-in real client/server regression smoke, never included in published jars. */
public final class PortClientSmoke {
    private static boolean opened,started,done;
    private static volatile boolean passed;
    private static int ticks;
    public static void tick(net.minecraft.client.Minecraft ignored) {
        if(!Boolean.getBoolean("tm_wagon.portSmoke")||done)return;
        var mc=Minecraft.getInstance();if(mc.gui.overlay()!=null)return;
        if(!opened&&mc.gui.screen()!=null&&!(mc.gui.screen() instanceof TitleScreen)) { mc.gui.setScreen(new TitleScreen());return; }
        if(!opened&&mc.gui.screen() instanceof TitleScreen) {
            opened=true;mc.options.pauseOnLostFocus=false;
            var settings=new LevelSettings("26.3 wagon smoke",GameType.CREATIVE,new LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("wagon-smoke-"+System.currentTimeMillis(),settings,new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),
                access->access.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());return;
        }
        if(opened&&mc.level!=null&&mc.player!=null&&!started) {
            if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) {
                try { Class.forName("net.irisshaders.iris.shadows.ShadowRenderer"); }
                catch(ClassNotFoundException missing) { throw new IllegalStateException("Iris shadow bridge target missing",missing); }
            }
            for(var item:BuiltInRegistries.ITEM)if(BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("tm_wagon")) {
                var stack=new WagonMaterial(WoodMaterial.OAK,DyeColor.WHITE).stack(item);
                var name=stack.getHoverName().getString();
                if(name.contains("item.tm_wagon.")||name.contains("block.tm_wagon."))throw new IllegalStateException("Missing item localization: "+name);
            }
            com.mojang.logging.LogUtils.getLogger().info("TM_WAGON_26_3_LOCALIZATION_PASS: every registered wagon item has a translated name");
            started=true;var server=mc.getSingleplayerServer();var playerId=mc.player.getUUID();
            server.execute(()->{try {
                var player=server.getPlayerList().getPlayer(playerId);var level=player.level();var origin=player.blockPosition().offset(0,0,7);
                var variants=new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY};
                for(int i=0;i<3;i++) {
                    var pos=origin.offset(i*10,0,0);
                    level.setBlock(pos,WagonContent.FRAME.get().defaultBlockState(),3);
                    var frame=(AssemblyFrameBlockEntity)level.getBlockEntity(pos);frame.initializeFrame();
                    var parts=WagonEntity.defaultParts();parts.put(WagonSlot.BODY,variants[i]);
                    for(var slot:WagonSlot.values()) {
                        var part=parts.get(slot);var wood=slot==WagonSlot.BODY?WoodMaterial.POPLAR:slot==WagonSlot.SEAT?WoodMaterial.PALE_OAK:WoodMaterial.SPRUCE;
                        var stack=new WagonMaterial(wood,net.minecraft.world.item.DyeColor.BLUE).stack(WagonContent.PART_ITEMS.get(part).get());
                        var error=frame.install(slot,part,null,stack);if(error!=null)throw new IllegalStateException("Install "+slot+": "+error);
                    }
                    var w=WagonContent.WAGON.get().create(level,net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);w.configure(parts,Direction.NORTH);w.setMaterials(frame.materials());w.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5+8);w.setNoGravity(true);level.addFreshEntity(w);
                    player.setPos(w.position().add(0,3,0));
                    var stack=new ItemStack(Items.FURNACE);var error=w.cargo().place(0,stack,player);if(error!=null)throw new IllegalStateException("Cargo placement "+error);
                    var furnace=w.cargo().entry(0);furnace.inventory.setItem(0,new ItemStack(Items.IRON_ORE));furnace.inventory.setItem(1,new ItemStack(Items.COAL));
                    for(int t=0;t<210;t++)furnace.tick();
                    if(!furnace.inventory.getItem(2).is(Items.IRON_INGOT))throw new IllegalStateException("Cargo furnace did not cook");
                    var cargo=w.cargo().save(level.registryAccess(),false);w.cargo().load(cargo,level.registryAccess());
                    if(!w.cargo().entry(0).inventory.getItem(2).is(Items.IRON_INGOT))throw new IllegalStateException("Cargo persistence lost content");
                    w.cargo().load(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
                    var extra=new net.minecraft.nbt.CompoundTag();var accessory=new net.minecraft.nbt.CompoundTag();accessory.putBoolean("Installed",true);
                    if(i==0)extra.put("Cover",accessory);else extra.put("Canopy",accessory);
                    var cabinet=new net.minecraft.nbt.CompoundTag();cabinet.putBoolean("Installed",true);cabinet.putInt("Rows",6);cabinet.put("Material",new WagonMaterial(WoodMaterial.PALE_OAK,DyeColor.WHITE).save());extra.put("Cabinet",cabinet);w.cargo().load(extra,level.registryAccess());
                    w.cargo().place(0,new ItemStack(Items.CHEST),player);w.cargo().place(1,new ItemStack(WagonContent.STOOL.get()),player);
                    if(i==0) {
                        w.cargo().cover().load(new net.minecraft.nbt.CompoundTag());player.setPos(w.pose().point(new Vec3(0,3,4)));var matError=w.cargo().place(8,new ItemStack(Items.STRAW_BED),player);if(matError!=null)throw new IllegalStateException("Mat placement: "+matError);
                        var oldRespawn=player.getRespawnConfig();
                        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),"time set night");level.updateSkyBrightness();
                        player.setPos(w.cargo().position(w.cargo().entry(8)));var sleep=StrawMatSleep.sleep(w.cargo(),w.cargo().entry(8),player);
                        if(sleep!=null)throw new IllegalStateException("Sleep: "+sleep);
                        w.setPos(w.position().add(1,0,0));w.setYRot(45);StrawMatSleep.follow(player);
                        if(!player.isSleeping())throw new IllegalStateException("Moving sleep ended");
                        if(!java.util.Objects.equals(oldRespawn,player.getRespawnConfig()))throw new IllegalStateException("Mat changed respawn");player.stopSleepInBed(true,true);
                        var bedding=w.cargo().entry(8);
                        if(bedding==null||!bedding.item.is(Items.STRAW_BED))throw new IllegalStateException("Waking consumed wagon straw bed");
                        player.setPos(w.cargo().position(bedding));
                        var repeat=StrawMatSleep.sleep(w.cargo(),bedding,player);if(repeat!=null)throw new IllegalStateException("Repeated bed sleep: "+repeat);
                        player.stopSleepInBed(true,true);
                        if(w.cargo().entry(8)!=bedding)throw new IllegalStateException("Second wake consumed wagon bedding");
                        w.cargo().cover().load(accessory);
                    }
                    if(i==2) {
                        player.setPos(w.pose().point(new Vec3(0,CargoHold.FLOOR,0)));
                        var shelf=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("poplar_shelf")));
                        var shelfError=w.cargo().place(2,shelf,player);if(shelfError!=null)throw new IllegalStateException("Render shelf placement: "+shelfError);
                        w.cargo().entry(2).inventory.setItem(0,new ItemStack(Items.DIAMOND,9));
                        w.cargo().place(3,new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("oxidized_copper_chest"))),player);
                    }
                    w.cargoGeometryChanged();w.cargoChanged(true);
                }
                player.setPos(Vec3.atCenterOf(origin).add(2,4,-10));player.setXRot(15);player.setYRot(0);
                PortSmokeChecks.prepare(server,player,origin);
                level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),"time set day");
                passed=true;com.mojang.logging.LogUtils.getLogger().info("TM_WAGON_26_3_GAMEPLAY_PASS: 3 assembled sizes, cargo furnace, persistence, moving sleep/wake without respawn");
            } catch(Throwable error) { com.mojang.logging.LogUtils.getLogger().error("TM_WAGON_26_3_SMOKE_FAIL",error);mc.execute(mc::stop); }});return;
        }
        if(passed&&++ticks==80) {
            var server=mc.getSingleplayerServer();var playerId=mc.player.getUUID();
            server.execute(()->{var player=server.getPlayerList().getPlayer(playerId);var start=PortSmokeChecks.transfers.getFirst().frame().getBlockPos();player.getAbilities().flying=true;player.onUpdateAbilities();player.connection.teleport(start.getX()-3,start.getY()+5,start.getZ()+3,-135,43);});
        }
        if(passed&&ticks==120)mc.gui.setScreen(new ItemPreview());
        if(passed&&(ticks==70||ticks==110||ticks==160))net.minecraft.client.Screenshot.grab(mc.gameDirectory,ticks==70?"world.png":ticks==110?"accessories.png":"items.png",mc.gameRenderer.mainRenderTarget(),1,c->{});
        if(passed&&ticks==180) { done=true;com.mojang.logging.LogUtils.getLogger().info("TM_WAGON_26_3_RENDER_PASS: live wagons, assembly block entities and all component items");mc.stop(); }
    }
    public static void serverTick(net.minecraft.server.MinecraftServer server) { if(Boolean.getBoolean("tm_wagon.portSmoke"))PortSmokeChecks.tick(server); }
    static final class ItemPreview extends Screen {
        @Override public boolean isPauseScreen() { return false; }
        ItemPreview() { super(Component.literal("26.3 item renderer smoke")); }
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float partial) {
            int i=0;g.fill(0,0,width,height,0xffbbb4a8);
            g.item(WagonContent.FRAME_ITEM.get().getDefaultInstance(),20,20);
            for(var part:WagonPart.values())g.item(new WagonMaterial(WoodMaterial.SPRUCE,DyeColor.BLUE).stack(WagonContent.PART_ITEMS.get(part).get()),50+(i++%8)*28,20+(i/8)*32);
            for(var item:new Item[]{WagonContent.CABINET.get(),WagonContent.CANOPY.get(),WagonContent.CARGO_COVER.get(),WagonContent.STOOL.get(),Items.STRAW_BED})g.item(new ItemStack(item),50+(i++%8)*28,90+(i/8)*32);
            int row=0;
            for(var wood:new WoodMaterial[]{WoodMaterial.PALE_OAK,WoodMaterial.POPLAR}) {
                int column=0;
                for(var part:WagonPart.values())if(WagonMaterial.wooden(part))g.item(new WagonMaterial(wood,DyeColor.BLUE).stack(WagonContent.PART_ITEMS.get(part).get()),20+column++*24,210+row*26);
                for(var item:new Item[]{WagonContent.CABINET.get(),WagonContent.STOOL.get()})g.item(new WagonMaterial(wood,DyeColor.WHITE).stack(item),20+column++*24,210+row*26);
                row++;
            }
        }
    }
}
