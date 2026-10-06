package com.sange.tm_wagon.material;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.WagonEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.phys.*;

public class WagonMaterialGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
    private static final Map<String,List<String>> PATTERNS=Map.ofEntries(
        Map.entry("wagon_assembly_frame",List.of("SPS"," S ","SSS")),Map.entry("cargo_body",List.of("PPP","PLT","PPP")),
        Map.entry("wide_cargo_body",List.of("PPP"," U ","PPP")),Map.entry("triple_seat",List.of("PPP","WWW"," V ")),Map.entry("long_cargo_body",List.of(" PP","CLT"," PP")),Map.entry("single_horse_shafts",List.of("SSS"," L ","SSS")),
        Map.entry("single_wooden_seat",List.of("SPS","P P")),Map.entry("single_seat",List.of("P","W","E")),
        Map.entry("double_seat",List.of("PPP","WWW"," D ")),Map.entry("small_wheel",List.of("ISI","SLS","ISI")),
        Map.entry("large_wheel",List.of("PSP","SOS","PSP")),Map.entry("wagon_straw_mat",List.of("HHH","PPP")),
        Map.entry("wagon_stool",List.of("PPP","SSS","S S")),Map.entry("wagon_cabinet",List.of("PPP"," B ","PPP")),
        Map.entry("wagon_cargo_cover",List.of("RWR","WWW","RWR")),Map.entry("wagon_canopy",List.of("WWW","S S","I I")));
    private static Item item(WagonPart part) { return WagonContent.PART_ITEMS.get(part).get(); }
    private static WagonComponentRecipe recipe(GameTestHelper h,String name) {
        var value=h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("tm_wagon",name)).orElseThrow().value();
        h.assertTrue(value instanceof WagonComponentRecipe,"JSON recipe not loaded: "+name);return (WagonComponentRecipe)value;
    }
    private static List<ItemStack> inputs(String name,WoodMaterial wood,DyeColor colour) {
        var material=new WagonMaterial(wood,colour);var result=new ArrayList<ItemStack>();
        var wool=BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(colour.getName()+"_wool"));
        for(String row:PATTERNS.get(name))for(char c:row.toCharArray())result.add(switch(c) {
            case 'S'->new ItemStack(Items.STICK);case 'P'->new ItemStack(wood.planks());
            case 'L'->new ItemStack(wood.stripped());case 'T'->new ItemStack(wood.trapdoor());case 'W'->new ItemStack(wool);
            case 'I'->new ItemStack(Items.IRON_INGOT);case 'H'->new ItemStack(Items.WHEAT);case 'R'->new ItemStack(Items.VINE);
            case 'B'->new ItemStack(Items.BARREL);case 'C'->material.stack(item(WagonPart.CARGO_BODY));
            case 'E'->material.stack(item(WagonPart.SINGLE_WOODEN_SEAT));case 'D'->material.stack(item(WagonPart.DOUBLE_WOODEN_SEAT));
            case 'U'->material.stack(item(WagonPart.LONG_CARGO_BODY));case 'V'->material.stack(item(WagonPart.TRIPLE_WOODEN_SEAT));
            case 'O'->material.stack(item(WagonPart.SMALL_WHEEL));default->ItemStack.EMPTY;
        });return result;
    }
    private static CraftingInput input(String name,List<ItemStack> stacks) {
        var rows=PATTERNS.get(name);return CraftingInput.of(rows.getFirst().length(),rows.size(),stacks);
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=25)
    public void all_loaded_recipes_support_every_wood_and_dye(GameTestHelper h) {
        for(String name:PATTERNS.keySet())for(var wood:WoodMaterial.values())for(var colour:DyeColor.values()) {
            var r=recipe(h,name);var in=input(name,inputs(name,wood,colour));
            h.assertTrue(r.matches(in,h.getLevel()),"Expected recipe did not match: "+name+"/"+wood+"/"+colour);
            var stack=r.assemble(in,h.getLevel().registryAccess());
            var expected=new WagonMaterial(WagonMaterial.wooden(stack.getItem())?wood:WoodMaterial.OAK,WagonMaterial.dyed(stack.getItem())?colour:DyeColor.WHITE);
            h.assertTrue(!stack.isEmpty()&&WagonMaterial.of(stack).equals(expected)&&stack.getCount()==(name.equals("small_wheel")?2:1),"Wrong recipe result: "+name);
            h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,in,h.getLevel()).isPresent(),"RecipeManager cannot find recipe: "+name);
        }
        for(String name:List.of("double_wooden_seat","double_horse_shafts")) {
            var source=item(name.equals("double_wooden_seat")?WagonPart.SINGLE_WOODEN_SEAT:WagonPart.SINGLE_HORSE_SHAFTS);
            for(var wood:WoodMaterial.values()) {
                var style=new WagonMaterial(name.equals("double_wooden_seat")?wood:WoodMaterial.OAK,DyeColor.WHITE);
                var in=CraftingInput.of(2,2,List.of(style.stack(source),ItemStack.EMPTY,ItemStack.EMPTY,style.stack(source)));
                var r=recipe(h,name);h.assertTrue(r.matches(in,h.getLevel())&&WagonMaterial.of(r.assemble(in,h.getLevel().registryAccess())).equals(style),"Shapeless upgrade lost material");
                var one=style.stack(source);one.setCount(2);
                h.assertTrue(!r.matches(CraftingInput.of(1,1,List.of(one)),h.getLevel()),"Two items in one slot satisfied two ingredients");
            }
        }h.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=25)
    public void recipes_reject_mixed_inputs_and_preserve_upgraded_material(GameTestHelper h) {
        for(String name:List.of("cargo_body","single_wooden_seat","wagon_stool","wagon_cabinet")) {
            var r=recipe(h,name);var stacks=inputs(name,WoodMaterial.SPRUCE,DyeColor.WHITE);stacks.set(PATTERNS.get(name).getFirst().indexOf('P'),new ItemStack(Items.BIRCH_PLANKS));
            h.assertTrue(!r.matches(input(name,stacks),h.getLevel())&&r.assemble(input(name,stacks),h.getLevel().registryAccess()).isEmpty(),"Mixed planks accepted: "+name);
            stacks=inputs(name,WoodMaterial.SPRUCE,DyeColor.WHITE);for(int i=0;i<stacks.size();i++)if(stacks.get(i).is(WoodMaterial.SPRUCE.planks()))stacks.set(i,new ItemStack(Items.BAMBOO_PLANKS));
            h.assertTrue(!r.matches(input(name,stacks),h.getLevel()),"Unsupported bamboo variant accepted: "+name);
        }
        for(Item mismatch:List.of(Items.BIRCH_LOG,Items.BIRCH_TRAPDOOR)) {
            var stacks=inputs("cargo_body",WoodMaterial.SPRUCE,DyeColor.WHITE);stacks.set(mismatch==Items.BIRCH_LOG?4:5,new ItemStack(mismatch));
            h.assertTrue(!recipe(h,"cargo_body").matches(input("cargo_body",stacks),h.getLevel()),"Mixed body log/trapdoor accepted");
        }
        for(String name:List.of("triple_seat","double_seat","wagon_cargo_cover","wagon_canopy")) {
            var stacks=inputs(name,WoodMaterial.OAK,DyeColor.BLUE);int w=String.join("",PATTERNS.get(name)).indexOf('W');stacks.set(w,new ItemStack(Items.RED_WOOL));
            h.assertTrue(!recipe(h,name).matches(input(name,stacks),h.getLevel()),"Mixed wool accepted: "+name);
        }
        var dual=recipe(h,"double_wooden_seat");
        h.assertTrue(!dual.matches(CraftingInput.of(2,1,List.of(new WagonMaterial(WoodMaterial.BIRCH,DyeColor.WHITE).stack(item(WagonPart.SINGLE_WOODEN_SEAT)),new ItemStack(item(WagonPart.SINGLE_WOODEN_SEAT)))),h.getLevel()),"Mixed wood shapeless upgrade accepted");
        for(String name:List.of("wide_cargo_body","triple_seat","long_cargo_body","single_seat","double_seat","large_wheel","wagon_assembly_frame","wagon_straw_mat")) {
            var stacks=inputs(name,WoodMaterial.WARPED,DyeColor.GREEN);
            for(int i=0;i<stacks.size();i++)if(stacks.get(i).is(Items.WARPED_PLANKS))stacks.set(i,new ItemStack(Items.BAMBOO_PLANKS));
            var r=recipe(h,name);h.assertTrue(r.matches(input(name,stacks),h.getLevel()),"Arbitrary upgrade/frame/mat planks rejected: "+name);
            var out=r.assemble(input(name,stacks),h.getLevel().registryAccess());
            h.assertTrue(WagonMaterial.of(out).wood()==(WagonMaterial.wooden(out.getItem())?WoodMaterial.WARPED:WoodMaterial.OAK),"Upgrade used added planks instead of original component");
        }
        var column=inputs("single_seat",WoodMaterial.CHERRY,DyeColor.PURPLE);var grid=new ArrayList<ItemStack>(Collections.nCopies(9,ItemStack.EMPTY));for(int i=0;i<3;i++)grid.set(i*3+2,column.get(i));
        var r=recipe(h,"single_seat");h.assertTrue(r.matches(CraftingInput.of(3,3,grid),h.getLevel()),"Shifted column rejected");grid.set(0,new ItemStack(Items.DIAMOND));
        h.assertTrue(!r.matches(CraftingInput.of(3,3,grid),h.getLevel()),"Extra ingredients accepted");h.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=25)
    public void item_components_persist_and_keep_distinct_stacks(GameTestHelper h) {
        for(var wood:WoodMaterial.values())for(var colour:DyeColor.values()) {
            var material=new WagonMaterial(wood,colour);var stack=material.stack(item(WagonPart.DOUBLE_SEAT));
            var loaded=ItemStack.parseOptional(h.getLevel().registryAccess(),(CompoundTag)stack.save(h.getLevel().registryAccess()));
            h.assertTrue(ItemStack.isSameItemSameComponents(stack,loaded)&&WagonMaterial.of(loaded).equals(material),"Stack NBT lost material");
            var buffer=io.netty.buffer.Unpooled.buffer();try { WagonMaterial.STREAM_CODEC.encode(buffer,material);h.assertTrue(WagonMaterial.STREAM_CODEC.decode(buffer).equals(material)&&buffer.readableBytes()==0,"Network material mismatch"); }finally {buffer.release();}
        }
        h.assertTrue(ItemStack.isSameItemSameComponents(WagonMaterial.DEFAULT.stack(item(WagonPart.CARGO_BODY)),new ItemStack(item(WagonPart.CARGO_BODY))),"Default material broke existing item stacking");
        h.assertTrue(!ItemStack.isSameItemSameComponents(new WagonMaterial(WoodMaterial.BIRCH,DyeColor.WHITE).stack(item(WagonPart.CARGO_BODY)),new ItemStack(item(WagonPart.CARGO_BODY))),"Different woods stack together");h.succeed();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=25)
    public void native_workbench_consumes_once_and_returns_correct_material(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();p.setNoGravity(true);
        var pos=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlock(pos,net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState(),3);
        var menu=new net.minecraft.world.inventory.CraftingMenu(1,p.getInventory(),net.minecraft.world.inventory.ContainerLevelAccess.create(h.getLevel(),pos));p.containerMenu=menu;
        var stacks=inputs("cargo_body",WoodMaterial.CHERRY,DyeColor.WHITE);
        for(int i=0;i<9;i++) {stacks.get(i).setCount(2);menu.slots.get(i+1).set(stacks.get(i));}
        h.assertTrue(menu.slots.getFirst().getItem().is(item(WagonPart.CARGO_BODY))&&WagonMaterial.of(menu.slots.getFirst().getItem()).wood()==WoodMaterial.CHERRY,"Workbench did not expose styled result");
        menu.clicked(0,0,net.minecraft.world.inventory.ClickType.PICKUP,p);
        h.assertTrue(menu.getCarried().getCount()==1&&WagonMaterial.of(menu.getCarried()).wood()==WoodMaterial.CHERRY,"Workbench pickup lost style");
        for(int i=1;i<=9;i++)h.assertTrue(menu.slots.get(i).getItem().getCount()==1,"Workbench consumed wrong amount");
        menu.clicked(0,0,net.minecraft.world.inventory.ClickType.PICKUP,p);
        h.assertTrue(menu.getCarried().getCount()==2&&menu.slots.getFirst().getItem().isEmpty(),"Repeated craft duplicated or lost result");
        for(int i=1;i<=9;i++)h.assertTrue(menu.slots.get(i).getItem().isEmpty(),"Craft retained ingredients");p.closeContainer();h.succeed();
    }
    private static AssemblyFrameBlockEntity frame(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(11,2,17));h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState().setValue(AssemblyFrameBlock.FACING,Direction.NORTH),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(f.initializeFrame()==null,"Frame failed");
        var parts=WagonEntity.defaultParts();parts.put(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);
        for(var e:parts.entrySet()) {
            var style=new WagonMaterial(WoodMaterial.values()[e.getKey().ordinal()],e.getKey()==WagonSlot.SEAT?DyeColor.RED:DyeColor.WHITE);
            h.assertTrue(f.install(e.getKey(),e.getValue(),null,style.stack(item(e.getValue())))==null,"Part failed");
        }return f;
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,AssemblyFrameBlockEntity f) {
        var p=h.makeMockServerPlayerInLevel();p.setNoGravity(true);p.getAbilities().instabuild=false;p.setPos(f.cargoPose().point(new Vec3(-3,0,-1.8)));return p;
    }
    private static List<ItemStack> dropped(GameTestHelper h,Vec3 pos) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos,pos).inflate(7)).stream().map(ItemEntity::getItem).toList();
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=85)
    public void mixed_material_round_trip_and_seat_break_preserve_exact_items(GameTestHelper h) {
        var f=frame(h);var p=player(h,f);var styles=f.materials();var cabinet=new WagonMaterial(WoodMaterial.WARPED,DyeColor.WHITE);
        h.assertTrue(f.cargo().cabinet().install(cabinet.stack(WagonContent.CABINET.get()),p,new Vec3(-15.5/16,1.7,-1.8))==null,"Cabinet failed");
        var inventory=f.cargo().cabinet().inventory();inventory.setItem(53,new ItemStack(Items.DIAMOND,19));
        var stool=new WagonMaterial(WoodMaterial.CHERRY,DyeColor.WHITE);
        h.assertTrue(f.cargo().place(0,stool.stack(WagonContent.STOOL.get()),p)==null,"Stool failed");
        var cover=new WagonMaterial(WoodMaterial.OAK,DyeColor.BLUE);
        h.assertTrue(f.cargo().cover().install(cover.stack(WagonContent.CARGO_COVER.get()),p,new Vec3(-1.15625,1.9,.5))==null,"Cover failed");
        var tag=f.saveWithFullMetadata(h.getLevel().registryAccess());f.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(f.materials().equals(WagonMaterial.loadSlots(WagonMaterial.save(styles)))&&f.cargo().cabinet().material().equals(cabinet),"Frame save lost styles");
        var savedInventory=f.cargo().cabinet().inventory();h.assertTrue(f.toggleFrame(null)==null,"Assembly failed");
        var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(f.getBlockPos()).inflate(7)).getFirst();
        h.assertTrue(w.material(WagonSlot.SEAT).equals(styles.get(WagonSlot.SEAT))&&w.material(WagonSlot.REAR_LEFT).equals(styles.get(WagonSlot.REAR_LEFT))&&w.cargo().cabinet().inventory()==savedInventory&&w.cargo().cover().material().equals(cover)&&WagonMaterial.of(w.cargo().entry(0).item).equals(stool),"Assembly changed style or copied cabinet");
        var entityTag=new CompoundTag();w.saveWithoutId(entityTag);var copy=WagonContent.WAGON.get().create(h.getLevel());copy.load(entityTag);
        h.assertTrue(copy.materials().equals(WagonMaterial.loadSlots(WagonMaterial.save(styles)))&&copy.cargo().cabinet().material().equals(cabinet)&&copy.cargo().cabinet().inventory().getItem(53).getCount()==19,"Entity save lost styles or contents");
        h.runAtTickTime(24,()->h.assertTrue(f.toggleFrame(null)==null,"Restore failed"));
        h.runAtTickTime(49,()->{
            h.assertTrue(w.isRemoved()&&f.cargo().cabinet().inventory()==savedInventory&&f.material(WagonSlot.SEAT).equals(styles.get(WagonSlot.SEAT))&&f.cargo().cover().material().equals(cover),"Restore changed materials or inventory ownership");
            f.remove(Set.of(WagonSlot.SEAT),true);f.remove(Set.of(WagonSlot.SEAT),true);
            var drops=dropped(h,f.cargoPose().position());
            h.assertTrue(drops.stream().filter(s->s.is(item(WagonPart.DOUBLE_SEAT))&&WagonMaterial.of(s).equals(styles.get(WagonSlot.SEAT))).mapToInt(ItemStack::getCount).sum()==1,"Seat drop lost wood/dye or duplicated");
            h.assertTrue(drops.stream().filter(s->s.is(WagonContent.CABINET.get())&&WagonMaterial.of(s).equals(cabinet)).mapToInt(ItemStack::getCount).sum()==1&&drops.stream().filter(s->s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum()==19,"Cabinet drop lost material or contents");
            h.succeed();
        });
    }
    @GameTest(template="tm_wagon:assembly_test",timeoutTicks=35)
    public void block_and_hammer_dismantling_preserve_materials(GameTestHelper h) {
        var f=frame(h);var p=player(h,f);var styles=f.materials();
        var canopy=new WagonMaterial(WoodMaterial.OAK,DyeColor.ORANGE);
        h.assertTrue(f.cargo().canopy().install(canopy.stack(WagonContent.CANOPY.get()),p,new Vec3(-1.15625,1.9,.5))==null,"Canopy failed");
        f.dismantle(true,true);final var drops=dropped(h,f.cargoPose().position());
        for(var e:styles.entrySet())h.assertTrue(drops.stream().filter(s->s.is(item(WagonEntity.defaultParts().get(e.getKey())==WagonPart.SINGLE_SEAT?WagonPart.DOUBLE_SEAT:WagonEntity.defaultParts().get(e.getKey())))&&WagonMaterial.of(s).equals(e.getValue())).count()>=1,"Dismantle lost styled part: "+e.getKey());
        h.assertTrue(drops.stream().anyMatch(s->s.is(WagonContent.CANOPY.get())&&WagonMaterial.of(s).equals(canopy)),"Dismantle lost canopy colour");
        h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(f.getBlockPos()).inflate(7)).forEach(ItemEntity::discard);
        var w=WagonContent.WAGON.get().create(h.getLevel());w.configure(WagonEntity.defaultParts(),Direction.NORTH);w.setMaterials(styles);w.setPos(f.cargoPose().position());h.getLevel().addFreshEntity(w);
        var cargo=w.cargo().save(h.getLevel().registryAccess(),false);var roof=new CompoundTag();roof.putBoolean("Installed",true);roof.put("Material",canopy.save());cargo.put("Canopy",roof);w.cargo().load(cargo,h.getLevel().registryAccess());
        var cab=new CompoundTag();cab.putBoolean("Installed",true);cab.putInt("Rows",3);cab.put("Material",new WagonMaterial(WoodMaterial.SPRUCE,DyeColor.WHITE).save());cargo=w.cargo().save(h.getLevel().registryAccess(),false);cargo.put("Cabinet",cab);w.cargo().load(cargo,h.getLevel().registryAccess());
        w.cargo().cabinet().inventory().setItem(0,new ItemStack(Items.EMERALD,11));
        com.sange.tm_wagon.assembly.DismantlingHammerGameTests.strike(h,w);w.cargo().destroy(true,true);w.discard();
        final var loot=dropped(h,w.position());
        for(var e:styles.entrySet())h.assertTrue(loot.stream().anyMatch(s->s.is(item(w.parts().get(e.getKey())))&&WagonMaterial.of(s).equals(e.getValue())),"Hammer lost component material: "+e.getKey());
        h.assertTrue(loot.stream().filter(s->s.is(WagonContent.CANOPY.get())&&WagonMaterial.of(s).equals(canopy)).mapToInt(ItemStack::getCount).sum()==1,"Hammer lost canopy colour");
        h.assertTrue(loot.stream().filter(s->s.is(WagonContent.CABINET.get())&&WagonMaterial.of(s).wood()==WoodMaterial.SPRUCE).mapToInt(ItemStack::getCount).sum()==1
            &&loot.stream().filter(s->s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum()==11,"Hammer lost cabinet or duplicated inventory");h.succeed();
    }
}
