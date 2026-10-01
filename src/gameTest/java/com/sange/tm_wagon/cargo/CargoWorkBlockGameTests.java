package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class CargoWorkBlockGameTests {
    private static WagonEntity wagon(GameTestHelper h) {
        var w=WagonContent.WAGON.get().create(h.getLevel());w.configure(WagonEntity.defaultParts(),Direction.NORTH);
        w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(w);return w;
    }
    private static ServerPlayer player(GameTestHelper h,CargoHold hold) {
        var p=h.makeMockServerPlayerInLevel();p.getAbilities().instabuild=false;p.getAbilities().flying=false;
        p.setPos(hold.owner().cargoPose().point(new Vec3(-3,0,0)));return p;
    }
    private static CargoEntry put(GameTestHelper h,CargoHold hold,int slot,Item item,Player p) {
        String error=hold.place(slot,new ItemStack(item),p);h.assertTrue(error==null,"Placement failed: "+item+" "+error);return hold.entry(slot);
    }
    private static int carried(Player p,Item item) { return p.getInventory().items.stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum(); }
    private static int dropped(GameTestHelper h,CargoHold hold,Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(hold.owner().cargoPose().position(),hold.owner().cargoPose().position()).inflate(7)).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void placement_faces_player_in_wagon_coordinates_and_preserves_on_transfer(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var p=player(h,hold);
        for(float yaw:new float[]{180,90,0,270,129.74466F})for(Direction side:Direction.Plane.HORIZONTAL) {
            w.applyPose(new WagonPose(w.position(),yaw,0,0));Vec3 c=CargoHold.centre(0);
            p.setPos(w.pose().point(c.add(side.getStepX()*3,-1.5,side.getStepZ()*3)));p.setYRot(17);
            for(Item item:new Item[]{Items.FURNACE,Items.CHEST,Items.LOOM,Items.STONECUTTER,Items.LECTERN}) {
                var e=put(h,hold,0,item,p);
                h.assertTrue(e.state.getValue(HorizontalDirectionalBlock.FACING)==side,"Cargo ignored player side or wagon yaw: "+item+" "+yaw+" "+side+" "+e.state);
                h.assertTrue(hold.take(0,p)==null,"Could not remove facing fixture");
            }
        }
        var e=put(h,hold,0,Items.FURNACE,p);var state=e.state;var target=wagon(h).cargo();hold.transferTo(target);
        h.assertTrue(hold.empty()&&target.entry(0)==e&&target.entry(0).state==state,"Transfer changed orientation/ownership");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void all_new_workstation_menus_open_close_and_remain_valid_while_moving(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var p=player(h,hold);
        for(Item item:new Item[]{Items.CARTOGRAPHY_TABLE,Items.STONECUTTER,Items.ANVIL,Items.SMITHING_TABLE,Items.LOOM,Items.GRINDSTONE,Items.ENCHANTING_TABLE,Items.BREWING_STAND,Items.BLAST_FURNACE,Items.ENDER_CHEST}) {
            var e=put(h,hold,0,item,p);CargoMenus.open(hold,e,p);var menu=p.containerMenu;
            h.assertTrue(menu!=p.inventoryMenu&&menu.stillValid(p),"Menu did not open: "+item);
            w.applyPose(new WagonPose(w.position().add(0,0,.15),w.getYRot()+1,0,0));
            h.assertTrue(menu.stillValid(p),"Moving invalidated workstation: "+item);
            h.assertTrue(hold.take(0,p)==null&&p.containerMenu==p.inventoryMenu&&!menu.stillValid(p),"Removed workstation retained menu access: "+item);
        }h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void stonecutting_and_smithing_use_native_recipes_and_consume_once(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=player(h,hold);var stone=put(h,hold,0,Items.STONECUTTER,p);CargoMenus.open(hold,stone,p);
        var menu=(StonecutterMenu)p.containerMenu;menu.getSlot(0).set(new ItemStack(Items.STONE,2));
        h.assertTrue(menu.getNumRecipes()>0&&menu.clickMenuButton(p,0),"Missing stonecutting recipes");
        var result=menu.getSlot(1).getItem().copy();h.assertTrue(!result.isEmpty(),"Stonecutter result empty");
        menu.clicked(1,0,ClickType.PICKUP,p);p.closeContainer();
        h.assertTrue(carried(p,result.getItem())==result.getCount()&&carried(p,Items.STONE)==1,"Stonecutting did not consume/return one input");
        var smith=put(h,hold,1,Items.SMITHING_TABLE,p);CargoMenus.open(hold,smith,p);var s=p.containerMenu;
        s.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));s.getSlot(1).set(new ItemStack(Items.DIAMOND_SWORD));s.getSlot(2).set(new ItemStack(Items.NETHERITE_INGOT));
        h.assertTrue(s.getSlot(3).getItem().is(Items.NETHERITE_SWORD),"Smithing did not use native recipe");s.clicked(3,0,ClickType.PICKUP,p);p.closeContainer();
        h.assertTrue(carried(p,Items.NETHERITE_SWORD)==1&&carried(p,Items.DIAMOND_SWORD)==0&&carried(p,Items.NETHERITE_INGOT)==0&&carried(p,Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)==0,"Smithing duplicated inputs/output");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void anvil_wear_changes_only_cargo_and_returns_damaged_variant(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=player(h,hold);p.experienceLevel=100;
        var e=put(h,hold,0,Items.ANVIL,p);var location=BlockPos.containing(hold.position(e));
        h.getLevel().setBlock(location,Blocks.STONE.defaultBlockState(),3);
        CargoMenus.open(hold,e,p);var menu=(AnvilMenu)p.containerMenu;menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));menu.setItemName("Wagon sword");
        h.assertTrue(!menu.getSlot(2).getItem().isEmpty(),"Anvil rename result empty");menu.clicked(2,0,ClickType.PICKUP,p);p.closeContainer();
        h.assertTrue(carried(p,Items.IRON_SWORD)==1&&p.experienceLevel==99&&h.getLevel().getBlockState(location).is(Blocks.STONE),"Anvil cost or real-world protection failed");
        e.interactionLevel().setBlock(CargoLevel.POS,Blocks.DAMAGED_ANVIL.defaultBlockState(),2);
        h.assertTrue(hold.take(0,p)==null&&carried(p,Items.DAMAGED_ANVIL)==1&&h.getLevel().getBlockState(location).is(Blocks.STONE),"Anvil wear did not affect returned cargo variant");
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void independent_enchanting_consumes_lapis_levels_and_refunds_on_close(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=player(h,hold);p.experienceLevel=100;var e=put(h,hold,0,Items.ENCHANTING_TABLE,p);
        CargoMenus.open(hold,e,p);var menu=(EnchantmentMenu)p.containerMenu;
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,8));
        int selected=-1;for(int i=0;i<3;i++) { h.assertTrue(menu.costs[i]<=8,"Isolated table got shelf power");if(menu.costs[i]>0)selected=i; }
        h.assertTrue(selected>=0&&menu.clickMenuButton(p,selected)&&!menu.getSlot(0).getItem().getEnchantments().isEmpty(),"Native enchanting failed");
        p.closeContainer();h.assertTrue(carried(p,Items.DIAMOND_SWORD)==1&&carried(p,Items.LAPIS_LAZULI)==8-selected-1&&p.experienceLevel==100-selected-1,"Enchanting duplicated items or lost experience cost");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void grindstone_removes_enchantments_and_awards_experience(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=player(h,hold);var e=put(h,hold,0,Items.GRINDSTONE,p);CargoMenus.open(hold,e,p);
        var sword=new ItemStack(Items.IRON_SWORD);sword.enchant(h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS),2);
        var menu=p.containerMenu;menu.getSlot(0).set(sword);h.assertTrue(menu.getSlot(2).getItem().getEnchantments().isEmpty(),"Grindstone retained enchantment");menu.clicked(2,0,ClickType.PICKUP,p);p.closeContainer();
        h.assertTrue(carried(p,Items.IRON_SWORD)==1&&!h.getLevel().getEntitiesOfClass(ExperienceOrb.class,new AABB(hold.owner().cargoPose().position(),hold.owner().cargoPose().position()).inflate(7)).isEmpty(),"Grindstone lost output or experience");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void brewing_save_transfer_and_removal_preserve_contents_without_duplication(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=player(h,hold);var e=put(h,hold,0,Items.BREWING_STAND,p);
        for(int i=0;i<3;i++)e.inventory.setItem(i,PotionContents.createItemStack(Items.POTION,Potions.WATER));
        e.inventory.setItem(3,new ItemStack(Items.NETHER_WART));e.inventory.setItem(4,new ItemStack(Items.BLAZE_POWDER));
        for(int i=0;i<201;i++)hold.tick();h.assertTrue(e.brewTime==200&&e.brewFuel==19&&e.inventory.getItem(4).isEmpty(),"Brewing progress/fuel wrong");
        var saved=hold.save(h.getLevel().registryAccess(),false);hold.destroy(false);var target=wagon(h).cargo();target.load(saved,h.getLevel().registryAccess());
        var resumed=target.entry(0);h.assertTrue(resumed.brewTime==200&&resumed.brewFuel==19,"Brewing progress not saved");
        for(int i=0;i<200;i++)target.tick();for(int i=0;i<3;i++)h.assertTrue(resumed.inventory.getItem(i).get(DataComponents.POTION_CONTENTS).is(Potions.AWKWARD),"Brewing recipe failed");
        h.assertTrue(resumed.inventory.getItem(3).isEmpty(),"Brewing did not consume ingredient");
        var transferred=wagon(h).cargo();target.transferTo(transferred);h.assertTrue(target.empty()&&transferred.entry(0)==resumed,"Transfer copied brewing ownership");
        p.setPos(transferred.owner().cargoPose().point(new Vec3(-3,0,0)));
        h.assertTrue(transferred.take(0,p)==null&&transferred.take(0,p)!=null&&dropped(h,transferred,Items.POTION)==3&&carried(p,Items.BREWING_STAND)==1,"Removing brewing stand lost/duplicated potions");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void cauldron_bucket_bottle_and_banner_actions_do_not_mutate_world(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=player(h,hold);var e=put(h,hold,0,Items.CAULDRON,p);var location=BlockPos.containing(hold.position(e));
        h.getLevel().setBlock(location,Blocks.STONE.defaultBlockState(),3);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));CargoWorkBlocks.interact(hold,e,p,InteractionHand.MAIN_HAND,CargoHold.centre(0));
        h.assertTrue(e.state.is(Blocks.WATER_CAULDRON)&&e.state.getValue(LayeredCauldronBlock.LEVEL)==3&&p.getMainHandItem().is(Items.BUCKET),"Water filling failed");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS_BOTTLE));CargoWorkBlocks.interact(hold,e,p,InteractionHand.MAIN_HAND,CargoHold.centre(0));
        h.assertTrue(e.state.getValue(LayeredCauldronBlock.LEVEL)==2&&p.getMainHandItem().get(DataComponents.POTION_CONTENTS).is(Potions.WATER),"Bottle extraction failed");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.LAVA_BUCKET));CargoWorkBlocks.interact(hold,e,p,InteractionHand.MAIN_HAND,CargoHold.centre(0));
        h.assertTrue(e.state.is(Blocks.LAVA_CAULDRON)&&p.getMainHandItem().is(Items.BUCKET)&&h.getLevel().getBlockState(location).is(Blocks.STONE),"Lava fill mutated world");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=45)
    public static void composting_and_new_storage_return_owned_items_once(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=player(h,hold);var e=put(h,hold,0,Items.COMPOSTER,p);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.CAKE,7));for(int i=0;i<7;i++)CargoWorkBlocks.interact(hold,e,p,InteractionHand.MAIN_HAND,CargoHold.centre(0));
        h.assertTrue(e.state.getValue(ComposterBlock.LEVEL)==7&&p.getMainHandItem().isEmpty(),"Compost ingredients incorrect");
        h.runAtTickTime(22,()->{
            hold.tick();CargoWorkBlocks.interact(hold,e,p,InteractionHand.MAIN_HAND,CargoHold.centre(0));
            h.assertTrue(e.state.getValue(ComposterBlock.LEVEL)==0&&dropped(h,hold,Items.BONE_MEAL)==1,"Compost did not produce one bone meal");
            var pot=put(h,hold,1,Items.DECORATED_POT,p);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND,3));
            for(int i=0;i<3;i++)CargoWorkBlocks.interact(hold,pot,p,InteractionHand.MAIN_HAND,CargoHold.centre(1));
            h.assertTrue(pot.inventory.getItem(0).getCount()==3&&p.getMainHandItem().isEmpty(),"Pot storage did not consume input");
            h.assertTrue(hold.take(1,p)==null&&dropped(h,hold,Items.DIAMOND)==3,"Pot removal lost contents");h.succeed();
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void lectern_bookshelf_and_ender_chest_have_independent_authoritative_storage(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=player(h,hold);var lectern=put(h,hold,0,Items.LECTERN,p);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WRITABLE_BOOK));CargoWorkBlocks.interact(hold,lectern,p,InteractionHand.MAIN_HAND,CargoHold.centre(0));
        h.assertTrue(p.containerMenu instanceof LecternMenu&&p.getMainHandItem().isEmpty()&&lectern.state.getValue(LecternBlock.HAS_BOOK),"Lectern book failed");
        p.containerMenu.clickMenuButton(p,3);p.closeContainer();h.assertTrue(carried(p,Items.WRITABLE_BOOK)==1&&!lectern.state.getValue(LecternBlock.HAS_BOOK),"Lectern take duplicated book");
        var shelf=put(h,hold,1,Items.CHISELED_BOOKSHELF,p);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOOK,2));
        CargoWorkBlocks.interact(hold,shelf,p,InteractionHand.MAIN_HAND,CargoHold.centre(1).add(-CargoHold.SCALE/2,.55,0));
        h.assertTrue(shelf.inventory.getItem(1).is(Items.BOOK)&&p.getMainHandItem().getCount()==1,"Bookshelf insertion failed");
        CargoWorkBlocks.interact(hold,shelf,p,InteractionHand.MAIN_HAND,CargoHold.centre(1).add(-CargoHold.SCALE/2,.55,0));h.assertTrue(shelf.inventory.getItem(1).isEmpty()&&carried(p,Items.BOOK)==2,"Bookshelf removal duplicated book");
        var ender=put(h,hold,2,Items.ENDER_CHEST,p);p.getEnderChestInventory().setItem(0,new ItemStack(Items.EMERALD,9));CargoMenus.open(hold,ender,p);
        h.assertTrue(p.containerMenu.getSlot(0).getItem().getCount()==9,"Ender chest did not use player storage");
        h.assertTrue(hold.take(2,p)==null&&p.getEnderChestInventory().getItem(0).getCount()==9&&dropped(h,hold,Items.EMERALD)==0,"Taking ender chest spilled/duplicated personal storage");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void blast_furnace_uses_blasting_speed_and_recipes(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=player(h,hold);var e=put(h,hold,0,Items.BLAST_FURNACE,p);
        e.inventory.setItem(0,new ItemStack(Items.RAW_IRON,2));e.inventory.setItem(1,new ItemStack(Items.COAL));
        for(int i=0;i<100;i++)hold.tick();h.assertTrue(e.inventory.getItem(2).is(Items.IRON_INGOT)&&e.inventory.getItem(2).getCount()==1&&e.fuelDuration==800,"Blast furnace speed/fuel incorrect");
        e.inventory.setItem(0,new ItemStack(Items.BEEF));for(int i=0;i<110;i++)hold.tick();h.assertTrue(e.inventory.getItem(0).is(Items.BEEF)&&e.inventory.getItem(2).getCount()==1,"Blast furnace accepted smelting-only recipe");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void cartography_and_loom_consume_only_native_recipe_inputs(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=player(h,hold);var table=put(h,hold,0,Items.CARTOGRAPHY_TABLE,p);CargoMenus.open(hold,table,p);
        var menu=p.containerMenu;menu.getSlot(0).set(MapItem.create(h.getLevel(),0,0,(byte)0,true,false));menu.getSlot(1).set(new ItemStack(Items.MAP,2));
        h.assertTrue(menu.getSlot(2).getItem().is(Items.FILLED_MAP)&&menu.getSlot(2).getItem().getCount()==2,"Cartography cloning failed");
        menu.clicked(2,0,ClickType.PICKUP,p);p.closeContainer();h.assertTrue(carried(p,Items.FILLED_MAP)==2&&carried(p,Items.MAP)==1,"Cartography consumed/cloned incorrect counts");
        var loom=put(h,hold,1,Items.LOOM,p);CargoMenus.open(hold,loom,p);var l=(LoomMenu)p.containerMenu;
        l.getSlot(0).set(new ItemStack(Items.WHITE_BANNER));l.getSlot(1).set(new ItemStack(Items.BLUE_DYE));
        h.assertTrue(l.clickMenuButton(p,0)&&!l.getSlot(3).getItem().get(DataComponents.BANNER_PATTERNS).layers().isEmpty(),"Loom pattern recipe failed");
        l.clicked(3,0,ClickType.PICKUP,p);p.closeContainer();h.assertTrue(carried(p,Items.WHITE_BANNER)==1&&carried(p,Items.BLUE_DYE)==0,"Loom duplicated banner/dye");h.succeed();
    }

    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void worn_out_anvil_consumes_cargo_once_and_closes_stale_menu(GameTestHelper h) {
        var hold=wagon(h).cargo();var p=player(h,hold);p.experienceLevel=100;var e=put(h,hold,0,Items.DAMAGED_ANVIL,p);
        CargoMenus.open(hold,e,p);var menu=(AnvilMenu)p.containerMenu;menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));menu.setItemName("Last repair");
        int seed=0;var random=net.minecraft.util.RandomSource.create();for(;;seed++) { random.setSeed(seed);if(random.nextFloat()<.12)break; }
        p.getRandom().setSeed(seed);menu.clicked(2,0,ClickType.PICKUP,p);
        h.assertTrue(hold.entry(0)==null&&!menu.stillValid(p)&&hold.take(0,p)!=null,"Worn-out anvil retained ownership/access");
        p.closeContainer();h.assertTrue(carried(p,Items.IRON_SWORD)==1&&carried(p,Items.DAMAGED_ANVIL)==0,"Anvil break duplicated workstation or result");h.succeed();
    }

}

