package com.sange.tm_wagon.client;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.cargo.*;
import com.sange.tm_wagon.entity.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** In-world ownership and compatibility regression checks, excluded from the mod jar. */
final class PortSmokeChecks {
    record Transfer(AssemblyFrameBlockEntity frame,WagonEntity wagon,Map<WagonSlot,com.sange.tm_wagon.material.WagonMaterial> materials) {}
    static final List<Transfer> transfers=new ArrayList<>();
    static long due;
    static int stage;
    static void require(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }
    static void prepare(MinecraftServer server,ServerPlayer player,BlockPos origin) {
        var level=player.level();
        require(WagonContent.FRAME_ITEM.get().getDescriptionId().equals("block.tm_wagon.wagon_assembly_frame"),"Assembly jack translation prefix");
        for(var part:WagonPart.values())require(WagonContent.PART_ITEMS.get(part).get().getDescriptionId().equals("block.tm_wagon."+part.id),"Component translation prefix "+part);
        require(BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("tm_wagon","wagon_straw_mat")),"Wagon straw mat is registered");
        var crafting=net.minecraft.world.item.crafting.CraftingInput.of(3,3,List.of(
            new ItemStack(Items.SPRUCE_PLANKS),new ItemStack(Items.SPRUCE_PLANKS),new ItemStack(Items.SPRUCE_PLANKS),
            new ItemStack(Items.SPRUCE_PLANKS),new ItemStack(Items.STRIPPED_SPRUCE_LOG),new ItemStack(Items.SPRUCE_TRAPDOOR),
            new ItemStack(Items.SPRUCE_PLANKS),new ItemStack(Items.SPRUCE_PLANKS),new ItemStack(Items.SPRUCE_PLANKS)));
        var recipe=level.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,crafting,level).orElseThrow();
        var crafted=recipe.value().assemble(crafting);
        require(crafted.is(WagonContent.PART_ITEMS.get(WagonPart.CARGO_BODY).get())&&com.sange.tm_wagon.material.WagonMaterial.of(crafted).wood()==com.sange.tm_wagon.material.WoodMaterial.SPRUCE,"Material-sensitive crafting recipe");
        for(int i=0;i<3;i++) {
            var frame=(AssemblyFrameBlockEntity)level.getBlockEntity(origin.offset(i*10,0,0));
            player.setPos(frame.cargoPose().point(new Vec3(0,3,0)));
            require(frame.cargo().place(0,new ItemStack(Items.CHEST),player)==null,"Frame cargo placement");
            frame.cargo().entry(0).inventory.setItem(0,new ItemStack(Items.DIAMOND,4));
            require(frame.cargo().place(1,new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("pale_oak_shelf"))),player)==null,"Frame shelf placement");
            frame.cargo().entry(1).inventory.setItem(2,new ItemStack(Items.APPLE,5));
            player.setPos(frame.cargoPose().point(new Vec3(0,3,4)));
            int bedAnchor=frame.cargo().capacity()-2;
            var bedError=frame.cargo().place(bedAnchor,new ItemStack(WagonContent.STRAW_MAT.get()),player);require(bedError==null,"Block straw bed placement "+i+": "+bedError);
            require(frame.cargo().entry(bedAnchor-2*frame.cargo().columns())==frame.cargo().entry(bedAnchor)&&frame.cargo().entry(bedAnchor-frame.cargo().columns())==frame.cargo().entry(bedAnchor),"Block bed reserves three cells");
            player.setPos(Vec3.atCenterOf(origin).add(-8,4,-9));
            var materials=frame.materials();
            require(frame.toggleFrame(null)==null,"Block to entity assembly");
            var wagon=level.getEntitiesOfClass(WagonEntity.class,new net.minecraft.world.phys.AABB(frame.getBlockPos()).inflate(1)).stream()
                .filter(w->w.position().distanceToSqr(Vec3.atBottomCenterOf(frame.getBlockPos()))<1).findFirst().orElseThrow();
            require(wagon.cargo().entry(0).inventory.getItem(0).getCount()==4&&frame.cargo().entry(0)==null,"Single inventory owner after entity conversion");
            require(wagon.cargo().entry(1).inventory.getItem(2).getCount()==5&&frame.cargo().entry(1)==null,"Shelf inventory owner after entity conversion");
            require(wagon.cargo().entry(bedAnchor).item.is(WagonContent.STRAW_MAT.get())&&frame.cargo().entry(bedAnchor)==null,"Straw bed single owner after assembly");
            require(wagon.materials().equals(materials),"Materials preserved after assembly");
            transfers.add(new Transfer(frame,wagon,materials));
        }
        var wagon=WagonContent.WAGON.get().create(level,EntitySpawnReason.TRIGGERED);
        wagon.configure(WagonEntity.defaultParts(),Direction.NORTH);
        wagon.setPos(Vec3.atBottomCenterOf(origin.offset(40,0,20)));level.addFreshEntity(wagon);
        player.setPos(wagon.pose().point(new Vec3(0,3,0)));
        workstations(wagon,player);
        newerCargo(wagon,player);
        strawBeds(wagon,player);
        player.setPos(wagon.pose().point(new Vec3(0,3,0)));
        optionalCargo(wagon,player);
        player.setPos(wagon.pose().point(new Vec3(0,3,-2)));
        require(player.startRiding(wagon),"Driver boarding");
        for(int slot=0;slot<wagon.horseCapacity();slot++) {
            var horse=net.minecraft.world.entity.EntityTypes.MULE.create(level,EntitySpawnReason.TRIGGERED);
            horse.setAge(-24000);require(!HorseHarness.eligible(horse),"Baby mule cannot pull a wagon");
            horse.setAge(0);horse.setPos(wagon.horsePosition(slot).add(2,0,0));level.addFreshEntity(horse);horse.setLeashedTo(player,true);
            require(HorseHarness.eligible(horse)&&wagon.attachHorse(player,horse,slot)==null,"Adult mule hitching");
            require(HorseHarness.owner(horse)==wagon&&horse.getLeashHolder()==wagon,"Mule harness ownership");
        }
        var start=wagon.position();
        for(int t=0;t<35;t++) {wagon.acceptInput(player,1,t>15?1:0,t>10);wagon.tick();}
        require(wagon.position().distanceToSqr(start)>.3,"Powered forward motion and steering");
        require(!wagon.horse(0).isEating(),"Pulling horse eating animation");
        player.stopRiding();
        var mule=wagon.horse(0);var muleId=mule.getUUID();
        var leadArea=wagon.getBoundingBox().inflate(15);
        int beforeRefund=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,leadArea).stream().filter(e->e.getItem().is(Items.LEAD)).mapToInt(e->e.getItem().getCount()).sum();
        wagon.detachHorse(muleId,true);wagon.detachHorse(muleId,true);
        int afterRefund=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,leadArea).stream().filter(e->e.getItem().is(Items.LEAD)).mapToInt(e->e.getItem().getCount()).sum();
        require(afterRefund-beforeRefund==1&&!HorseHarness.attached(mule)&&!mule.isNoGravity(),"Mule detachment refunds one lead and restores movement");
        wagon.detachAllHorses();wagon.discard();
        com.mojang.logging.LogUtils.getLogger().info("TM_WAGON_26_2_MULE_PASS: juvenile rejection, adult hitching, driving and single lead refund");
        camelDraft(player,origin);
        player.connection.teleport(origin.getX()-6,origin.getY()+5,origin.getZ()-9,-40,20);
        due=level.getGameTime()+24;stage=1;
    }
    static void camelDraft(ServerPlayer player,BlockPos origin) {
        var level=player.level();
        var wagon=WagonContent.WAGON.get().create(level,EntitySpawnReason.TRIGGERED);
        var parts=new EnumMap<WagonSlot,WagonPart>(WagonEntity.defaultParts());parts.put(WagonSlot.SHAFTS,WagonPart.DOUBLE_HORSE_SHAFTS);
        wagon.configure(parts,Direction.NORTH);wagon.setPos(Vec3.atBottomCenterOf(origin.offset(40,0,20)));level.addFreshEntity(wagon);
        player.setPos(wagon.pose().point(new Vec3(0,3,-2)));require(player.startRiding(wagon),"Camel driver boarding");
        var camels=new ArrayList<net.minecraft.world.entity.animal.camel.Camel>();
        for(int slot=0;slot<2;slot++) {
            var camel=net.minecraft.world.entity.EntityTypes.CAMEL.create(level,EntitySpawnReason.TRIGGERED);camel.setNoAi(true);
            camel.setAge(-100);require(!HorseHarness.eligible(camel),"Baby camel cannot pull");
            camel.setAge(0);camel.setPos(wagon.horsePosition(slot));level.addFreshEntity(camel);camel.setLeashedTo(player,true);
            camel.sitDown();require(camel.isCamelSitting(),"Unhitched camel can sit");
            require(wagon.attachHorse(player,camel,slot)==null,"Adult camel hitching");
            camel.sitDown();require(!camel.isCamelSitting()&&camel.getPose()==net.minecraft.world.entity.Pose.STANDING,"Hitched camel remains standing");
            Vec3 before=camel.position();camel.travel(new Vec3(0,0,1));require(camel.position().distanceToSqr(before)<1e-9,"Camel cannot travel independently");
            camels.add(camel);
        }
        var start=wagon.position();for(int tick=0;tick<25;tick++){wagon.acceptInput(player,1,tick>10?1:0);wagon.tick();}
        require(wagon.readyToPull()&&wagon.position().distanceToSqr(start)>.3,"Camels drive and turn the wagon");
        int before=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,wagon.getBoundingBox().inflate(15)).stream().filter(e->e.getItem().is(Items.LEAD)).mapToInt(e->e.getItem().getCount()).sum();
        wagon.detachAllHorses();wagon.detachAllHorses();
        int after=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,wagon.getBoundingBox().inflate(15)).stream().filter(e->e.getItem().is(Items.LEAD)).mapToInt(e->e.getItem().getCount()).sum();
        require(after-before==2,"Camel leads refund exactly once");
        for(var camel:camels){require(!HorseHarness.attached(camel)&&!camel.isNoGravity(),"Camel release restores movement");camel.sitDown();require(camel.isCamelSitting(),"Released camel can sit");camel.discard();}
        player.stopRiding();wagon.discard();
        com.mojang.logging.LogUtils.getLogger().info("TM_WAGON_26_2_CAMEL_PASS: juvenile rejection, hitching, standing, driving and lead refunds");
    }
    static void workstations(WagonEntity wagon,ServerPlayer player) {
        var hold=wagon.cargo();var lookup=player.level().registryAccess();
        for(var item:new Item[]{Items.CHEST,Items.BARREL,Items.SHULKER_BOX,Items.FURNACE,Items.SMOKER,Items.BLAST_FURNACE,Items.BREWING_STAND,
                Items.ENDER_CHEST,Items.CRAFTING_TABLE,Items.CARTOGRAPHY_TABLE,Items.STONECUTTER,Items.ANVIL,Items.SMITHING_TABLE,Items.LOOM,Items.GRINDSTONE,Items.ENCHANTING_TABLE}) {
            require(hold.place(0,new ItemStack(item),player)==null,"Workstation placement: "+item);
            var entry=hold.entry(0);CargoMenus.open(hold,entry,player);
            require(player.containerMenu!=player.inventoryMenu,"Workstation menu: "+item);player.closeContainer();
            if(item==Items.BREWING_STAND) {
                var water=new ItemStack(Items.POTION);water.set(DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.WATER));
                entry.inventory.setItem(0,water);entry.inventory.setItem(3,new ItemStack(Items.NETHER_WART));entry.inventory.setItem(4,new ItemStack(Items.BLAZE_POWDER));
                for(int tick=0;tick<405;tick++)entry.tick();
                require(entry.inventory.getItem(0).get(DataComponents.POTION_CONTENTS).is(net.minecraft.world.item.alchemy.Potions.AWKWARD),"Brewing cargo recipe");
            } else if(item==Items.FURNACE||item==Items.SMOKER||item==Items.BLAST_FURNACE) {
                entry.inventory.setItem(0,new ItemStack(item==Items.SMOKER?Items.BEEF:Items.IRON_ORE));
                entry.inventory.setItem(1,new ItemStack(Items.COAL));
                for(int tick=0;tick<50;tick++)entry.tick();
                require(entry.cook>0&&entry.burn>0,"Native cooking starts "+item);
                int cook=entry.cook,burn=entry.burn;
                hold.load(hold.save(lookup,false),lookup);entry=hold.entry(0);
                require(entry.cook==cook&&entry.burn==burn,"Cooking progress persistence "+item);
                for(int tick=0;tick<160;tick++)entry.tick();
                require(entry.inventory.getItem(2).is(item==Items.SMOKER?Items.COOKED_BEEF:Items.IRON_INGOT),"Native cooking finishes "+item);
                require(entry.totalCook==(item==Items.FURNACE?200:100),"Native cooking duration "+item);
            }
            hold.load(new net.minecraft.nbt.CompoundTag(),lookup);
        }
        com.mojang.logging.LogUtils.getLogger().info("TM_WAGON_26_2_WORKSTATIONS_PASS: 16 native menus, brewing, furnace/smoker/blast furnace and progress persistence");
    }
    static void newerCargo(WagonEntity wagon,ServerPlayer player) {
        var hold=wagon.cargo();var lookup=player.level().registryAccess();
        for(var wood:new com.sange.tm_wagon.material.WoodMaterial[]{com.sange.tm_wagon.material.WoodMaterial.PALE_OAK,com.sange.tm_wagon.material.WoodMaterial.SPRUCE}) {
            var p=new ItemStack(wood.planks());
            var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,List.of(p,p,p,p,new ItemStack(wood.stripped()),new ItemStack(wood.trapdoor()),p,p,p));
            var recipe=player.level().recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,player.level()).orElseThrow();
            var result=recipe.value().assemble(input);
            require(result.is(WagonContent.PART_ITEMS.get(WagonPart.CARGO_BODY).get())&&com.sange.tm_wagon.material.WagonMaterial.of(result).wood()==wood,"New wood crafting "+wood);
        }
        for(var prefix:new String[]{"","exposed_","weathered_","oxidized_","waxed_","waxed_exposed_","waxed_weathered_","waxed_oxidized_"}) {
            var stack=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(prefix+"copper_chest")));
            require(hold.place(0,stack,player)==null,"Copper chest placement "+prefix);
            var entry=hold.entry(0);require(entry.kind==CargoEntry.Kind.CHEST,"Copper chest native storage "+prefix);
            CargoMenus.open(hold,entry,player);require(player.containerMenu!=player.inventoryMenu,"Copper chest menu "+prefix);player.closeContainer();
            entry.inventory.setItem(0,new ItemStack(Items.EMERALD,13));hold.load(hold.save(lookup,false),lookup);
            require(hold.entry(0).inventory.getItem(0).getCount()==13,"Copper chest persistence "+prefix);
            hold.load(new net.minecraft.nbt.CompoundTag(),lookup);
        }
        var copper=builtinItem("copper_chest");
        copper.set(DataComponents.LOCK,new net.minecraft.world.LockCode(net.minecraft.advancements.predicates.ItemPredicate.Builder.item()
            .of(lookup.lookupOrThrow(net.minecraft.core.registries.Registries.ITEM),Items.TRIPWIRE_HOOK).build()));
        require("message.tm_wagon.cargo_container_protected".equals(hold.place(0,copper,player)),"Modern copper chest lock is protected");
        copper=builtinItem("copper_chest");
        copper.set(DataComponents.CONTAINER_LOOT,new net.minecraft.world.item.component.SeededContainerLoot(
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,Identifier.withDefaultNamespace("chests/simple_dungeon")),1));
        require("message.tm_wagon.cargo_container_protected".equals(hold.place(0,copper,player)),"Modern copper chest pending loot is protected");
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var shelfWoods=new ArrayList<String>();for(var wood:com.sange.tm_wagon.material.WoodMaterial.values())shelfWoods.add(wood.getSerializedName());shelfWoods.add("bamboo");
        for(var wood:shelfWoods) {
            var stack=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(wood+"_shelf")));
            require(hold.place(0,stack,player)==null,"Shelf placement "+wood);
            var entry=hold.entry(0);require(entry.kind==CargoEntry.Kind.SHELF&&entry.inventory.getContainerSize()==3,"Shelf adapter "+wood);
            entry.state=entry.state.setValue(net.minecraft.world.level.block.ShelfBlock.FACING,Direction.NORTH);
            var hit=hold.centreAt(0).add(0,CargoHold.SCALE/2,-CargoHold.SCALE/2);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND,9));
            CargoWorkBlocks.interact(hold,entry,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            require(player.getMainHandItem().isEmpty()&&entry.inventory.getItem(1).getCount()==9,"Shelf whole-stack insertion "+wood);
            var replacement=new ItemStack(Items.SHULKER_BOX);replacement.set(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new ItemStack(Items.APPLE,4))));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,replacement);
            CargoWorkBlocks.interact(hold,entry,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            require(player.getMainHandItem().is(Items.DIAMOND)&&player.getMainHandItem().getCount()==9,"Shelf whole-stack retrieval "+wood);
            var visual=CargoEntry.load(hold,entry.save(lookup,true),lookup);
            require(visual.inventory.getItem(1).is(Items.SHULKER_BOX)&&visual.inventory.getItem(1).getOrDefault(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.EMPTY).nonEmptyItemCopyStream().findAny().isEmpty(),"Shelf render packet excludes nested contents");
            hold.load(hold.save(lookup,false),lookup);entry=hold.entry(0);
            require(entry.inventory.getItem(1).get(DataComponents.CONTAINER).nonEmptyItemCopyStream().findFirst().orElseThrow().getCount()==4,"Shelf nested container persistence");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            CargoWorkBlocks.interact(hold,entry,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit.add(0,0,CargoHold.SCALE));
            require(player.getMainHandItem().isEmpty()&&!entry.inventory.getItem(1).isEmpty(),"Shelf rear face does not swap");
            CargoWorkBlocks.interact(hold,entry,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            require(entry.inventory.isEmpty()&&player.getMainHandItem().get(DataComponents.CONTAINER).nonEmptyItemCopyStream().findFirst().orElseThrow().getCount()==4,"Shelf exact retrieval with nested contents");
            hold.load(new net.minecraft.nbt.CompoundTag(),lookup);
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        if(ModList.get().isLoaded("carryon")) {
            var shelf=builtinItem("pale_oak_shelf");
            require(hold.place(0,shelf,player)==null,"Shelf Carry On placement");
            var entry=hold.entry(0);entry.inventory.setItem(1,new ItemStack(Items.EMERALD,11));
            var data=tschipp.carryon.common.carry.CarryOnDataManager.getCarryData(player);data.clear();data.setTick(-1);data.setKeyPressed(true);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);
            if(tschipp.carryon.common.config.ListHandler.isPermitted(entry.state.getBlock())) {
                require(CarryOnCargo.pickup(hold,entry,player)&&hold.entry(0)==null&&data.isCarrying(),"Shelf Carry On pickup");
                var carried=(net.minecraft.world.level.block.entity.ShelfBlockEntity)data.getBlockEntity(net.minecraft.core.BlockPos.containing(wagon.position()),lookup);
                require(carried.getItem(1).getCount()==11,"Shelf Carry On inventory");data.setTick(-1);
                require(CarryOnCargo.place(hold,0,player)&&!data.isCarrying()&&hold.entry(0).inventory.getItem(1).getCount()==11,"Shelf Carry On re-placement");
            }
            data.clear();hold.load(new net.minecraft.nbt.CompoundTag(),lookup);
        }
        require(hold.place(0,builtinItem("pale_oak_shelf"),player)==null,"Shelf removal placement");
        hold.entry(0).inventory.setItem(2,new ItemStack(Items.NETHER_STAR,7));
        require(hold.entry(0).returnedItem().getOrDefault(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.EMPTY).nonEmptyItemCopyStream().findAny().isEmpty(),"Shelf returned item does not duplicate cargo inventory");
        var area=com.sange.tm_wagon.physics.OrientedBox.at(hold.slotBounds(0).inflate(3),hold.owner().cargoPose()).bounds();
        var previous=player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).stream().filter(i->i.getItem().is(Items.NETHER_STAR)).mapToInt(i->i.getItem().getCount()).sum();
        require(hold.take(0,player)==null&&hold.entry(0)==null,"Shelf cargo removal");
        var dropped=player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).stream().filter(i->i.getItem().is(Items.NETHER_STAR)).mapToInt(i->i.getItem().getCount()).sum();
        require(dropped-previous==7,"Shelf contents drop exactly once");
        require(CargoHold.allowed(new ItemStack(WagonContent.STRAW_MAT.get())),"Wagon straw mats supported as three-slot bedding");
        require(!CargoHold.allowed(new ItemStack(Blocks.BED.pick(DyeColor.WHITE)))&&!CargoHold.allowed(new ItemStack(Items.OAK_DOOR)),"Ordinary beds and doors remain unsupported");
        com.mojang.logging.LogUtils.getLogger().info("TM_WAGON_26_2_NEW_CARGO_PASS: wood recipes, 8 copper chests, 12 shelf materials, swaps, persistence and bounded visual data");
    }
    static void strawBeds(WagonEntity wagon,ServerPlayer player) {
        var hold=wagon.cargo();var lookup=player.level().registryAccess();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var stack=new ItemStack(WagonContent.STRAW_MAT.get(),2);
        stack.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Test bedding"));
        player.setPos(wagon.pose().point(new Vec3(0,3,-4)));
        require(hold.place(0,stack,player)==null&&stack.getCount()==1,"Front placement consumes exactly one wagon mat");
        var bed=hold.entry(0);require(bed==hold.entry(2)&&bed==hold.entry(4)&&bed.reversed&&bed.footprintRows()==3&&hold.occupiedSlots()==3,"Front placement shares three reserved cells");
        require("message.tm_wagon.cargo_occupied".equals(hold.place(2,new ItemStack(Items.STONE),player)),"Reserved bedding cell rejects other cargo");
        require("message.tm_wagon.mat_space".equals(hold.place(8,stack,player))&&stack.getCount()==1,"Insufficient bed space preserves source item");
        hold.load(hold.save(lookup,false),lookup);bed=hold.entry(2);
        require(bed.reversed&&bed.item.is(WagonContent.STRAW_MAT.get())&&bed.item.getHoverName().getString().equals("Test bedding"),"Bed save/load retains direction and item components");
        long before=player.getInventory().countItem(WagonContent.STRAW_MAT.get());
        require(hold.take(2,player)==null&&hold.occupiedSlots()==0,"Bed removed through its middle cell");
        require(player.getInventory().countItem(WagonContent.STRAW_MAT.get())==before+1,"Bed removal returns one native item");
        player.setPos(wagon.pose().point(new Vec3(0,3,4)));
        require(hold.place(8,stack,player)==null&&stack.isEmpty(),"Rear placement consumes remaining bed");
        bed=hold.entry(8);require(!bed.reversed&&bed==hold.entry(6)&&bed==hold.entry(4),"Rear placement uses the opposite sleeping direction");
        var area=com.sange.tm_wagon.physics.OrientedBox.at(hold.matBounds(8).inflate(3),wagon.pose()).bounds();
        long droppedBefore=player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).stream().filter(e->e.getItem().is(WagonContent.STRAW_MAT.get())).mapToInt(e->e.getItem().getCount()).sum();
        hold.destroy(true);hold.destroy(true);
        long droppedAfter=player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).stream().filter(e->e.getItem().is(WagonContent.STRAW_MAT.get())).mapToInt(e->e.getItem().getCount()).sum();
        require(droppedAfter-droppedBefore==1,"Wrecked bedding drops once as a wagon straw mat");
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        com.mojang.logging.LogUtils.getLogger().info("TM_WAGON_26_2_STRAW_MAT_PASS: both directions, three cells, consumption, save/load, removal and one-time destruction drop");
    }
    private static ItemStack builtinItem(String id) { return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id))); }
    static void tick(MinecraftServer server) {
        if(stage==0||server.overworld().getGameTime()<due)return;
        try {
            if(stage==1) {
                for(var transfer:transfers) {
                    require(!transfer.frame.switching()&&!transfer.frame.extended(),"Assembly animation completion");
                    require(transfer.frame.toggleFrame(null)==null,"Entity to block restoration start");
                }
                stage=2;due=server.overworld().getGameTime()+24;
            } else {
                for(var transfer:transfers) {
                    require(transfer.wagon.isRemoved()&&AssemblyFrameBlockEntity.complete(transfer.frame.parts()),"Restoration completion");
                    require(transfer.frame.cargo().entry(0).inventory.getItem(0).getCount()==4,"Restored cargo contents");
                    require(transfer.frame.cargo().entry(1).inventory.getItem(2).getCount()==5,"Restored shelf contents");
                    require(transfer.frame.cargo().entry(transfer.frame.cargo().capacity()-2).item.is(WagonContent.STRAW_MAT.get())&&transfer.frame.cargo().entry(transfer.frame.cargo().capacity()-2-2*transfer.frame.cargo().columns())==transfer.frame.cargo().entry(transfer.frame.cargo().capacity()-2),"Restored wagon matding and reserved cells");
                    require(transfer.frame.materials().equals(transfer.materials),"Restored mixed materials");
                }
                stage=0;com.mojang.logging.LogUtils.getLogger().info("TM_WAGON_26_2_TRANSFER_PASS: animated round trip, single-owner cargo, material preservation, horse driving, optional cargo bridges");
            }
        } catch(Throwable failure) {
            stage=0;com.mojang.logging.LogUtils.getLogger().error("TM_WAGON_26_2_SMOKE_FAIL",failure);
            net.minecraft.client.Minecraft.getInstance().execute(()->net.minecraft.client.Minecraft.getInstance().stop());
        }
    }
    static void optionalCargo(WagonEntity wagon,ServerPlayer player) {
        var hold=wagon.cargo();var lookup=player.level().registryAccess();
        if(ModList.get().isLoaded("carryon")) {
            var be=new ChestBlockEntity(BlockPos.containing(wagon.position()),Blocks.CHEST.defaultBlockState());be.setLevel(player.level());
            be.setItem(0,new ItemStack(Items.EMERALD,7));
            var data=tschipp.carryon.common.carry.CarryOnDataManager.getCarryData(player);
            data.setBlock(be.getBlockState(),be,player,be.getBlockPos());data.setTick(-1);
            require(CarryOnCargo.place(hold,0,player)&&hold.entry(0)!=null&&!data.isCarrying(),"Carry On source committed once");
            require(hold.entry(0).inventory.getItem(0).getCount()==7,"Carry On inventory import");
            data.setKeyPressed(true);data.setTick(-1);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);
            require(CarryOnCargo.pickup(hold,hold.entry(0),player)&&hold.entry(0)==null&&data.isCarrying(),"Carry On cargo pickup");
            var returned=(ChestBlockEntity)data.getBlockEntity(be.getBlockPos(),lookup);
            require(returned.getItem(0).getCount()==7,"Carry On contents round trip");data.clear();
            require(!CarryOnCargo.pickup(hold,CargoEntry.fromItem(hold,new ItemStack(WagonContent.STOOL.get()),Blocks.OAK_PLANKS.defaultBlockState()),player),"Carry On rejects stool components");
        }
        if(ModList.get().isLoaded("travelersbackpack")) {
            var stack=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("travelersbackpack:standard")));
            com.tiviacz.travelersbackpack.inventory.BackpackWrapper.initializeSize(stack);
            stack.set(com.tiviacz.travelersbackpack.init.ModDataComponents.BACKPACK_CONTAINER.get(),net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND,3))));
            require(hold.place(0,stack,player)==null,"Traveler backpack placement");
            com.sange.tm_wagon.compat.BackpackCompat.open(hold,hold.entry(0),player);
            require(player.containerMenu instanceof com.sange.tm_wagon.compat.backpack.TravelerCargo.Menu,"Native traveler menu");player.closeContainer();
            hold.load(hold.save(lookup,false),lookup);
            require(hold.entry(0).returnedItem().get(com.tiviacz.travelersbackpack.init.ModDataComponents.BACKPACK_CONTAINER.get()).nonEmptyItemCopyStream().findFirst().orElseThrow().getCount()==3,"Traveler contents preservation");
            require(hold.take(0,player)==null,"Traveler unloading");
        }
        if(ModList.get().isLoaded("sophisticatedbackpacks")) {
            var stack=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("sophisticatedbackpacks:backpack")));
            net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper.fromStack(stack).getInventoryHandler().setStackInSlot(0,new ItemStack(Items.GOLD_INGOT,5));
            require(hold.place(0,stack,player)==null,"Sophisticated backpack placement");
            com.sange.tm_wagon.compat.BackpackCompat.open(hold,hold.entry(0),player);
            require(player.containerMenu instanceof net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer,"Native sophisticated menu");player.closeContainer();
            hold.load(hold.save(lookup,false),lookup);
            require(net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper.fromStack(hold.entry(0).returnedItem()).getInventoryHandler().getStackInSlot(0).getCount()==5,"Sophisticated contents preservation");
            require(hold.take(0,player)==null,"Sophisticated unloading");
        }
    }
}
