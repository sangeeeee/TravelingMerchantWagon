package com.sange.tm_wagon.cargo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

public final class CargoEntry {
    public enum Kind { ORDINARY, CHEST, BARREL, SHULKER, FURNACE, SMOKER, BLAST_FURNACE, CRAFTING, CARTOGRAPHY, STONECUTTER, ANVIL, SMITHING, LOOM, GRINDSTONE, ENCHANTING, BREWING, CAULDRON, COMPOSTER, ENDER_CHEST, LECTERN, BOOKSHELF, POT, STRAW_MAT, SLEEPING_BAG, STOOL }
    public final UUID id;
    public final ItemStack item;
    public BlockState state;
    public final Kind kind;
    public final Inventory inventory;
    CargoHold hold;
    public int burn,fuelDuration,cook,totalCook=200;
    public int brewTime,brewFuel,page;
    public long compostReady=Long.MIN_VALUE;
    public UUID sleeper;
    /** Head at the rear end; anchors always store the rearmost reserved cell. */
    public boolean reversed;
    public net.minecraft.world.item.Item brewingIngredient=Items.AIR;
    private CargoLevel interactionLevel;
    public boolean opened;
    public long lidStart=Long.MIN_VALUE;
    public float lidFrom;
    private boolean loading;
    private final Map<ResourceLocation,Integer> recipesUsed=new HashMap<>();
    private final RecipeManager.CachedCheck<net.minecraft.world.Container,? extends AbstractCookingRecipe> recipeCheck;

