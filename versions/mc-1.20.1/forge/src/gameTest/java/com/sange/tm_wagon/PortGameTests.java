package com.sange.tm_wagon;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.cargo.*;
import com.sange.tm_wagon.entity.*;
import com.sange.tm_wagon.material.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class PortGameTests {
    static WagonEntity wagon(GameTestHelper h,WagonPart body) {
        var w=WagonContent.WAGON.get().create(h.getLevel());
        var parts=new java.util.EnumMap<WagonSlot,WagonPart>(WagonSlot.class);parts.putAll(WagonEntity.defaultParts());parts.put(WagonSlot.BODY,body);
        w.configure(parts,Direction.NORTH);w.setNoGravity(true);w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(w);return w;
    }
    static Player player(GameTestHelper h,CargoHold hold) {var p=h.makeMockPlayer();p.setPos(hold.owner().cargoPose().point(new Vec3(-3,0,0)));return p;}
    static AssemblyFrameBlockEntity frame(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(11,2,17));h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState(),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(f.initializeFrame()==null,"Frame initialization failed");return f;
    }
    @GameTest(template="assembly_test",timeoutTicks=120)
    public static void all_body_sizes_assembly_and_entity_transfer(GameTestHelper h) {
        var f=frame(h);
        for(var entry:WagonEntity.defaultParts().entrySet())h.assertTrue(f.install(entry.getKey(),entry.getValue(),null,new ItemStack(WagonContent.PART_ITEMS.get(entry.getValue()).get()))==null,"Mount failed: "+entry.getKey());
        var p=player(h,f.cargo());var stack=new ItemStack(Items.CHEST);stack.setHoverName(net.minecraft.network.chat.Component.literal("Kept chest"));
        h.assertTrue(f.cargo().place(0,stack,p)==null,"Cargo placement failed");f.cargo().entry(0).inventory.setItem(0,new ItemStack(Items.DIAMOND,17));
        h.assertTrue(f.toggleFrame(null)==null,"Entity conversion failed");h.assertTrue(f.cargo().empty(),"Source still owns transferred cargo");
        var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(4)).get(0);
        // This fixture tests ownership transfer, not unsupported-platform physics.
        w.setNoGravity(true);
        h.assertTrue(w.cargo().entry(0).inventory.getItem(0).getCount()==17,"Chest inventory lost during conversion");
        h.runAfterDelay(30,()->{
            h.assertTrue(f.toggleFrame(null)==null,"Restoration could not begin");
            h.runAfterDelay(30,()->{h.assertTrue(w.isRemoved()&&f.has(WagonSlot.BODY)&&f.cargo().entry(0).inventory.getItem(0).getCount()==17,"Restoration lost ownership or contents");h.succeed();});
        });
    }
    @GameTest(template="assembly_test")
    public static void capacities_materials_and_nbt_persistence(GameTestHelper h) {
        for(var body:new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY}) {
            var w=wagon(h,body);var hold=w.cargo();var p=player(h,hold);
            h.assertTrue(hold.capacity()==(body==WagonPart.CARGO_BODY?10:body==WagonPart.LONG_CARGO_BODY?12:32),"Wrong cargo capacity");
            var styles=new java.util.EnumMap<WagonSlot,WagonMaterial>(WagonSlot.class);styles.put(WagonSlot.BODY,new WagonMaterial(WoodMaterial.SPRUCE,DyeColor.BLUE));w.setMaterials(styles);
            for(int i=0;i<hold.capacity();i++){var s=new ItemStack(Items.OAK_PLANKS,2);s.getOrCreateTag().putInt("Marker",i);h.assertTrue(hold.place(i,s,p)==null&&s.getCount()==1,"Placement count changed");}
            var tag=w.saveWithoutId(new net.minecraft.nbt.CompoundTag());var copy=WagonContent.WAGON.get().create(h.getLevel());copy.load(tag);
            h.assertTrue(copy.material(WagonSlot.BODY).wood()==WoodMaterial.SPRUCE&&copy.cargo().occupiedSlots()==hold.capacity(),"Material/cargo persistence failed");
            h.assertTrue(copy.cargo().entry(hold.capacity()-1).item.getTag().getInt("Marker")==hold.capacity()-1,"Custom item NBT lost");w.discard();
        }h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void cargo_transfer_and_removal_have_one_owner(GameTestHelper h) {
        var w=wagon(h,WagonPart.CARGO_BODY);var hold=w.cargo();var p=player(h,hold);
        h.assertTrue(hold.place(0,new ItemStack(Items.CHEST),p)==null,"Place failed");hold.entry(0).inventory.setItem(0,new ItemStack(Items.DIAMOND,17));
        var target=new CargoHold(w);hold.transferTo(target);h.assertTrue(hold.empty()&&target.entry(0)!=null,"Transfer duplicated entry");
        var saved=target.save(h.getLevel().registryAccess(),false);target.load(saved,h.getLevel().registryAccess());
        h.assertTrue(target.entry(0).inventory.getItem(0).getCount()==17,"Container NBT lost");
        h.assertTrue(target.take(0,p)==null&&target.take(0,p)!=null,"Removal was not atomic");
        h.assertTrue(p.getInventory().items.stream().filter(s->s.is(Items.CHEST)).mapToInt(ItemStack::getCount).sum()==1,"Removal duplicated/lost chest");h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void materials_stack_and_blacklist(GameTestHelper h) {
        for(var wood:WoodMaterial.values()) {
            var material=new WagonMaterial(wood,DyeColor.RED);var item=material.stack(WagonContent.PART_ITEMS.get(WagonPart.DOUBLE_SEAT).get());
            h.assertTrue(WagonMaterial.of(item).equals(material),"Material NBT failed: "+wood);
            h.assertTrue(ItemStack.isSameItemSameTags(item,material.stack(item.getItem())),"Same variants do not stack");
        }
        for(var block:WagonContent.BLOCKS.getEntries())h.assertTrue(!CargoConfig.allows(block.get()),"Own module escaped blacklist");
        h.assertTrue(!CargoHold.allowed(new ItemStack(Items.RED_BED))&&!CargoHold.allowed(new ItemStack(Items.OAK_DOOR)),"Multi-block cargo accepted");h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void seats_use_actual_anchor_and_obstruction(GameTestHelper h) {
        var w=wagon(h,WagonPart.CARGO_BODY);var sheep=player(h,w.cargo());
        h.assertTrue(sheep.startRiding(w),"Could not board seat");w.positionRider(sheep);
        var expected=w.getPassengerRidingPosition(sheep).add(0,sheep.getMyRidingOffset(),0);
        h.assertTrue(sheep.position().distanceToSqr(expected)<1e-8,"Passenger is not at seat anchor");
        sheep.stopRiding();var point=w.getPassengerRidingPosition(sheep);h.getLevel().setBlock(BlockPos.containing(point.add(0,1,0)),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!sheep.startRiding(w),"Entity boarded through external obstruction");h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void rotated_collision_has_no_outer_air_wall(GameTestHelper h) {
        var w=wagon(h,WagonPart.CARGO_BODY);w.setYRot(45);w.refreshDimensions();
        var corners=w.getBoundingBox();var box=new AABB(corners.minX+.02,w.getY()+1.6,corners.minZ+.02,corners.minX+.12,w.getY()+1.7,corners.minZ+.12);
        h.assertTrue(h.getLevel().noCollision(null,box),"Broad AABB creates an invisible collision wall");
        h.assertTrue(!w.colliders().isEmpty(),"Oriented colliders missing");h.succeed();
    }

    @GameTest(template="assembly_test")
    public static void frame_loads_without_level_and_keeps_container(GameTestHelper h) {
        var f=frame(h);
        h.assertTrue(f.install(WagonSlot.BODY,WagonPart.CARGO_BODY,null,new ItemStack(WagonContent.PART_ITEMS.get(WagonPart.CARGO_BODY).get()))==null,"Body mount failed");
        h.assertTrue(f.cargo().place(0,new ItemStack(Items.CHEST),player(h,f.cargo()))==null,"Cargo placement failed");
        f.cargo().entry(0).inventory.setItem(2,new ItemStack(Items.EMERALD,23));
        var copy=new AssemblyFrameBlockEntity(f.getBlockPos(),f.getBlockState());copy.load(f.saveWithoutMetadata());
        h.assertTrue(copy.cargo().entry(0).inventory.getItem(2).getCount()==23,"Offline BE load lost cargo");
        var again=new AssemblyFrameBlockEntity(f.getBlockPos(),f.getBlockState());again.load(copy.saveWithoutMetadata());
        h.assertTrue(again.cargo().entry(0).inventory.getItem(2).getCount()==23,"Offline BE save lost cargo");h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void traveler_native_inventory_round_trip(GameTestHelper h) {
        if(!net.minecraftforge.fml.ModList.get().isLoaded("travelersbackpack")){h.succeed();return;}
        var hold=wagon(h,WagonPart.CARGO_BODY).cargo();var p=player(h,hold);
        var stack=com.tiviacz.travelersbackpack.init.ModItems.STANDARD_TRAVELERS_BACKPACK.get().getDefaultInstance();
        var nativeBag=new com.tiviacz.travelersbackpack.inventory.BackpackWrapper(stack,3,null,h.getLevel());
        nativeBag.inventory.setStackInSlot(0,new ItemStack(Items.DIAMOND,19));stack.getOrCreateTag().putString("CustomMarker","traveler");
        h.assertTrue(hold.place(0,stack,p)==null,"Traveler placement failed");
        var saved=hold.save(h.getLevel().registryAccess(),false);hold.load(saved,h.getLevel().registryAccess());
        var loaded=new com.tiviacz.travelersbackpack.inventory.BackpackWrapper(hold.entry(0).item,3,null,h.getLevel());
        h.assertTrue(loaded.inventory.getStackInSlot(0).getCount()==19&&hold.entry(0).item.getTag().getString("CustomMarker").equals("traveler"),"Traveler contents lost");
        var be=((net.minecraft.world.level.block.EntityBlock)hold.entry(0).state.getBlock()).newBlockEntity(BlockPos.ZERO,hold.entry(0).state);be.setLevel(h.getLevel());
        com.sange.tm_wagon.compat.BackpackCompat.snapshot(be,hold.entry(0).item.copy(),h.getLevel().registryAccess());
        var carried=com.sange.tm_wagon.compat.BackpackCompat.fromBlockEntity(be,hold.entry(0).item.copy(),h.getLevel().registryAccess());
        var restored=new com.tiviacz.travelersbackpack.inventory.BackpackWrapper(carried,3,null,h.getLevel());
        h.assertTrue(restored.inventory.getStackInSlot(0).getCount()==19,"Carry On BE round trip lost traveler items");
        h.assertTrue(hold.take(0,p)==null,"Traveler removal failed");
        var result=p.getInventory().items.stream().filter(com.sange.tm_wagon.compat.BackpackCompat::traveler).findFirst().orElseThrow();
        h.assertTrue(new com.tiviacz.travelersbackpack.inventory.BackpackWrapper(result,3,null,h.getLevel()).inventory.getStackInSlot(0).getCount()==19,"Removed traveler contents lost");h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void sophisticated_native_inventory_round_trip(GameTestHelper h) {
        if(!net.minecraftforge.fml.ModList.get().isLoaded("sophisticatedbackpacks")){h.succeed();return;}
        var hold=wagon(h,WagonPart.CARGO_BODY).cargo();var p=player(h,hold);
        var stack=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("sophisticatedbackpacks","backpack")));
        var wrapper=net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackLinkedStorageResolver.resolveOrCreate(h.getLevel(),stack);
        wrapper.getInventoryHandler().setStackInSlot(0,new ItemStack(Items.GOLD_INGOT,21));stack.getOrCreateTag().putString("CustomMarker","sophisticated");
        h.assertTrue(hold.place(0,stack,p)==null,"Sophisticated placement failed");
        var saved=hold.save(h.getLevel().registryAccess(),false);hold.load(saved,h.getLevel().registryAccess());
        var entry=hold.entry(0);var be=((net.minecraft.world.level.block.EntityBlock)entry.state.getBlock()).newBlockEntity(BlockPos.ZERO,entry.state);be.setLevel(h.getLevel());
        com.sange.tm_wagon.compat.BackpackCompat.snapshot(be,entry.item.copy(),h.getLevel().registryAccess());
        var restored=com.sange.tm_wagon.compat.BackpackCompat.fromBlockEntity(be,entry.item.copy(),h.getLevel().registryAccess());
        h.assertTrue(net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackLinkedStorageResolver.resolveOrCreate(h.getLevel(),restored).getInventoryHandler().getStackInSlot(0).getCount()==21,"Sophisticated BE contents lost");
        h.assertTrue(hold.take(0,p)==null,"Sophisticated removal failed");
        var result=p.getInventory().items.stream().filter(com.sange.tm_wagon.compat.BackpackCompat::sophisticated).findFirst().orElseThrow();
        h.assertTrue(result.getTag().getString("CustomMarker").equals("sophisticated"),"Custom NBT lost");
        h.assertTrue(net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackLinkedStorageResolver.resolveOrCreate(h.getLevel(),result).getInventoryHandler().getStackInSlot(0).getCount()==21,"Removed sophisticated contents lost");h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void maid_tasks_recipes_and_sleep_follow(GameTestHelper h) {
        if(net.minecraftforge.fml.ModList.get().isLoaded("touhou_little_maid")) {
            var tasks=com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager.getTaskMap();
            h.assertTrue(tasks.containsKey(com.sange.tm_wagon.compat.maid.WagonMaidExtension.TASK)&&tasks.containsKey(com.sange.tm_wagon.compat.maid.WagonMaidExtension.RIDE_TASK),"Maid tasks not registered");
        }
        for(String id:new String[]{"cargo_body","long_cargo_body","wide_cargo_body","single_seat","triple_wooden_seat","dismantling_hammer"})
            h.assertTrue(h.getLevel().getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("tm_wagon",id)).isPresent(),"Recipe missing: "+id);
        var w=wagon(h,WagonPart.CARGO_BODY);var hold=w.cargo();var p=player(h,hold);
        h.assertTrue(hold.place(4,new ItemStack(WagonContent.STRAW_MAT.get()),p)==null,"Mat placement failed");
        var sheep=net.minecraft.world.entity.EntityType.SHEEP.create(h.getLevel());sheep.setPos(hold.position(hold.entry(4)));sheep.setNoAi(true);h.getLevel().addFreshEntity(sheep);
        h.assertTrue(StrawMatSleep.sleepMob(hold,4,sheep),"Mob could not use mat");
        w.setPos(w.position().add(1,0,0));w.setYRot(45);StrawMatSleep.follow(sheep);
        h.assertTrue(sheep.isSleeping()&&sheep.position().distanceToSqr(StrawMatSleep.sleepingPoint(sheep))<1e-8,"Moving mat does not keep sleeper attached");
        sheep.stopSleeping();h.assertTrue(!StrawMatSleep.matSleeper(sheep)&&!sheep.isSleeping(),"Sleep session leaked");h.succeed();
    }

    @GameTest(template="assembly_test")
    public static void hitched_horse_accepts_driving_input(GameTestHelper h) {
        for(int x=4;x<=18;x++)for(int z=7;z<=28;z++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,1,z)),Blocks.STONE.defaultBlockState(),3);
        var w=wagon(h,WagonPart.CARGO_BODY);var p=player(h,w.cargo());
        var horse=EntityType.HORSE.create(h.getLevel());horse.setNoAi(true);horse.setPos(w.horsePosition(0));h.getLevel().addFreshEntity(horse);horse.setLeashedTo(p,false);
        h.assertTrue(w.attachHorse(p,horse,0)==null,"Horse attachment failed");
        h.assertTrue(p.startRiding(w)&&w.driver()==p,"Driver could not mount");var origin=w.position();
        for(int i=0;i<20;i++){w.acceptInput(p,1,0,false);w.tick();}
        h.assertTrue(w.position().distanceToSqr(origin)>.1&&w.hasHorse(horse.getUUID()),"Drive input failed to move hitched wagon");
        h.assertTrue(horse.getLeashHolder()==w,"Harness leash owner lost");w.detachAllHorses();
        h.assertTrue(!HorseHarness.attached(horse)&&!w.hasAttachedHorses(),"Detach did not release horse");h.succeed();
    }

    @GameTest(template="assembly_test")
    public static void native_right_click_installs_body_on_every_platform_cell(GameTestHelper h) {
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"wagon-placement"));
        for(var facing:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST}) {
            var f=frame(h);h.getLevel().setBlock(f.getBlockPos(),f.getBlockState().setValue(AssemblyFrameBlock.FACING,facing),3);f.initializeFrame();
            var cells=f.frameCells().entrySet().stream().filter(e->e.getValue().stream().anyMatch(b->Math.abs(e.getKey().getY()+b.maxY-1.375)<1e-6)).map(e->e.getKey()).toList();
            f.dismantle(false,true);
            for(var body:new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY})for(var offset:cells) {
                f=frame(h);h.getLevel().setBlock(f.getBlockPos(),f.getBlockState().setValue(AssemblyFrameBlock.FACING,facing),3);f.initializeFrame();
                var box=f.frameCells().get(offset).stream().filter(b->Math.abs(offset.getY()+b.maxY-1.375)<1e-6).findFirst().orElseThrow();
                var pos=f.getBlockPos().offset(offset);var point=Vec3.atLowerCornerOf(pos).add((box.minX+box.maxX)/2,box.maxY,(box.minZ+box.maxZ)/2);
                p.setPos(point.add(0,0,3));p.setShiftKeyDown(false);p.getAbilities().instabuild=false;
                var stack=new WagonMaterial(WoodMaterial.SPRUCE,DyeColor.WHITE).stack(WagonContent.PART_ITEMS.get(body).get());stack.setCount(2);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var hit=new BlockHitResult(point,Direction.UP,pos,false);
                var result=p.gameMode.useItemOn(p,h.getLevel(),stack,InteractionHand.MAIN_HAND,hit);
                h.assertTrue(result.consumesAction()&&f.part(WagonSlot.BODY)==body,"Native placement failed: "+body+" / "+facing+" / "+offset);
                h.assertTrue(f.extended()&&!f.switching(),"Component click folded the frame");
                h.assertTrue(stack.getCount()==1&&f.material(WagonSlot.BODY).wood()==WoodMaterial.SPRUCE,"Placement count/material incorrect");
                f.dismantle(false,true);
            }
        }h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void platform_empty_hand_still_folds_without_components(GameTestHelper h) {
        var f=frame(h);var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"wagon-jack"));
        var cell=f.frameCells().entrySet().stream().filter(e->e.getValue().stream().anyMatch(b->Math.abs(e.getKey().getY()+b.maxY-1.375)<1e-6)).findFirst().orElseThrow();
        var box=cell.getValue().stream().filter(b->Math.abs(cell.getKey().getY()+b.maxY-1.375)<1e-6).findFirst().orElseThrow();
        var pos=f.getBlockPos().offset(cell.getKey());var point=Vec3.atLowerCornerOf(pos).add((box.minX+box.maxX)/2,box.maxY,(box.minZ+box.maxZ)/2);
        p.setPos(point.add(0,0,3));p.setShiftKeyDown(false);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        var result=p.gameMode.useItemOn(p,h.getLevel(),ItemStack.EMPTY,InteractionHand.MAIN_HAND,new BlockHitResult(point,Direction.UP,pos,false));
        h.assertTrue(result.consumesAction()&&f.switching()&&!f.extended(),"Empty-hand platform interaction stopped working");h.succeed();
    }
}
