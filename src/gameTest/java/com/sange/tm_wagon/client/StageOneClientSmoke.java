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
            for(WagonPart part:WagonPart.values())if(part!=WagonPart.LONG_CARGO_BODY)items.add(WagonContent.PART_ITEMS.get(part).get().getDefaultInstance());
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
            } else if(frames<100) {
                for(int i=0;i<4;i++) {
                    drawWagon(graphics,wagons.get(i),width/4+(i%2)*width/2,105+(i/2)*175,25);
                    graphics.drawCenteredString(font,wagons.get(i).facing().name(),width/4+(i%2)*width/2,172+(i/2)*175,0xff463729);
                }
            } else {
                for(int i=0;i<2;i++) {
                    if(i==0)drawAssembly(graphics,assemblies.getFirst(),width/4,190,42);
                    else drawWagon(graphics,wagons.getFirst(),width*3/4,190,42,1);
                    graphics.drawCenteredString(font,i==0?"Block cargo":"Entity cargo",width/4+i*width/2,290,0xff463729);
                }
                graphics.drawCenteredString(font,frames>=330?"Covered cargo remains visible through the open tailgate":frames>=255?"Grey cargo cover / roll toward rear / reusable cloth meshes":frames>=230?"Extended cargo compartment / 12 slots / rear axle moved":frames>=205?"Oak cargo stool / one slot / half-block height":frames>=180?"Straw mat / three cargo slots / adjacent cargo":frames>=150?"Cargo workstations / book and decorated pot renderers":frames<125?"10 scaled cargo slots / closed containers":"Open chest + shulker / lowered tailgate",width/2,height-40,0xff463729);
                if(frames>=180) {
                    int x=width/2-24,y=40;graphics.fill(x,y,x+48,y+48,0xffd0c5af);
                    graphics.pose().pushPose();graphics.pose().translate(x,y,0);graphics.pose().scale(3,3,3);
                    graphics.renderItem(new ItemStack(frames>=255?WagonContent.CARGO_COVER.get():frames>=230?WagonContent.PART_ITEMS.get(WagonPart.LONG_CARGO_BODY).get():frames>=205?WagonContent.STOOL.get():WagonContent.STRAW_MAT.get()),0,0);graphics.pose().popPose();
                    graphics.renderItem(new ItemStack(frames>=255?WagonContent.CARGO_COVER.get():frames>=230?WagonContent.PART_ITEMS.get(WagonPart.LONG_CARGO_BODY).get():frames>=205?WagonContent.STOOL.get():WagonContent.STRAW_MAT.get()),width/2+30,y+16);
                    graphics.drawCenteredString(font,Component.translatable(frames>=255?"item.tm_wagon.wagon_cargo_cover":frames>=230?"block.tm_wagon.long_cargo_body":frames>=205?"item.tm_wagon.wagon_stool":"item.tm_wagon.wagon_straw_mat"),width/2,y+54,0xff463729);
                }
            }
            if(frames==64)verifyCachedFramePose(graphics);
            if(frames==80)verifyAudio();
            if(frames==90)verifyEntityAndFrameVisibility(graphics);
            if(frames==93)verifyDrivingBones(graphics);
            if(frames==94)verifyHarnessRopeModel();
            if(frames==99)loadCargoPreview(false);
            if(frames==124)loadCargoPreview(true);
            if(frames==140)verifyCargoBones(graphics);
            graphics.flush();frames++;
            if(frames==15)save("stage-one-items.png");
            if(frames==40)save("stage-one-assemblies.png");
            if(frames==65)save("folding-frame-states.png");
            if(frames==90)save("wagon-entity-variants.png");
            if(frames==120)save("wagon-cargo-closed.png");
            if(frames==145)save("wagon-cargo-open.png");
            if(frames==150)loadWorkBlockPreview();
            if(frames==175)save("wagon-work-blocks.png");
            if(frames==180)loadStrawMatPreview();
            if(frames==195)save("wagon-straw-mat.png");
            if(frames==205)loadStoolPreview();
            if(frames==220)save("wagon-stools.png");
            if(frames==230)loadExtendedCargoPreview(graphics);
            if(frames==245)save("wagon-extended-cargo.png");
            if(frames==255)loadCoverPreview(0);
            if(frames==270)save("wagon-cover-closed.png");
            if(frames==280)loadCoverPreview(2);
            if(frames==295)save("wagon-cover-partial.png");
            if(frames==305)loadCoverPreview(6);
            if(frames==320)save("wagon-cover-rolled.png");
            if(frames==330)loadCoveredTailgatePreview();
            if(frames==350)save("wagon-covered-cargo-tailgate.png");
            if(frames==365) {LogUtils.getLogger().info("TM_WAGON_CLIENT_SMOKE_PASS: cargo, work blocks, seats and standard/extended cargo covers in both forms");Minecraft.getInstance().stop();}
        }
        private void loadCoverPreview(int opened) {
            var registry=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
            var frame=assemblies.getFirst();var cargo=frame.cargo().save(registry,true);var cover=new CompoundTag();cover.putBoolean("Installed",true);cover.putInt("OpenRows",Math.min(5,opened));cargo.put("Cover",cover);
            var tag=new CompoundTag();tag.put("Modules",com.sange.tm_wagon.entity.WagonEntity.encode(com.sange.tm_wagon.entity.WagonEntity.defaultParts()));tag.put("Cargo",cargo);frame.loadWithComponents(tag,registry);
            var wagon=wagons.getFirst();cover=new CompoundTag();cover.putBoolean("Installed",true);cover.putInt("OpenRows",opened);
            cargo=wagon.cargo().save(registry,true);cargo.put("Cover",cover);wagon.cargo().load(cargo,registry);wagon.cargoGeometryChanged();
            var manager=Minecraft.getInstance().getModelManager();
            for(String name:new String[]{"cargo_cover_sheet","cargo_cover_roll","cargo_cover_back_hem"}) {
                var id=net.minecraft.client.resources.model.ModelResourceLocation.standalone(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tm_wagon","block/"+name));
                if(manager.getModel(id)==manager.getMissingModel())throw new IllegalStateException("Missing cover mesh: "+name);
            }
            if(wagon.cargo().cover().openRows()!=opened||!frame.cargo().cover().installed())throw new IllegalStateException("Cover preview lost state");
            LogUtils.getLogger().info("TM_WAGON_COVER_MODEL_PASS: standard={} and extended={} exposed rows, all three baked meshes loaded",frame.cargo().cover().openRows(),opened);
        }
        private void loadCoveredTailgatePreview() {
            loadCoverPreview(0);
            var registry=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
            var frame=assemblies.getFirst();var cargo=frame.cargo().save(registry,true);
            cargo.putBoolean("GateTarget",true);cargo.putBoolean("GateCollision",true);cargo.putLong("GateStart",Long.MIN_VALUE);
            var tag=new CompoundTag();tag.put("Modules",com.sange.tm_wagon.entity.WagonEntity.encode(com.sange.tm_wagon.entity.WagonEntity.defaultParts()));tag.put("Cargo",cargo);frame.loadWithComponents(tag,registry);
            var wagon=wagons.getFirst();cargo=wagon.cargo().save(registry,true);
            cargo.putBoolean("GateTarget",true);cargo.putBoolean("GateCollision",true);cargo.putLong("GateStart",Long.MIN_VALUE);
            wagon.cargo().load(cargo,registry);wagon.cargoGeometryChanged();
            if(!frame.cargo().gateOpen()||!wagon.cargo().gateOpen()||!wagon.cargo().cover().covered(10)||wagon.cargo().entry(10)==null)throw new IllegalStateException("Covered cargo preview lost rear container");
            LogUtils.getLogger().info("TM_WAGON_COVERED_CARGO_RENDER_PASS: full cloth and lowered tailgates, rear cargo in both forms");
        }
        private void loadExtendedCargoPreview(GuiGraphics graphics) {
            var registry=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
            var cargo=new CompoundTag();var entries=new net.minecraft.nbt.ListTag();
            for(int slot=0;slot<12;slot++) {
                var value=new CompoundTag();value.putInt("Slot",slot);value.putUUID("Id",java.util.UUID.randomUUID());
                var block=slot==10?net.minecraft.world.level.block.Blocks.CHEST:slot==11?net.minecraft.world.level.block.Blocks.OAK_PLANKS:slot%2==0?net.minecraft.world.level.block.Blocks.HAY_BLOCK:net.minecraft.world.level.block.Blocks.OAK_PLANKS;
                var item=slot==11?new ItemStack(WagonContent.STOOL.get()):new ItemStack(block);
                value.put("Item",item.save(registry));value.put("State",net.minecraft.nbt.NbtUtils.writeBlockState(block.defaultBlockState()));value.putBoolean("Visual",true);entries.add(value);
            }
            cargo.put("Entries",entries);
            var bodyOnly=new AssemblyFrameBlockEntity(new BlockPos(40,0,0),WagonContent.FRAME.get().defaultBlockState());
            var tag=new CompoundTag();var modules=new CompoundTag();modules.putString("BODY","LONG_CARGO_BODY");tag.put("Modules",modules);bodyOnly.loadWithComponents(tag,registry);
            drawAssembly(graphics,bodyOnly,-1000,-1000,1);
            for(int i=0;i<4;i++) {
                var parts=new java.util.EnumMap<WagonSlot,WagonPart>(WagonSlot.class);parts.putAll(wagons.get(i).parts());parts.put(WagonSlot.BODY,WagonPart.LONG_CARGO_BODY);
                wagons.get(i).configure(parts,Direction.NORTH);wagons.get(i).cargo().load(cargo,registry);wagons.get(i).cargoGeometryChanged();
                tag=new CompoundTag();tag.put("Modules",com.sange.tm_wagon.entity.WagonEntity.encode(parts));tag.put("Cargo",cargo);assemblies.get(i).loadWithComponents(tag,registry);
                drawWagon(graphics,wagons.get(i),-1000,-1000,1,1);drawAssembly(graphics,assemblies.get(i),-1000,-1000,1);
                if(wagons.get(i).cargo().capacity()!=12||wagons.get(i).cargo().entry(11)==null||assemblies.get(i).cargo().entry(10)==null)throw new IllegalStateException("Extended cargo render lost the final row");
            }
            try {
                var resource=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tm_wagon","geo/assembly/long_single_seat_single_horse.geo.json");
                try(var reader=Minecraft.getInstance().getResourceManager().getResourceOrThrow(resource).openAsReader()) {
                    var bones=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
                    for(var raw:bones) {
                        var bone=raw.getAsJsonObject();String name=bone.get("name").getAsString();
                        if(name.equals("rear_left_wheel")&&Math.abs(bone.getAsJsonArray("pivot").get(2).getAsDouble()-31.2)>.001)throw new IllegalStateException("Extended rear wheel pivot is wrong");
                        if(name.equals("tailgate")&&Math.abs(bone.getAsJsonArray("pivot").get(2).getAsDouble()-48.8)>.001)throw new IllegalStateException("Extended gate pivot is wrong");
                    }
                }
            }catch(java.io.IOException error){throw new IllegalStateException(error);}
            LogUtils.getLogger().info("TM_WAGON_EXTENDED_RENDER_PASS: eight block/entity combinations and body-only extended model, 12 slots, correct rear/gate pivots");
        }
        private void loadStoolPreview() {
            var registry=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
            var tag=new CompoundTag();var entries=new net.minecraft.nbt.ListTag();
            for(int slot:new int[]{0,1,4,5,8,9}) {
                var value=new CompoundTag();value.putInt("Slot",slot);value.putUUID("Id",java.util.UUID.randomUUID());
                var block=slot==9?net.minecraft.world.level.block.Blocks.CHEST:net.minecraft.world.level.block.Blocks.OAK_PLANKS;
                var item=slot==9?new ItemStack(block):new ItemStack(WagonContent.STOOL.get());
                value.put("Item",item.save(registry));value.put("State",net.minecraft.nbt.NbtUtils.writeBlockState(block.defaultBlockState()));value.putBoolean("Visual",true);entries.add(value);
            }
            tag.put("Entries",entries);assemblies.getFirst().cargo().load(tag,registry);wagons.getFirst().cargo().load(tag,registry);wagons.getFirst().cargoGeometryChanged();
            var model=Minecraft.getInstance().getItemRenderer().getModel(new ItemStack(WagonContent.STOOL.get()),null,null,0);
            if(model==Minecraft.getInstance().getModelManager().getMissingModel()||model.getQuads(null,null,net.minecraft.util.RandomSource.create()).isEmpty())throw new IllegalStateException("Missing stool model");
            LogUtils.getLogger().info("TM_WAGON_STOOL_RENDER_PASS: oak stool model baked, one slot, half-block collision, both host forms");
        }
        private void loadStrawMatPreview() {
            var registry=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
            var tag=new CompoundTag();var entries=new net.minecraft.nbt.ListTag();
            for(int slot:new int[]{8,5,9,2}) {
                var value=new CompoundTag();value.putInt("Slot",slot);value.putUUID("Id",java.util.UUID.randomUUID());
                var block=slot==8?net.minecraft.world.level.block.Blocks.HAY_BLOCK:slot==5?net.minecraft.world.level.block.Blocks.CHEST:net.minecraft.world.level.block.Blocks.OAK_PLANKS;
                var item=slot==8?new ItemStack(WagonContent.STRAW_MAT.get()):new ItemStack(block);
                value.put("Item",item.save(registry));value.put("State",net.minecraft.nbt.NbtUtils.writeBlockState(block.defaultBlockState()));value.putBoolean("Visual",true);entries.add(value);
            }
            tag.put("Entries",entries);assemblies.getFirst().cargo().load(tag,registry);wagons.getFirst().cargo().load(tag,registry);wagons.getFirst().cargoGeometryChanged();
            if(wagons.getFirst().cargo().entry(4)!=wagons.getFirst().cargo().entry(8)||wagons.getFirst().cargo().boxes().size()!=4)throw new IllegalStateException("Mat preview span or collision count is wrong");
            var model=Minecraft.getInstance().getItemRenderer().getModel(new ItemStack(WagonContent.STRAW_MAT.get()),null,null,0);
            if(model==Minecraft.getInstance().getModelManager().getMissingModel())throw new IllegalStateException("Missing straw mat model");
            LogUtils.getLogger().info("TM_WAGON_STRAW_MAT_RENDER_PASS: model baked, one anchor reserves three slots, both host forms render");
        }
        private void loadCargoPreview(boolean open) {
            var registry=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
            var tag=new CompoundTag();var entries=new net.minecraft.nbt.ListTag();
            var blocks=new net.minecraft.world.level.block.Block[]{net.minecraft.world.level.block.Blocks.CHEST,net.minecraft.world.level.block.Blocks.BLUE_SHULKER_BOX,
                net.minecraft.world.level.block.Blocks.BARREL,net.minecraft.world.level.block.Blocks.FURNACE,net.minecraft.world.level.block.Blocks.SMOKER,
                net.minecraft.world.level.block.Blocks.CRAFTING_TABLE,net.minecraft.world.level.block.Blocks.OAK_PLANKS,net.minecraft.world.level.block.Blocks.HAY_BLOCK,
                net.minecraft.world.level.block.Blocks.PUMPKIN,net.minecraft.world.level.block.Blocks.IRON_BLOCK};
            for(int i=0;i<blocks.length;i++) {
                var value=new CompoundTag();value.putInt("Slot",i);value.putUUID("Id",java.util.UUID.randomUUID());
                value.put("Item",new ItemStack(blocks[i]).save(registry));value.put("State",net.minecraft.nbt.NbtUtils.writeBlockState(blocks[i].defaultBlockState()));
                value.putBoolean("Visual",true);value.putBoolean("Opened",open&&i<3);value.putLong("LidStart",Long.MIN_VALUE);entries.add(value);
            }
            tag.put("Entries",entries);tag.putBoolean("GateTarget",open);tag.putBoolean("GateCollision",open);tag.putLong("GateStart",Long.MIN_VALUE);
            assemblies.getFirst().cargo().load(tag,registry);wagons.getFirst().cargo().load(tag,registry);wagons.getFirst().cargoGeometryChanged();
        }
        private void loadWorkBlockPreview() {
            var registry=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
            var tag=new CompoundTag();var entries=new net.minecraft.nbt.ListTag();
            var blocks=new net.minecraft.world.level.block.Block[]{net.minecraft.world.level.block.Blocks.ENDER_CHEST,net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE,
                net.minecraft.world.level.block.Blocks.LECTERN,net.minecraft.world.level.block.Blocks.DECORATED_POT,net.minecraft.world.level.block.Blocks.WATER_CAULDRON,
                net.minecraft.world.level.block.Blocks.COMPOSTER,net.minecraft.world.level.block.Blocks.CHISELED_BOOKSHELF,net.minecraft.world.level.block.Blocks.BREWING_STAND,
                net.minecraft.world.level.block.Blocks.ANVIL,net.minecraft.world.level.block.Blocks.BLAST_FURNACE};
            for(int i=0;i<blocks.length;i++) {
                var value=new CompoundTag();value.putInt("Slot",i);value.putUUID("Id",java.util.UUID.randomUUID());var state=blocks[i].defaultBlockState();
                if(i==2)state=state.setValue(net.minecraft.world.level.block.LecternBlock.HAS_BOOK,true);
                if(i==4)state=state.setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL,3);
                if(i==5)state=state.setValue(net.minecraft.world.level.block.ComposterBlock.LEVEL,8);
                if(i==6)state=state.setValue(net.minecraft.world.level.block.ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(1),true);
                if(i==7)state=state.setValue(net.minecraft.world.level.block.BrewingStandBlock.HAS_BOTTLE[0],true);
                value.put("Item",new ItemStack(blocks[i]).save(registry));value.put("State",net.minecraft.nbt.NbtUtils.writeBlockState(state));
                value.putBoolean("Visual",true);value.putBoolean("Opened",true);value.putLong("LidStart",Long.MIN_VALUE);entries.add(value);
            }
            tag.put("Entries",entries);tag.putLong("GateStart",Long.MIN_VALUE);
            assemblies.getFirst().cargo().load(tag,registry);wagons.getFirst().cargo().load(tag,registry);wagons.getFirst().cargoGeometryChanged();
        }
        private void verifyCargoBones(GuiGraphics graphics) {
            var wagon=wagons.getFirst();var entityRenderer=(WagonRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(wagon);
            drawWagon(graphics,wagon,-1000,-1000,1,1);
            if(Math.abs(entityRenderer.getGeoModel().getBone("tailgate").orElseThrow().getRotX()-Math.PI)>.001)
                throw new IllegalStateException("Entity tailgate pose was not applied");
            drawAssembly(graphics,assemblies.get(1),-1000,-1000,1);
            if(Math.abs(renderer.getGeoModel().getBone("tailgate").orElseThrow().getRotX())>.001)
                throw new IllegalStateException("Open tailgate pose leaked into neighbouring block wagon");
            for(var entry:java.util.List.of(wagon.cargo().entry(0),wagon.cargo().entry(1)))if(entry.lid(1)!=1)
                throw new IllegalStateException("Container lid did not remain open");
            LogUtils.getLogger().info("TM_WAGON_CARGO_RENDER_PASS: ten slots in both forms, open chest/shulker, tailgate bone cache isolation");
        }
        private void verifyAudio() {
            try(var stream=new net.minecraft.client.sounds.JOrbisAudioStream(StageOneClientSmoke.class.getResourceAsStream("/assets/tm_wagon/sounds/wagon_roll.ogg"))) {
                if(stream.getFormat().getChannels()!=1 || stream.read(8192).remaining()==0)throw new IllegalStateException("Invalid wagon OGG");
                LogUtils.getLogger().info("TM_WAGON_AUDIO_PASS: mono OGG decoded, {} Hz",stream.getFormat().getSampleRate());
            } catch(java.io.IOException error) { throw new IllegalStateException(error); }
        }
        private void drawAssembly(GuiGraphics graphics,AssemblyFrameBlockEntity frame,int x,int y,float scale) {
            graphics.pose().pushPose();graphics.pose().translate(x,y,500);
            graphics.pose().scale(scale,-scale,scale);graphics.pose().mulPose(Axis.XP.rotationDegrees(frames>=330?2:25));
            graphics.pose().mulPose(Axis.YP.rotationDegrees(frames>=330?-8:-35));
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
            graphics.pose().mulPose(Axis.XP.rotationDegrees(frames>=330?2:25));graphics.pose().mulPose(Axis.YP.rotationDegrees(frames>=330?-8:-35));
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
        private void verifyHarnessRopeModel() {
            for(var wagon:wagons) {
                var entityRenderer=(WagonRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(wagon);
                var resource=entityRenderer.getGeoModel().getModelResource(wagon);
                var point=wagon.pose().local(wagon.getRopeHoldPosition(1)).scale(16);
                boolean onBeam=false;
                try(var reader=Minecraft.getInstance().getResourceManager().getResourceOrThrow(resource).openAsReader()) {
                    var geometry=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
                    for(var entry:geometry.getAsJsonArray("bones")) {
                        var bone=entry.getAsJsonObject();if(!bone.get("name").getAsString().equals("shafts"))continue;
                        for(var cube:bone.getAsJsonArray("cubes")) {
                            var origin=cube.getAsJsonObject().getAsJsonArray("origin");var size=cube.getAsJsonObject().getAsJsonArray("size");
                            double x=origin.get(0).getAsDouble(),y=origin.get(1).getAsDouble(),z=origin.get(2).getAsDouble();
                            if(x<0&&x+size.get(0).getAsDouble()>0&&Math.abs(point.z-z)<.001
                                &&point.y>=y&&point.y<=y+size.get(1).getAsDouble()&&size.get(2).getAsDouble()<4)onBeam=true;
                        }
                    }
                } catch(java.io.IOException error) { throw new IllegalStateException("Could not inspect rendered shaft model",error); }
                if(!onBeam)throw new IllegalStateException("Leash endpoint misses rendered front wood beam: "+resource);
            }
            LogUtils.getLogger().info("TM_WAGON_HARNESS_RENDER_PASS: leash endpoint matches the wooden beam in all four rendered models");
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