    public static Kind kind(BlockState state) {
        var block=state.getBlock();
        if(block==Blocks.CHEST||block==Blocks.TRAPPED_CHEST)return Kind.CHEST;
        if(block==Blocks.BARREL)return Kind.BARREL;
        var storage=CargoContainers.kind(state);if(storage!=Kind.ORDINARY)return storage;
        if(block instanceof ShulkerBoxBlock)return Kind.SHULKER;
        if(block==Blocks.FURNACE)return Kind.FURNACE;
        if(block==Blocks.SMOKER)return Kind.SMOKER;
        if(block==Blocks.BLAST_FURNACE)return Kind.BLAST_FURNACE;
        if(block==Blocks.CRAFTING_TABLE)return Kind.CRAFTING;
        if(block==Blocks.CARTOGRAPHY_TABLE)return Kind.CARTOGRAPHY;
        if(block==Blocks.STONECUTTER)return Kind.STONECUTTER;
        if(block instanceof AnvilBlock)return Kind.ANVIL;
        if(block==Blocks.SMITHING_TABLE)return Kind.SMITHING;
        if(block==Blocks.LOOM)return Kind.LOOM;
        if(block==Blocks.GRINDSTONE)return Kind.GRINDSTONE;
        if(block==Blocks.ENCHANTING_TABLE)return Kind.ENCHANTING;
        if(block==Blocks.BREWING_STAND)return Kind.BREWING;
        if(block instanceof AbstractCauldronBlock)return Kind.CAULDRON;
        if(block==Blocks.COMPOSTER)return Kind.COMPOSTER;
        if(block==Blocks.ENDER_CHEST)return Kind.ENDER_CHEST;
        if(block==Blocks.LECTERN)return Kind.LECTERN;
        if(block==Blocks.CHISELED_BOOKSHELF)return Kind.BOOKSHELF;
        if(block==Blocks.DECORATED_POT)return Kind.POT;
        return Kind.ORDINARY;
    }
    public CargoEntry(CargoHold hold,UUID id,ItemStack stack,BlockState state) {
        this.hold=hold;this.id=id;item=stack.copyWithCount(1);this.state=state;var detected=kind(state);
        if((detected==Kind.CHEST||detected==Kind.BARREL)&&CargoContainers.protectedContents(stack))detected=Kind.ORDINARY;
        kind=stack.getItem() instanceof StrawMatItem?Kind.STRAW_MAT:stack.getItem() instanceof WagonStoolItem?Kind.STOOL:com.sange.tm_wagon.compat.BackpackCompat.sleepingBag(stack)?Kind.SLEEPING_BAG:detected;
        recipeCheck=RecipeManager.createCheck(recipeType());
        inventory=new Inventory(switch(kind) {
            case FURNACE,SMOKER,BLAST_FURNACE->3;case CHEST,BARREL,SHULKER->27;
            case BREWING->5;case LECTERN,POT->1;case BOOKSHELF->6;default->0;
        });
    }
    public int footprintRows() { return kind==Kind.STRAW_MAT?3:kind==Kind.SLEEPING_BAG?2:1; }
    public boolean sleepingSurface() { return kind==Kind.STRAW_MAT||kind==Kind.SLEEPING_BAG; }
    public static CargoEntry fromItem(CargoHold hold,ItemStack stack,BlockState state) {
        var lookup=hold.owner().cargoLevel().registryAccess();
        var entry=new CargoEntry(hold,UUID.randomUUID(),stack,state);entry.loading=true;
        if(entry.inventory.getContainerSize()>0) {
            var contents=NonNullList.withSize(entry.inventory.getContainerSize(),ItemStack.EMPTY);
            var tag=com.sange.tm_wagon.cargo.ItemNbt.blockEntity(stack);
            ContainerHelper.loadAllItems(tag,contents);
            for(int i=0;i<contents.size();i++)entry.inventory.setItem(i,contents.get(i));
            entry.burn=Math.max(0,tag.getInt("BurnTime"));entry.cook=Math.max(0,tag.getInt("CookTime"));
            entry.totalCook=Math.max(1,tag.contains("CookTimeTotal")?tag.getInt("CookTimeTotal"):200);
            entry.fuelDuration=entry.cooking()?Math.max(entry.burn,entry.fuelTime(contents.get(1))):0;
            var recipes=tag.getCompound("RecipesUsed");
            for(String key:recipes.getAllKeys()) { var name=ResourceLocation.tryParse(key);if(name!=null)entry.recipesUsed.put(name,Math.max(0,recipes.getInt(key))); }
            if(entry.kind==Kind.BREWING) {
                entry.brewTime=Math.max(0,tag.getInt("BrewTime"));entry.brewFuel=Math.max(0,tag.getInt("Fuel"));entry.brewingIngredient=entry.inventory.getItem(3).getItem();
            }
            if(entry.kind==Kind.LECTERN&&tag.contains("Book"))entry.inventory.setItem(0,ItemStack.of(tag.getCompound("Book")));
            if(entry.kind==Kind.POT&&tag.contains("item"))entry.inventory.setItem(0,ItemStack.of(tag.getCompound("item")));
            
            for(String key:new String[]{"Items","BurnTime","CookTime","CookTimeTotal","RecipesUsed","BrewTime","Fuel","Book","Page","item"})tag.remove(key);
            if(tag.isEmpty())entry.item.removeTagKey("BlockEntityTag");else ItemNbt.blockEntity(entry.item,tag);
        }
        if(entry.cooking())entry.state=entry.state.setValue(AbstractFurnaceBlock.LIT,entry.burn>0);
        entry.loading=false;CargoWorkBlocks.updateVisualState(entry);return entry;
    }
    private RecipeType<? extends AbstractCookingRecipe> recipeType() { return switch(kind) {
        case SMOKER->RecipeType.SMOKING;case BLAST_FURNACE->RecipeType.BLASTING;default->RecipeType.SMELTING;
    }; }
    private int fuelTime(ItemStack fuel) {
        // The item hook can return -1 to request the vanilla fuel table. ForgeHooks
        // resolves that fallback and applies mod fuel events, like a world furnace.
        int duration=net.minecraftforge.common.ForgeHooks.getBurnTime(fuel,recipeType());
        return kind==Kind.SMOKER||kind==Kind.BLAST_FURNACE?duration/2:duration;
    }
    public boolean cooking() { return kind==Kind.FURNACE||kind==Kind.SMOKER||kind==Kind.BLAST_FURNACE; }
    public net.minecraft.world.level.Level holdOwnerLevel() { return hold.owner().cargoLevel(); }
    public float lid(float tick) {
        if(lidStart==Long.MIN_VALUE||hold.owner().cargoLevel()==null)return opened?1:0;
        float t=(float)net.minecraft.util.Mth.clamp((hold.owner().cargoLevel().getGameTime()+tick-lidStart)/8,0,1);
        return lidFrom+((opened?1:0)-lidFrom)*t;
    }
    public void setOpened(boolean open) {
        if(opened==open)return;
        lidFrom=lid(0);opened=open;lidStart=hold.owner().cargoLevel().getGameTime();
        if(state.hasProperty(BarrelBlock.OPEN))state=state.setValue(BarrelBlock.OPEN,open);
        hold.changed(true);
    }
    CargoLevel interactionLevel() {
        if(interactionLevel==null)interactionLevel=new CargoLevel(hold,this);return interactionLevel;
    }
    public ItemStack returnedItem() {
        // Anvil wear changes the returned block; cauldrons always return the empty vessel.
        var returned=kind==Kind.ANVIL?new ItemStack(state.getBlock().asItem()):item.copy();
        if(kind==Kind.SHULKER) {
            var contents=new java.util.ArrayList<ItemStack>();for(int i=0;i<inventory.getContainerSize();i++)contents.add(inventory.getItem(i));
            var tag=ItemNbt.blockEntity(returned);ContainerHelper.saveAllItems(tag,NonNullList.of(ItemStack.EMPTY,contents.toArray(ItemStack[]::new)));ItemNbt.blockEntity(returned,tag);
        }
        return returned;
    }
    public void popExperience(Player player) {
        if(!(hold.owner().cargoLevel() instanceof ServerLevel level)||recipesUsed.isEmpty())return;
        var recipes=new java.util.ArrayList<net.minecraft.world.item.crafting.Recipe<?>>();
        recipesUsed.forEach((key,count)->level.getRecipeManager().byKey(key).ifPresent(recipe->{
            if(recipe instanceof AbstractCookingRecipe cooking) {
                float value=count*cooking.getExperience();int amount=(int)value;
                if(level.random.nextFloat()<value-amount)amount++;
                if(amount>0)ExperienceOrb.award(level,hold.position(this),amount);recipes.add(recipe);
            }
        }));
        recipesUsed.clear();if(player instanceof ServerPlayer p)p.awardRecipes(recipes);hold.changed(false);
    }
    public void tick() {
        if(hold.owner().cargoBusy())return;
        CargoWorkBlocks.tick(this);
        if(!cooking()||hold.owner().cargoBusy())return;
        if(burn==0&&(inventory.getItem(0).isEmpty()||inventory.getItem(1).isEmpty())) {
            if(cook>0) { cook=Math.max(0,cook-2);hold.changed(false); }return;
        }
        boolean lit=burn>0;if(burn>0)burn--;
        var recipe=recipeCheck.getRecipeFor(new SimpleContainer(inventory.getItem(0)),hold.owner().cargoLevel()).orElse(null);
        var result=recipe==null?ItemStack.EMPTY:recipe.assemble(new SimpleContainer(inventory.getItem(0)),hold.owner().cargoLevel().registryAccess());
        var output=inventory.getItem(2);
        boolean canCook=!result.isEmpty()&&(output.isEmpty()||ItemStack.isSameItemSameTags(output,result))
            &&output.getCount()+result.getCount()<=Math.min(inventory.getMaxStackSize(),result.getMaxStackSize());
        if(burn==0&&canCook) {
            var fuel=inventory.getItem(1);int duration=fuelTime(fuel);
            if(duration>0) { burn=fuelDuration=duration;var remainder=fuel.getCraftingRemainingItem();fuel.shrink(1);if(fuel.isEmpty())inventory.setItem(1,remainder); }
        }
        if(burn>0&&canCook) {
            int required=Math.max(1,recipe.getCookingTime());if(totalCook!=required) { totalCook=required;cook=0; }
            if(++cook>=totalCook) {
                cook=0;if(output.isEmpty())inventory.setItem(2,result.copy());else output.grow(result.getCount());
                if(inventory.getItem(0).is(Items.WET_SPONGE)&&inventory.getItem(1).is(Items.BUCKET))inventory.setItem(1,new ItemStack(Items.WATER_BUCKET));
                inventory.getItem(0).shrink(1);recipesUsed.merge(recipe.getId(),1,Integer::sum);
            }
        } else cook=burn>0?0:Math.max(0,cook-2);
        if(lit!=(burn>0)&&state.hasProperty(AbstractFurnaceBlock.LIT)) { state=state.setValue(AbstractFurnaceBlock.LIT,burn>0);hold.changed(true); }
        hold.changed(false);
    }
    public CompoundTag save(HolderLookup.Provider lookup,boolean visual) {
        var tag=new CompoundTag();tag.putUUID("Id",id);var saved=item.copy();
        if(visual) { saved.removeTagKey("BlockEntityTag");com.sange.tm_wagon.compat.BackpackCompat.visual(saved); }
        tag.put("Item",saved.save(new net.minecraft.nbt.CompoundTag()));tag.put("State",NbtUtils.writeBlockState(state));
        tag.putBoolean("Opened",opened);tag.putLong("LidStart",lidStart);tag.putFloat("LidFrom",lidFrom);
        tag.putBoolean("Visual",visual);tag.putBoolean("Reversed",reversed);
        if(visual&&sleeper!=null)tag.putUUID("Sleeper",sleeper);
        if(!visual) {
            var contents=NonNullList.withSize(inventory.getContainerSize(),ItemStack.EMPTY);
            for(int i=0;i<contents.size();i++)contents.set(i,inventory.getItem(i));ContainerHelper.saveAllItems(tag,contents);
            tag.putInt("BrewTime",brewTime);tag.putInt("BrewFuel",brewFuel);tag.putInt("Page",page);tag.putLong("CompostReady",compostReady);
            tag.putString("BrewingIngredient",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(brewingIngredient).toString());
            tag.putInt("Burn",burn);tag.putInt("FuelDuration",fuelDuration);tag.putInt("Cook",cook);tag.putInt("TotalCook",totalCook);
            var used=new CompoundTag();recipesUsed.forEach((key,count)->used.putInt(key.toString(),count));tag.put("Recipes",used);
        }return tag;
    }
    public static CargoEntry load(CargoHold hold,CompoundTag tag,HolderLookup.Provider lookup) {
        var item=ItemStack.of(tag.getCompound("Item"));
        if(item.isEmpty()||!(item.getItem() instanceof net.minecraft.world.item.BlockItem||item.getItem() instanceof StrawMatItem||item.getItem() instanceof WagonStoolItem||com.sange.tm_wagon.compat.BackpackCompat.matches(item)))return null;
        var blocks=lookup==null?net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup():lookup.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
        var state=NbtUtils.readBlockState(blocks,tag.getCompound("State"));
        if(!tag.hasUUID("Id"))return null;
        var entry=new CargoEntry(hold,tag.getUUID("Id"),item,state);
        entry.loading=true;entry.reversed=tag.getBoolean("Reversed");
        if(tag.getBoolean("Visual")&&tag.hasUUID("Sleeper"))entry.sleeper=tag.getUUID("Sleeper");
        var contents=NonNullList.withSize(entry.inventory.getContainerSize(),ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag,contents);
        for(int i=0;i<contents.size();i++)entry.inventory.setItem(i,contents.get(i));
        entry.brewTime=Math.max(0,tag.getInt("BrewTime"));entry.brewFuel=Math.max(0,tag.getInt("BrewFuel"));entry.page=Math.max(0,tag.getInt("Page"));
        entry.compostReady=tag.contains("CompostReady")?tag.getLong("CompostReady"):Long.MIN_VALUE;
        var ingredient=ResourceLocation.tryParse(tag.getString("BrewingIngredient"));entry.brewingIngredient=ingredient==null?Items.AIR:net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ingredient);
        entry.burn=Math.max(0,tag.getInt("Burn"));entry.fuelDuration=Math.max(0,tag.getInt("FuelDuration"));entry.cook=Math.max(0,tag.getInt("Cook"));entry.totalCook=Math.max(1,tag.getInt("TotalCook"));
        entry.opened=tag.getBoolean("Opened");entry.lidStart=tag.getLong("LidStart");entry.lidFrom=tag.getFloat("LidFrom");
        if(!tag.getBoolean("Visual")) {
            entry.opened=false;entry.lidStart=Long.MIN_VALUE;entry.lidFrom=0;
            if(entry.kind==Kind.BARREL)entry.state=entry.state.setValue(BarrelBlock.OPEN,false);
        }
        var used=tag.getCompound("Recipes");for(String key:used.getAllKeys()) { var name=ResourceLocation.tryParse(key);if(name!=null)entry.recipesUsed.put(name,Math.max(0,used.getInt(key))); }
        entry.loading=false;return entry;
    }
    public final class Inventory extends SimpleContainer implements StackedContentsCompatible {
        Inventory(int size) { super(size); }
        @Override public boolean canPlaceItem(int slot,ItemStack stack) { return kind!=Kind.LECTERN; }
        @Override public boolean stillValid(Player player) { return hold.valid(CargoEntry.this,player)&&(kind!=Kind.LECTERN||!getItem(0).isEmpty()); }
        @Override public void setItem(int slot,ItemStack stack) {
            if(!loading&&slot==0&&cooking()&&!ItemStack.isSameItemSameTags(getItem(slot),stack))cook=0;
            super.setItem(slot,stack);
        }
        @Override public void setChanged() {
            super.setChanged();if(!loading) { CargoWorkBlocks.updateVisualState(CargoEntry.this);hold.changed(false); }
        }
        @Override public void fillStackedContents(StackedContents contents) { for(int i=0;i<getContainerSize();i++)contents.accountStack(getItem(i)); }
    }
    public ContainerData data() {
        return new ContainerData() {
            public int get(int i) { return switch(i) { case 0->burn;case 1->fuelDuration;case 2->cook;case 3->totalCook;default->0; }; }
            public void set(int i,int v) { switch(i) { case 0->burn=v;case 1->fuelDuration=v;case 2->cook=v;case 3->totalCook=v; } }
            public int getCount() { return 4; }
        };
    }
}
