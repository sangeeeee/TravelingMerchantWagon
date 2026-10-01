package com.sange.tm_wagon.cargo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

public final class CargoEntry {
    public enum Kind { ORDINARY, CHEST, BARREL, SHULKER, FURNACE, SMOKER, CRAFTING }
    public final UUID id;
    public final ItemStack item;
    public BlockState state;
    public final Kind kind;
    public final Inventory inventory;
    CargoHold hold;
    public int burn,fuelDuration,cook,totalCook=200;
    public boolean opened;
    public long lidStart=Long.MIN_VALUE;
    public float lidFrom;
    private boolean loading;
    private final Map<ResourceLocation,Integer> recipesUsed=new HashMap<>();
    private final RecipeManager.CachedCheck<SingleRecipeInput,? extends AbstractCookingRecipe> recipeCheck;

    public static Kind kind(BlockState state) {
        var block=state.getBlock();
        if(block==Blocks.CHEST||block==Blocks.TRAPPED_CHEST)return Kind.CHEST;
        if(block==Blocks.BARREL)return Kind.BARREL;
        if(block instanceof ShulkerBoxBlock)return Kind.SHULKER;
        if(block==Blocks.FURNACE)return Kind.FURNACE;
        if(block==Blocks.SMOKER)return Kind.SMOKER;
        if(block==Blocks.CRAFTING_TABLE)return Kind.CRAFTING;
        return Kind.ORDINARY;
    }
    public CargoEntry(CargoHold hold,UUID id,ItemStack stack,BlockState state) {
        this.hold=hold;this.id=id;item=stack.copyWithCount(1);this.state=state;kind=kind(state);
        recipeCheck=kind==Kind.SMOKER?RecipeManager.createCheck(RecipeType.SMOKING):RecipeManager.createCheck(RecipeType.SMELTING);
        inventory=new Inventory(cooking()?3:kind==Kind.CHEST||kind==Kind.BARREL||kind==Kind.SHULKER?27:0);
    }
    public static CargoEntry fromItem(CargoHold hold,ItemStack stack,BlockState state) {
        var entry=new CargoEntry(hold,UUID.randomUUID(),stack,state);entry.loading=true;
        if(entry.inventory.getContainerSize()>0) {
            var contents=NonNullList.withSize(entry.inventory.getContainerSize(),ItemStack.EMPTY);
            var tag=stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA,CustomData.EMPTY).copyTag();
            ContainerHelper.loadAllItems(tag,contents,hold.owner().cargoLevel().registryAccess());
            var component=stack.get(DataComponents.CONTAINER);
            // Vanilla container items have an empty default component. It must
            // not erase inventory imported from legacy BlockEntityTag data.
            if(component!=null&&component.nonEmptyStream().findAny().isPresent())component.copyInto(contents);
            for(int i=0;i<contents.size();i++)entry.inventory.setItem(i,contents.get(i));
            entry.burn=Math.max(0,tag.getInt("BurnTime"));entry.cook=Math.max(0,tag.getInt("CookTime"));
            entry.totalCook=Math.max(1,tag.contains("CookTimeTotal")?tag.getInt("CookTimeTotal"):200);
            entry.fuelDuration=Math.max(entry.burn,entry.fuelTime(contents.get(1)));
            var recipes=tag.getCompound("RecipesUsed");
            for(String key:recipes.getAllKeys()) { var name=ResourceLocation.tryParse(key);if(name!=null)entry.recipesUsed.put(name,Math.max(0,recipes.getInt(key))); }
            entry.item.remove(DataComponents.CONTAINER);
            for(String key:new String[]{"Items","BurnTime","CookTime","CookTimeTotal","RecipesUsed"})tag.remove(key);
            if(tag.isEmpty())entry.item.remove(DataComponents.BLOCK_ENTITY_DATA);else entry.item.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(tag));
        }
        if(entry.cooking())entry.state=entry.state.setValue(AbstractFurnaceBlock.LIT,entry.burn>0);
        entry.loading=false;return entry;
    }
    private RecipeType<? extends AbstractCookingRecipe> recipeType() { return kind==Kind.SMOKER?RecipeType.SMOKING:RecipeType.SMELTING; }
    private int fuelTime(ItemStack fuel) { int duration=fuel.getBurnTime(recipeType());return kind==Kind.SMOKER?duration/2:duration; }
    public boolean cooking() { return kind==Kind.FURNACE||kind==Kind.SMOKER; }
    public net.minecraft.world.level.Level holdOwnerLevel() { return hold.owner().cargoLevel(); }
    public float lid(float tick) {
        if(lidStart==Long.MIN_VALUE||hold.owner().cargoLevel()==null)return opened?1:0;
        float t=(float)Math.clamp((hold.owner().cargoLevel().getGameTime()+tick-lidStart)/8,0,1);
        return lidFrom+((opened?1:0)-lidFrom)*t;
    }
    public void setOpened(boolean open) {
        if(opened==open)return;
        lidFrom=lid(0);opened=open;lidStart=hold.owner().cargoLevel().getGameTime();
        if(state.hasProperty(BarrelBlock.OPEN))state=state.setValue(BarrelBlock.OPEN,open);
        hold.changed(true);
    }
    public ItemStack returnedItem() {
        var returned=item.copy();
        if(kind==Kind.SHULKER) {
            var contents=new java.util.ArrayList<ItemStack>();for(int i=0;i<inventory.getContainerSize();i++)contents.add(inventory.getItem(i));
            returned.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(contents));
        }
        return returned;
    }
    public void popExperience(Player player) {
        if(!(hold.owner().cargoLevel() instanceof ServerLevel level)||recipesUsed.isEmpty())return;
        var recipes=new java.util.ArrayList<net.minecraft.world.item.crafting.RecipeHolder<?>>();
        recipesUsed.forEach((key,count)->level.getRecipeManager().byKey(key).ifPresent(recipe->{
            if(recipe.value() instanceof AbstractCookingRecipe cooking) {
                float value=count*cooking.getExperience();int amount=(int)value;
                if(level.random.nextFloat()<value-amount)amount++;
                if(amount>0)ExperienceOrb.award(level,hold.position(this),amount);recipes.add(recipe);
            }
        }));
        recipesUsed.clear();if(player instanceof ServerPlayer p)p.awardRecipes(recipes);hold.changed(false);
    }
    public void tick() {
        if(!cooking()||hold.owner().cargoBusy())return;
        if(burn==0&&(inventory.getItem(0).isEmpty()||inventory.getItem(1).isEmpty())) {
            if(cook>0) { cook=Math.max(0,cook-2);hold.changed(false); }return;
        }
        boolean lit=burn>0;if(burn>0)burn--;
        var recipe=recipeCheck.getRecipeFor(new SingleRecipeInput(inventory.getItem(0)),hold.owner().cargoLevel()).orElse(null);
        var result=recipe==null?ItemStack.EMPTY:recipe.value().assemble(new SingleRecipeInput(inventory.getItem(0)),hold.owner().cargoLevel().registryAccess());
        var output=inventory.getItem(2);
        boolean canCook=!result.isEmpty()&&(output.isEmpty()||ItemStack.isSameItemSameComponents(output,result))
            &&output.getCount()+result.getCount()<=Math.min(inventory.getMaxStackSize(),result.getMaxStackSize());
        if(burn==0&&canCook) {
            var fuel=inventory.getItem(1);int duration=fuelTime(fuel);
            if(duration>0) { burn=fuelDuration=duration;var remainder=fuel.getCraftingRemainingItem();fuel.shrink(1);if(fuel.isEmpty())inventory.setItem(1,remainder); }
        }
        if(burn>0&&canCook) {
            int required=Math.max(1,recipe.value().getCookingTime());if(totalCook!=required) { totalCook=required;cook=0; }
            if(++cook>=totalCook) {
                cook=0;if(output.isEmpty())inventory.setItem(2,result.copy());else output.grow(result.getCount());
                if(inventory.getItem(0).is(Items.WET_SPONGE)&&inventory.getItem(1).is(Items.BUCKET))inventory.setItem(1,new ItemStack(Items.WATER_BUCKET));
                inventory.getItem(0).shrink(1);recipesUsed.merge(recipe.id(),1,Integer::sum);
            }
        } else cook=burn>0?0:Math.max(0,cook-2);
        if(lit!=(burn>0)&&state.hasProperty(AbstractFurnaceBlock.LIT)) { state=state.setValue(AbstractFurnaceBlock.LIT,burn>0);hold.changed(true); }
        hold.changed(false);
    }
    public CompoundTag save(HolderLookup.Provider lookup,boolean visual) {
        var tag=new CompoundTag();tag.putUUID("Id",id);var saved=item.copy();
        if(visual) { saved.remove(DataComponents.CONTAINER);saved.remove(DataComponents.BLOCK_ENTITY_DATA); }
        tag.put("Item",saved.save(lookup));tag.put("State",NbtUtils.writeBlockState(state));
        tag.putBoolean("Opened",opened);tag.putLong("LidStart",lidStart);tag.putFloat("LidFrom",lidFrom);
        tag.putBoolean("Visual",visual);
        if(!visual) {
            var contents=NonNullList.withSize(inventory.getContainerSize(),ItemStack.EMPTY);
            for(int i=0;i<contents.size();i++)contents.set(i,inventory.getItem(i));ContainerHelper.saveAllItems(tag,contents,lookup);
            tag.putInt("Burn",burn);tag.putInt("FuelDuration",fuelDuration);tag.putInt("Cook",cook);tag.putInt("TotalCook",totalCook);
            var used=new CompoundTag();recipesUsed.forEach((key,count)->used.putInt(key.toString(),count));tag.put("Recipes",used);
        }return tag;
    }
    public static CargoEntry load(CargoHold hold,CompoundTag tag,HolderLookup.Provider lookup) {
        var item=ItemStack.parseOptional(lookup,tag.getCompound("Item"));
        if(item.isEmpty()||!(item.getItem() instanceof net.minecraft.world.item.BlockItem))return null;
        var state=NbtUtils.readBlockState(lookup.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),tag.getCompound("State"));
        var entry=new CargoEntry(hold,tag.hasUUID("Id")?tag.getUUID("Id"):UUID.randomUUID(),item,state);entry.loading=true;
        var contents=NonNullList.withSize(entry.inventory.getContainerSize(),ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,contents,lookup);
        for(int i=0;i<contents.size();i++)entry.inventory.setItem(i,contents.get(i));
        entry.burn=Math.max(0,tag.getInt("Burn"));entry.fuelDuration=Math.max(0,tag.getInt("FuelDuration"));entry.cook=Math.max(0,tag.getInt("Cook"));entry.totalCook=Math.max(1,tag.getInt("TotalCook"));
        entry.opened=tag.getBoolean("Opened");entry.lidStart=tag.contains("LidStart")?tag.getLong("LidStart"):Long.MIN_VALUE;entry.lidFrom=tag.getFloat("LidFrom");
        if(!tag.getBoolean("Visual")) {
            entry.opened=false;entry.lidStart=Long.MIN_VALUE;entry.lidFrom=0;
            if(entry.kind==Kind.BARREL)entry.state=entry.state.setValue(BarrelBlock.OPEN,false);
        }
        var used=tag.getCompound("Recipes");for(String key:used.getAllKeys()) { var name=ResourceLocation.tryParse(key);if(name!=null)entry.recipesUsed.put(name,Math.max(0,used.getInt(key))); }
        entry.loading=false;return entry;
    }
    public final class Inventory extends SimpleContainer implements StackedContentsCompatible {
        Inventory(int size) { super(size); }
        @Override public boolean stillValid(Player player) { return hold.valid(CargoEntry.this,player); }
        @Override public void setItem(int slot,ItemStack stack) {
            if(!loading&&slot==0&&cooking()&&!ItemStack.isSameItemSameComponents(getItem(slot),stack))cook=0;
            super.setItem(slot,stack);
        }
        @Override public void setChanged() { super.setChanged();if(!loading)hold.changed(false); }
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
