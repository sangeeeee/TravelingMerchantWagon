package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.CargoConfig;
import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.WagonEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class CargoGameTests {
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void cargo_filter_modes_ids_namespaces_tags_and_validation(GameTestHelper h) {
        var black=CargoConfig.Policy.from(CargoConfig.ListMode.BLACKLIST,List.of("tm_wagon:*","minecraft:stone","#minecraft:logs"),List.of("minecraft:stone"));
        h.assertTrue(!black.allows(Blocks.STONE)&&!black.allows(Blocks.OAK_LOG)&&black.allows(Blocks.OAK_PLANKS),"Blacklist ID/tag match or inactive whitelist incorrect");
        for(var registered:WagonContent.BLOCKS.getEntries())h.assertTrue(!black.allows(registered.get()),"Own block escaped default namespace rule");
        var white=CargoConfig.Policy.from(CargoConfig.ListMode.WHITELIST,List.of("minecraft:*"),List.of("minecraft:stone","#minecraft:logs","tm_wagon:*"));
        h.assertTrue(white.allows(Blocks.STONE)&&white.allows(Blocks.OAK_LOG)&&white.allows(WagonContent.FRAME.get())&&!white.allows(Blocks.OAK_PLANKS),"Whitelist or inactive blacklist incorrect");
        h.assertTrue(CargoConfig.Policy.from(CargoConfig.ListMode.BLACKLIST,List.of(),List.of()).allows(Blocks.STONE),"Empty blacklist rejected cargo");
        h.assertTrue(!CargoConfig.Policy.from(CargoConfig.ListMode.WHITELIST,List.of(),List.of()).allows(Blocks.STONE),"Empty whitelist accepted cargo");
        for(String value:List.of("minecraft:stone"," tm_wagon:* ","#minecraft:logs"))h.assertTrue(CargoConfig.validEntry(value),"Valid selector rejected");
        for(String value:List.of("","stone","minecraft:","tm_wagon:wheel*","#tm_wagon:*","Bad:stone",":*"))h.assertTrue(!CargoConfig.validEntry(value),"Invalid selector accepted: "+value);
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void default_filter_rejects_wagon_blocks_without_consumption(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var p=player(h,hold);
        for(var registered:WagonContent.BLOCKS.getEntries())h.assertTrue(!CargoConfig.allows(registered.get()),"Loaded default config permits own block");
        var items=new java.util.ArrayList<net.minecraft.world.item.Item>();items.add(WagonContent.FRAME_ITEM.get());
        for(var registered:WagonContent.PART_ITEMS.values())items.add(registered.get());
        for(var item:items) {
            var stack=new ItemStack(item,2);
            h.assertTrue(!CargoHold.allowed(stack)&&"message.tm_wagon.cargo_filtered".equals(hold.place(0,stack,p)),"Own item accepted as entity cargo");
            h.assertTrue(stack.getCount()==2&&hold.empty(),"Rejected placement changed ownership");
        }
        w.discard();var f=frame(h);var blockPlayer=player(h,f.cargo());
        for(var item:items) {
            blockPlayer.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item,2));
            Vec3 hit=f.cargoPose().point(CargoHold.centre(0));BlockPos pos=BlockPos.containing(hit.add(0,-.01,0));
            var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(blockPlayer,InteractionHand.MAIN_HAND,pos,
                new net.minecraft.world.phys.BlockHitResult(hit,Direction.UP,pos,false));
            CargoInteractions.block(event);
            h.assertTrue(event.isCanceled()&&event.getCancellationResult().consumesAction(),"Own item bypassed block-form cargo filter");
            h.assertTrue(f.cargo().empty()&&blockPlayer.getMainHandItem().getCount()==2,"Own item accepted as block-form cargo");
        }
        var mount=WagonSlot.FRONT_LEFT.position(f.getBlockPos(),f.facing());
        var mountEvent=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(blockPlayer,InteractionHand.MAIN_HAND,mount,
            new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(mount),Direction.WEST,mount,false));
        CargoInteractions.block(mountEvent);
        h.assertTrue(!mountEvent.isCanceled(),"Cargo filtering intercepted a normal wheel mount position");
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void filtered_existing_cargo_survives_load_transfer_and_removal(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var item=WagonContent.PART_ITEMS.get(WagonPart.SMALL_WHEEL).get();
        var legacy=CargoEntry.fromItem(hold,new ItemStack(item),WagonContent.PART_BLOCKS.get(WagonPart.SMALL_WHEEL).get().defaultBlockState());
        var tag=hold.save(h.getLevel().registryAccess(),false);var saved=legacy.save(h.getLevel().registryAccess(),false);saved.putInt("Slot",0);
        var list=new net.minecraft.nbt.ListTag();list.add(saved);tag.put("Entries",list);hold.load(tag,h.getLevel().registryAccess());
        h.assertTrue(hold.entry(0)!=null&&!CargoHold.allowed(new ItemStack(item)),"Blacklisted existing cargo was deleted on load");
        var target=new CargoHold(w);hold.transferTo(target);
        h.assertTrue(hold.empty()&&target.entry(0)!=null,"Blacklisted cargo was lost during transfer");
        var p=player(h,target);
        h.assertTrue(target.take(0,p)==null&&target.take(0,p)!=null&&carried(p,item)==1,"Existing blacklisted cargo could not be removed exactly once");
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void cargo_sounds_match_block_and_only_committed_transactions(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var p=player(h,hold);Vec3 point=hold.owner().cargoPose().point(CargoHold.centre(0));
        var sounds=new java.util.ArrayList<net.minecraft.sounds.SoundEvent>();
        java.util.function.Consumer<net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition> listener=event->{
            if(event.getLevel()==h.getLevel()&&event.getPosition().distanceToSqr(point)<.00001&&event.getSource()==net.minecraft.sounds.SoundSource.BLOCKS&&event.getSound()!=null)sounds.add(event.getSound().value());
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        try {
            for(var block:List.of(Blocks.STONE,Blocks.GREEN_WOOL,Blocks.GLASS,Blocks.CHEST)) {
                var stack=new ItemStack(block,2);int before=sounds.size();
                h.assertTrue(hold.place(0,stack,p)==null,"Sound test placement failed");
                var state=hold.entry(0).state;var type=state.getSoundType(h.getLevel(),BlockPos.containing(point),p);
                h.assertTrue(sounds.size()==before+1&&sounds.get(before)==type.getPlaceSound(),"Missing/duplicate/wrong placement sound");
                h.assertTrue(hold.place(0,stack,p)!=null&&sounds.size()==before+1,"Failed placement played a sound");
                h.assertTrue(hold.take(0,p)==null&&sounds.size()==before+2&&sounds.get(before+1)==type.getBreakSound(),"Missing/duplicate/wrong removal sound");
                h.assertTrue(hold.take(0,p)!=null&&sounds.size()==before+2&&stack.getCount()==1,"Repeated removal played a sound or duplicated goods");
            }
            int before=sounds.size();
            h.assertTrue(hold.place(0,new ItemStack(WagonContent.FRAME_ITEM.get()),p)!=null&&sounds.size()==before,"Filtered cargo played a sound");
            w.discard();var blockHold=frame(h).cargo();var blockPlayer=player(h,blockHold);int slot=0;
            h.assertTrue(blockHold.place(slot,new ItemStack(Blocks.OAK_PLANKS),blockPlayer)==null&&blockHold.take(slot,blockPlayer)==null,"Block-form sound test failed");
            h.assertTrue(sounds.size()==before+2,"Block-form cargo did not broadcast both sounds exactly once");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener); }
        h.succeed();
    }
    private static WagonEntity wagon(GameTestHelper h) {
        var w=WagonContent.WAGON.get().create(h.getLevel());w.configure(WagonEntity.defaultParts(),Direction.NORTH);
        w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(w);return w;
    }
    private static AssemblyFrameBlockEntity frame(GameTestHelper h) {
        var p=h.absolutePos(new BlockPos(11,2,17));
        h.getLevel().setBlock(p,WagonContent.FRAME.get().defaultBlockState().setValue(AssemblyFrameBlock.FACING,Direction.NORTH),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(p);
        h.assertTrue(f.initializeFrame()==null,"Frame init failed");
        install(h,f,WagonSlot.BODY,WagonPart.CARGO_BODY);
        for(var part:WagonEntity.defaultParts().entrySet())if(part.getKey()!=WagonSlot.BODY)install(h,f,part.getKey(),part.getValue());
        return f;
    }
    private static void install(GameTestHelper h,AssemblyFrameBlockEntity f,WagonSlot slot,WagonPart part) {
        h.assertTrue(f.install(slot,part,null,new ItemStack(WagonContent.PART_ITEMS.get(part).get()))==null,"Part installation failed");
    }
    private static Player player(GameTestHelper h,CargoHold hold) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(hold.owner().cargoPose().point(new Vec3(-3,0,0)));return p;
    }
    private static ServerPlayer serverPlayer(GameTestHelper h,CargoHold hold) {
        var p=h.makeMockServerPlayerInLevel();p.getAbilities().instabuild=false;p.getAbilities().flying=false;
        p.teleportTo(hold.owner().cargoPose().point(new Vec3(-3,0,0)).x,hold.owner().cargoPose().position().y,hold.owner().cargoPose().position().z);
        return p;
    }
    private static CargoEntry put(GameTestHelper h,CargoHold hold,int slot,Item item,Player player) {
        String error=hold.place(slot,new ItemStack(item),player);h.assertTrue(error==null,"Cargo placement failed: "+error);return hold.entry(slot);
    }
    private static List<ItemEntity> drops(GameTestHelper h,CargoHold hold) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(hold.owner().cargoPose().position(),hold.owner().cargoPose().position()).inflate(7));
    }
    private static int dropped(GameTestHelper h,CargoHold hold,Item item) { return drops(h,hold).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum(); }
    private static int carried(Player p,Item item) { return p.getInventory().items.stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum(); }

    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void ten_slots_components_consumption_and_rejection(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var p=player(h,hold);
        for(int i=0;i<10;i++) {
            var stack=new ItemStack(Items.OAK_PLANKS,2);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Cargo "+i));
            h.assertTrue(hold.place(i,stack,p)==null&&stack.getCount()==1,"Placement did not consume exactly one");
            h.assertTrue(hold.place(i,stack,p)!=null&&stack.getCount()==1,"Occupied slot consumed or replaced cargo");
        }
        h.assertTrue(hold.boxes().size()==10,"Wrong capacity");
        h.assertTrue(hold.take(0,p)==null&&hold.take(0,p)!=null,"Repeated removal succeeded");
        h.assertTrue(carried(p,Items.OAK_PLANKS)==1&&p.getInventory().getItem(0).getHoverName().getString().equals("Cargo 0"),"Lost custom item components");
        for(var item:new Item[]{Items.RED_BED,Items.OAK_DOOR,Items.TALL_GRASS,Items.STICK}) {
            var stack=new ItemStack(item,2);h.assertTrue("message.tm_wagon.cargo_unsupported".equals(hold.place(0,stack,p))&&stack.getCount()==2,"Unsupported cargo consumed");
        }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void block_form_mouse_hit_and_sneak_remove(GameTestHelper h) {
        var f=frame(h);var hold=f.cargo();var p=player(h,hold);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE,2));
        h.assertTrue(hold.interact(p,InteractionHand.MAIN_HAND,CargoHold.centre(9)).consumesAction()&&hold.entry(9)!=null,"Floor hit failed");
        AABB volume=CargoHold.worldBox(CargoHold.slotBox(9),f.cargoPose()).deflate(.04);
        h.assertTrue(!h.getLevel().noCollision(null,volume),"Block-form cargo has no collision");
        p.setShiftKeyDown(true);hold.interact(p,InteractionHand.MAIN_HAND,CargoHold.centre(9).add(0,.68,0));
        h.assertTrue(hold.entry(9)==null&&carried(p,Items.STONE)==2,"Sneak removal lost/duplicated stone");
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void cargo_collision_blocks_entities_and_placement(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var p=player(h,hold);
        var pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);
        pig.setPos(w.pose().point(CargoHold.centre(4)));h.getLevel().addFreshEntity(pig);
        var stack=new ItemStack(Items.STONE,2);
        h.assertTrue("message.tm_wagon.cargo_blocked".equals(hold.place(4,stack,p))&&stack.getCount()==2,"Occupied volume accepted cargo");pig.discard();
        put(h,hold,4,Items.STONE,p);pig=EntityType.PIG.create(h.getLevel());pig.setPos(w.pose().point(CargoHold.centre(4)).add(0,2,0));
        pig.move(MoverType.SELF,new Vec3(0,-3,0));
        h.assertTrue(Math.abs(pig.getY()-w.getY()-2.18)<.01,"Entity did not land on scaled cargo");
        h.assertTrue(h.getLevel().noCollision(null,CargoHold.worldBox(CargoHold.slotBox(5),w.pose()).deflate(.04)),"Empty neighbouring slot blocked");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void chest_contents_spill_once_and_shulker_retains_them(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=player(h,hold);
        var chest=new ItemStack(Items.CHEST);chest.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND,7))));
        h.assertTrue(hold.place(0,chest,p)==null,"Preloaded chest failed");
        h.assertTrue(hold.take(0,p)==null&&hold.take(0,p)!=null,"Repeated take duplicated chest");
        h.assertTrue(dropped(h,hold,Items.DIAMOND)==7&&carried(p,Items.CHEST)==1,"Chest contents did not spill exactly once");
        h.assertTrue(!p.getInventory().getItem(0).has(DataComponents.CONTAINER),"Returned chest still contains same items");
        var shulker=new ItemStack(Items.BLUE_SHULKER_BOX);shulker.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.EMERALD,11))));
        h.assertTrue(hold.place(1,shulker,p)==null&&hold.take(1,p)==null,"Shulker place/take failed");
        var returned=p.getInventory().items.stream().filter(s->s.is(Items.BLUE_SHULKER_BOX)).findFirst().orElseThrow();
        h.assertTrue(returned.get(DataComponents.CONTAINER).nonEmptyStream().mapToInt(ItemStack::getCount).sum()==11&&dropped(h,hold,Items.EMERALD)==0,"Shulker contents spilled or disappeared");
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void nbt_container_and_full_inventory_take_have_one_owner(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=serverPlayer(h,hold);
        var contents=NonNullList.withSize(27,ItemStack.EMPTY);contents.set(5,new ItemStack(Items.DIAMOND,9));
        var tag=new CompoundTag();ContainerHelper.saveAllItems(tag,contents,h.getLevel().registryAccess());tag.putString("id","minecraft:barrel");
        var stack=new ItemStack(Items.BARREL);stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(tag));
        h.assertTrue(hold.place(2,stack,p)==null,"NBT barrel failed");
        h.assertTrue(hold.entry(2).inventory.getItem(5).getCount()==9,"NBT items were not loaded into barrel");
        for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        h.assertTrue(hold.take(2,p)==null,"Full-inventory take failed");
        h.assertTrue(dropped(h,hold,Items.BARREL)==1&&dropped(h,hold,Items.DIAMOND)==9,"Full inventory duplicated/lost cargo: barrels="+dropped(h,hold,Items.BARREL)+", diamonds="+dropped(h,hold,Items.DIAMOND));
        var item=drops(h,hold).stream().filter(e->e.getItem().is(Items.BARREL)).findFirst().orElseThrow().getItem();
        h.assertTrue(!item.get(DataComponents.BLOCK_ENTITY_DATA).copyTag().contains("Items"),"Returned barrel retains spilled inventory");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void destruction_and_repeated_discard_never_duplicate(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var p=player(h,hold);
        put(h,hold,0,Items.CHEST,p).inventory.setItem(0,new ItemStack(Items.DIAMOND,13));
        put(h,hold,1,Items.SHULKER_BOX,p).inventory.setItem(2,new ItemStack(Items.EMERALD,17));
        w.discard();w.discard();hold.destroy(true);
        h.assertTrue(dropped(h,hold,Items.CHEST)==1&&dropped(h,hold,Items.DIAMOND)==13&&dropped(h,hold,Items.SHULKER_BOX)==1&&dropped(h,hold,Items.EMERALD)==0,"Destruction duplicated/spilled shulker items");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void furnace_smoker_recipe_fuel_and_saved_progress(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=player(h,hold);var f=put(h,hold,0,Items.FURNACE,p);var s=put(h,hold,1,Items.SMOKER,p);
        f.inventory.setItem(0,new ItemStack(Items.RAW_IRON,2));f.inventory.setItem(1,new ItemStack(Items.COAL,2));
        s.inventory.setItem(0,new ItemStack(Items.BEEF,2));s.inventory.setItem(1,new ItemStack(Items.COAL,2));
        for(int i=0;i<100;i++)hold.tick();
        h.assertTrue(f.cook==100&&f.inventory.getItem(2).isEmpty()&&s.inventory.getItem(2).is(Items.COOKED_BEEF)&&s.fuelDuration==800,"Furnace/smoker timing or fuel differs from vanilla");
        var saved=hold.save(h.getLevel().registryAccess(),false);var copy=new CargoHold(hold.owner());copy.load(saved,h.getLevel().registryAccess());
        h.assertTrue(copy.entry(0).cook==100&&copy.entry(0).burn==f.burn&&copy.entry(0).inventory.getItem(0).getCount()==2,"Saved processing state changed");
        for(int i=0;i<100;i++)copy.tick();
        h.assertTrue(copy.entry(0).inventory.getItem(2).is(Items.IRON_INGOT)&&copy.entry(1).inventory.getItem(2).getCount()==2,"Recipes did not resume after save");
        var visible=hold.save(h.getLevel().registryAccess(),true).getList("Entries",10).getCompound(0);
        h.assertTrue(!visible.contains("Items")&&!visible.contains("Burn")&&!visible.contains("Recipes"),"Visual sync exposed complete inventory/tick state");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void full_output_does_not_consume_fuel_and_lava_returns_bucket(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=player(h,hold);var f=put(h,hold,0,Items.FURNACE,p);
        f.inventory.setItem(0,new ItemStack(Items.RAW_IRON));f.inventory.setItem(1,new ItemStack(Items.COAL));f.inventory.setItem(2,new ItemStack(Items.IRON_INGOT,64));
        for(int i=0;i<210;i++)hold.tick();
        h.assertTrue(f.inventory.getItem(1).getCount()==1&&f.burn==0&&f.inventory.getItem(0).getCount()==1,"Blocked output consumed fuel/input");
        f.inventory.setItem(2,ItemStack.EMPTY);f.inventory.setItem(1,new ItemStack(Items.LAVA_BUCKET));hold.tick();
        h.assertTrue(f.inventory.getItem(1).is(Items.BUCKET)&&f.burn==20000,"Fuel container was lost");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=90)
    public static void actual_conversion_round_trip_preserves_slots_and_contents(GameTestHelper h) {
        var f=frame(h);var p=player(h,f.cargo());var entry=put(h,f.cargo(),7,Items.CHEST,p);entry.inventory.setItem(3,new ItemStack(Items.DIAMOND,23));
        var id=entry.id;var furnace=put(h,f.cargo(),2,Items.FURNACE,p);furnace.inventory.setItem(0,new ItemStack(Items.RAW_IRON,2));furnace.inventory.setItem(1,new ItemStack(Items.COAL));
        f.cargo().tick();h.assertTrue(f.toggleFrame(null)==null,"Assembly with cargo failed");
        var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(6)).getFirst();
        h.assertTrue(f.cargo().empty()&&w.cargo().entry(7)==entry&&w.cargo().entry(2)==furnace,"Conversion copied/lost ownership");
        h.runAtTickTime(24,()->{h.assertTrue(f.toggleFrame(null)==null,"Restoration failed");h.assertTrue("message.tm_wagon.assembly_busy".equals(w.cargo().take(7,p)),"Locked wagon allowed cargo removal");});
        h.runAtTickTime(49,()->{
            h.assertTrue(w.isRemoved()&&w.cargo().empty()&&f.cargo().entry(7).id.equals(id)&&f.cargo().entry(7).inventory.getItem(3).getCount()==23,"Round trip lost/duplicated cargo");
            h.assertTrue(f.cargo().entry(2).burn>0&&f.cargo().entry(2).cook>0&&drops(h,f.cargo()).isEmpty(),"Conversion reset processing or created drops");h.succeed();
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=110)
    public static void failed_restoration_preserves_authoritative_cargo(GameTestHelper h) {
        var f=frame(h);var p=player(h,f.cargo());var entry=put(h,f.cargo(),6,Items.BARREL,p);entry.inventory.setItem(1,new ItemStack(Items.DIAMOND,19));
        h.assertTrue(f.toggleFrame(null)==null,"Assembly failed");var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(6)).getFirst();
        var obstruction=f.getBlockPos().offset(0,1,2);
        h.runAtTickTime(24,()->{h.assertTrue(f.toggleFrame(null)==null,"Restore failed to begin");h.getLevel().setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);});
        h.runAtTickTime(74,()->{
            h.assertTrue(!w.isRemoved()&&f.cargo().empty()&&w.cargo().entry(6)==entry&&entry.inventory.getItem(1).getCount()==19&&drops(h,w.cargo()).isEmpty(),"Failed restore moved/lost/duplicated goods");
            h.getLevel().setBlock(obstruction,Blocks.AIR.defaultBlockState(),3);h.assertTrue(f.toggleFrame(null)==null,"Retry failed");
        });
        h.runAtTickTime(99,()->{h.assertTrue(w.isRemoved()&&f.cargo().entry(6)==entry&&entry.inventory.getItem(1).getCount()==19,"Retry lost goods");h.succeed();});
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void native_chest_menu_shift_click_cursor_and_invalidation(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=serverPlayer(h,hold);var entry=put(h,hold,0,Items.CHEST,p);entry.inventory.setItem(0,new ItemStack(Items.DIAMOND,5));
        CargoMenus.open(hold,entry,p);var menu=p.containerMenu;
        h.assertTrue(!(menu instanceof InventoryMenu)&&menu.stillValid(p)&&entry.opened,"Native chest did not open");
        menu.clicked(0,0,ClickType.QUICK_MOVE,p);h.assertTrue(carried(p,Items.DIAMOND)==5&&entry.inventory.getItem(0).isEmpty(),"Shift-click duplicated/lost chest contents");
        entry.inventory.setItem(1,new ItemStack(Items.EMERALD,3));menu.clicked(1,0,ClickType.PICKUP,p);
        h.assertTrue(menu.getCarried().getCount()==3,"Pickup failed");h.assertTrue(hold.take(0,p)==null,"Take open chest failed");
        h.assertTrue(p.containerMenu==p.inventoryMenu&&menu.getCarried().isEmpty()&&!menu.stillValid(p)&&carried(p,Items.EMERALD)==3&&carried(p,Items.CHEST)==1,"Closing menu lost/duplicated cursor or retained access");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void crafting_menu_returns_inputs_and_shulker_rejects_nesting(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=serverPlayer(h,hold);var workbench=put(h,hold,0,Items.CRAFTING_TABLE,p);
        CargoMenus.open(hold,workbench,p);var menu=p.containerMenu;
        for(int slot:new int[]{1,2,4,5})menu.getSlot(slot).set(new ItemStack(Items.OAK_PLANKS,2));
        h.assertTrue(menu.getSlot(0).getItem().is(Items.CRAFTING_TABLE),"Native cargo workbench did not compute recipe");
        menu.clicked(0,0,ClickType.PICKUP,p);p.closeContainer();
        h.assertTrue(carried(p,Items.CRAFTING_TABLE)==1&&carried(p,Items.OAK_PLANKS)==4,"Workbench crafted or returned ingredients incorrectly");
        var shulker=put(h,hold,1,Items.SHULKER_BOX,p);CargoMenus.open(hold,shulker,p);
        h.assertTrue(!p.containerMenu.getSlot(0).mayPlace(new ItemStack(Items.BLUE_SHULKER_BOX))&&p.containerMenu.getSlot(0).mayPlace(new ItemStack(Items.STONE)),"Shulker nested-container rule was lost");p.closeContainer();h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void multiple_viewers_share_inventory_and_close_before_transfer(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var p=serverPlayer(h,hold);var q=serverPlayer(h,hold);var entry=put(h,hold,0,Items.CHEST,p);
        CargoMenus.open(hold,entry,p);CargoMenus.open(hold,entry,q);p.closeContainer();h.assertTrue(entry.opened,"First viewer closed lid for second viewer");
        q.containerMenu.getSlot(0).set(new ItemStack(Items.DIAMOND,31));
        var target=new CargoHold(w);hold.transferTo(target);
        h.assertTrue(q.containerMenu==q.inventoryMenu&&hold.empty()&&target.entry(0)==entry&&!entry.opened&&entry.inventory.getItem(0).getCount()==31,"Transfer left open menu or duplicated inventory");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=60)
    public static void tailgate_animation_commits_collision_only_at_endpoint(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var old=hold.tailBox();
        h.assertTrue(hold.toggleGate()==null&&hold.toggleGate()!=null&&hold.gateProgress(0)==0,"Gate began at endpoint or accepted repeated clicks");
        h.runAtTickTime(8,()->h.assertTrue(hold.gateProgress(0)>0&&hold.gateProgress(0)<1&&hold.tailBox().equals(old),"Collision moved during gate animation"));
        h.runAtTickTime(18,()->{
            h.assertTrue(hold.gateOpen()&&!hold.gateMoving()&&hold.tailBox().maxY<1.6,"Gate did not lower outside cart");
            var saved=hold.save(h.getLevel().registryAccess(),false);var copy=new CargoHold(w);copy.load(saved,h.getLevel().registryAccess());
            h.assertTrue(copy.gateOpen()&&copy.gateProgress(0)==1&&copy.tailBox().equals(hold.tailBox()),"Gate pose was not persisted");
            h.assertTrue(hold.toggleGate()==null,"Gate would not close");
        });
        h.runAtTickTime(36,()->{h.assertTrue(!hold.gateOpen()&&!hold.gateMoving()&&hold.tailBox().equals(old),"Gate did not return to closed collision");h.succeed();});
    }
    @GameTest(template="assembly_test",timeoutTicks=90)
    public static void blocked_gate_close_reverses_without_embedding_entities(GameTestHelper h) {
        var f=frame(h);var hold=f.cargo();h.assertTrue(hold.toggleGate()==null,"Open failed");
        h.runAtTickTime(18,()->{
            h.assertTrue(hold.gateOpen(),"Block-form gate did not open");
            var pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);pig.setNoGravity(true);pig.setPos(f.cargoPose().point(new Vec3(0,1.6,2.35)));h.getLevel().addFreshEntity(pig);
            h.assertTrue(hold.toggleGate()==null,"Close failed to begin");
        });
        h.runAtTickTime(55,()->{h.assertTrue(hold.gateOpen()&&!hold.gateMoving(),"Blocked gate embedded entity or kept retrying");h.succeed();});
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void actual_block_use_routes_cargo_before_world_placement(GameTestHelper h) {
        var f=frame(h);var p=serverPlayer(h,f.cargo());var point=f.cargoPose().point(CargoHold.centre(4));
        p.setPos(point.add(0,.8,0));p.setXRot(90);p.setYRot(180);
        var pos=BlockPos.containing(point.add(0,-.001,0));var hit=new net.minecraft.world.phys.BlockHitResult(point,Direction.UP,pos,false);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.RED_BED,2));
        h.assertTrue(p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,hit).consumesAction()&&f.cargo().empty()&&p.getMainHandItem().getCount()==2,"Multiblock item fell through to normal placement");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE,2));
        h.assertTrue(p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,hit).consumesAction()&&f.cargo().entry(4)!=null&&p.getMainHandItem().getCount()==1,"Native right click did not route to cargo");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void entity_surface_ray_places_and_removes_scaled_cargo(GameTestHelper h) {
        var w=wagon(h);var p=player(h,w.cargo());var point=w.pose().point(CargoHold.centre(6));
        p.setPos(point.add(0,.8,0));p.setXRot(90);p.setYRot(180);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.CHEST,2));
        h.assertTrue(w.interact(p,InteractionHand.MAIN_HAND).consumesAction()&&w.cargo().entry(6)!=null&&p.getMainHandItem().getCount()==1,"Entity floor ray did not place cargo");
        p.setShiftKeyDown(true);w.interact(p,InteractionHand.MAIN_HAND);
        h.assertTrue(w.cargo().empty()&&carried(p,Items.CHEST)==2,"Entity cargo ray did not remove correct slot");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void block_break_and_repeated_remove_spill_cargo_once(GameTestHelper h) {
        var f=frame(h);var p=player(h,f.cargo());put(h,f.cargo(),0,Items.BARREL,p).inventory.setItem(0,new ItemStack(Items.DIAMOND,21));
        f.remove(java.util.Set.of(WagonSlot.BODY),true);f.remove(java.util.Set.of(WagonSlot.BODY),true);
        h.assertTrue(f.cargo().empty()&&dropped(h,f.cargo(),Items.BARREL)==1&&dropped(h,f.cargo(),Items.DIAMOND)==21,"Block-form break duplicated or lost goods");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void native_furnace_output_shift_click_awards_experience_once(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=serverPlayer(h,hold);var f=put(h,hold,0,Items.FURNACE,p);
        f.inventory.setItem(0,new ItemStack(Items.RAW_IRON,10));f.inventory.setItem(1,new ItemStack(Items.COAL,2));
        for(int i=0;i<2000;i++)hold.tick();CargoMenus.open(hold,f,p);var menu=p.containerMenu;
        menu.clicked(2,0,ClickType.QUICK_MOVE,p);
        int experience=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,new AABB(hold.owner().cargoPose().position(),hold.owner().cargoPose().position()).inflate(7)).stream().mapToInt(e->e.getValue()).sum();
        h.assertTrue(carried(p,Items.IRON_INGOT)==10&&experience==7,"Furnace output/experience incorrect: "+experience);
        h.assertTrue(hold.take(0,p)==null,"Could not remove furnace after taking output");
        int after=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,new AABB(hold.owner().cargoPose().position(),hold.owner().cargoPose().position()).inflate(7)).stream().mapToInt(e->e.getValue()).sum();
        h.assertTrue(after==experience,"Removing furnace awarded the same experience twice");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void open_gate_conversion_retains_geometry_and_container_position(GameTestHelper h) {
        var f=frame(h);var p=player(h,f.cargo());put(h,f.cargo(),9,Items.SHULKER_BOX,p).inventory.setItem(0,new ItemStack(Items.DIAMOND,29));
        h.assertTrue(f.cargo().toggleGate()==null,"Could not open gate");
        h.runAtTickTime(18,()->{
            h.assertTrue(f.cargo().gateOpen()&&f.toggleFrame(null)==null,"Open gate assembly failed");
            var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(6)).getFirst();
            h.assertTrue(w.cargo().gateOpen()&&w.cargo().entry(9).inventory.getItem(0).getCount()==29,"Assembly changed gate/cargo");
            h.runAfterDelay(24,()->h.assertTrue(f.toggleFrame(null)==null,"Open gate restore failed to begin"));
            h.runAfterDelay(49,()->{
                h.assertTrue(w.isRemoved()&&f.cargo().gateOpen()&&f.cargo().entry(9).inventory.getItem(0).getCount()==29,"Open gate round trip changed contents or pose");h.succeed();
            });
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void save_reload_preserves_full_cargo_but_closes_unattended_lids(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var p=player(h,hold);var entry=put(h,hold,3,Items.BARREL,p);entry.inventory.setItem(0,new ItemStack(Items.DIAMOND,27));entry.setOpened(true);
        var tag=new CompoundTag();w.saveWithoutId(tag);var loaded=WagonContent.WAGON.get().create(h.getLevel());loaded.load(tag);
        var restored=loaded.cargo().entry(3);h.assertTrue(restored.id.equals(entry.id)&&restored.inventory.getItem(0).getCount()==27&&!restored.opened&&!restored.state.getValue(net.minecraft.world.level.block.BarrelBlock.OPEN),"Save reload lost contents or kept unattended lid open");
        h.assertTrue(loaded.intersects(CargoHold.worldBox(CargoHold.slotBox(3),loaded.pose()).deflate(.05)),"Reloaded cargo has no entity collision");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=30)
    public static void frame_unload_closes_menu_without_spilling_inventory(GameTestHelper h) {
        var f=frame(h);var p=serverPlayer(h,f.cargo());var entry=put(h,f.cargo(),4,Items.CHEST,p);entry.inventory.setItem(0,new ItemStack(Items.DIAMOND,15));
        CargoMenus.open(f.cargo(),entry,p);p.containerMenu.setCarried(new ItemStack(Items.EMERALD,3));f.onChunkUnloaded();
        h.assertTrue(p.containerMenu==p.inventoryMenu&&!entry.opened&&entry.inventory.getItem(0).getCount()==15&&carried(p,Items.EMERALD)==3&&drops(h,f.cargo()).isEmpty(),"Chunk unload lost cargo/cursor or retained menu");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=90)
    public static void new_work_block_contents_and_brewing_progress_survive_both_form_conversions(GameTestHelper h) {
        var f=frame(h);var p=player(h,f.cargo());var brew=put(h,f.cargo(),6,Items.BREWING_STAND,p);
        brew.inventory.setItem(0,net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,net.minecraft.world.item.alchemy.Potions.WATER));
        brew.inventory.setItem(3,new ItemStack(Items.NETHER_WART));brew.inventory.setItem(4,new ItemStack(Items.BLAZE_POWDER));
        f.cargo().tick();int fuel=brew.brewFuel;int time=brew.brewTime;var state=brew.state;
        var shelf=put(h,f.cargo(),7,Items.CHISELED_BOOKSHELF,p);shelf.inventory.setItem(2,new ItemStack(Items.BOOK));
        var pot=put(h,f.cargo(),8,Items.DECORATED_POT,p);pot.inventory.setItem(0,new ItemStack(Items.DIAMOND,13));
        h.assertTrue(f.toggleFrame(null)==null,"Workstation assembly failed");
        var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(6)).getFirst();
        h.assertTrue(f.cargo().empty()&&w.cargo().entry(6)==brew&&brew.brewFuel==fuel&&brew.brewTime==time&&brew.state==state,"Entity conversion changed workstation ownership/state");
        h.runAtTickTime(24,()->h.assertTrue(f.toggleFrame(null)==null,"Workstation restore failed to start"));
        h.runAtTickTime(49,()->{
            h.assertTrue(w.isRemoved()&&w.cargo().empty()&&f.cargo().entry(6)==brew&&f.cargo().entry(7)==shelf&&f.cargo().entry(8)==pot,"Block restoration copied/lost workstations");
            h.assertTrue(brew.brewFuel==fuel&&brew.brewTime>0&&brew.brewTime<=time&&shelf.inventory.getItem(2).is(Items.BOOK)&&pot.inventory.getItem(0).getCount()==13&&drops(h,f.cargo()).isEmpty(),"Restoration lost/duplicated contents or brewing progress");h.succeed();
        });
    }

}
